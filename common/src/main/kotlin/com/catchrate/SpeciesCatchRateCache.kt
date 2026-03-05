package com.catchrate

import com.catchrate.platform.PlatformHelper
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonParser
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Local-only catch rate cache. Cobblemon doesn't sync catchRate to the client
 * (Species.encode/decode omits it), so the registry always has the constructor
 * default of 45. We resolve every species from local data instead:
 *
 *   1. Local datapacks (game dir /datapacks/) — ZIP and folder packs
 *   2. World save datapacks (saves/<world>/datapacks/) — per-world overrides
 *   3. Mod JARs on classpath — Cobblemon + addon mods
 *   4. Fallback: 45 (Cobblemon's default — most common wild Pokémon)
 */
object SpeciesCatchRateCache {

    private const val DEFAULT_CATCH_RATE = 45
    private val cache = ConcurrentHashMap<String, Int>()
    private var datapacksScanned = false
    private val datapackOverrides = ConcurrentHashMap<String, Int>()

    fun getCatchRate(species: Species): Int {
        val speciesName = species.name.lowercase()
        cache[speciesName]?.let { return it }

        val resolved = resolve(species)
        cache[speciesName] = resolved
        CatchRateMod.debugOnChange(
            "CatchRate", speciesName,
            "${species.name} catchRate resolved to $resolved (local data)"
        )
        return resolved
    }

    /** Clear the cache (e.g., on world change or disconnect). */
    fun invalidate() {
        cache.clear()
        datapackOverrides.clear()
        datapacksScanned = false
        CatchRateMod.debug("Cache", "Catch rate cache invalidated")
    }

    // -- Resolution chain --

    private fun resolve(species: Species): Int {
        ensureDatapacksScanned()
        datapackOverrides[species.name.lowercase()]?.let { return it }
        loadFromClasspath(species)?.let { return it }
        return DEFAULT_CATCH_RATE
    }

    // -- Datapack scanning --

    private fun ensureDatapacksScanned() {
        if (datapacksScanned) return
        datapacksScanned = true
        try {
            val gameDir = PlatformHelper.getGameDir()
            scanDatapackDir(gameDir.resolve("datapacks"))
            // Also scan world-level datapacks in saves/
            val savesDir = gameDir.resolve("saves")
            if (Files.isDirectory(savesDir)) {
                Files.list(savesDir).use { worlds ->
                    worlds.forEach { worldDir ->
                        scanDatapackDir(worldDir.resolve("datapacks"))
                    }
                }
            }
        } catch (e: Throwable) {
            CatchRateMod.debug("Cache", "Datapack scan failed: ${e.message}")
        }
        if (datapackOverrides.isNotEmpty()) {
            CatchRateMod.LOGGER.info("[CatchRate] Loaded ${datapackOverrides.size} catch rate(s) from local datapacks")
        }
    }

    private fun scanDatapackDir(dir: Path) {
        if (!Files.isDirectory(dir)) return
        try {
            Files.list(dir).use { entries ->
                entries.forEach { entry ->
                    try {
                        when {
                            Files.isDirectory(entry) -> scanDatapackFolder(entry)
                            entry.toString().endsWith(".zip", ignoreCase = true) -> scanDatapackZip(entry)
                        }
                    } catch (e: Throwable) {
                        CatchRateMod.debug("Cache", "Failed to scan datapack ${entry.fileName}: ${e.message}")
                    }
                }
            }
        } catch (_: Throwable) { }
    }

    private fun scanDatapackFolder(root: Path) {
        val dataDir = root.resolve("data")
        if (!Files.isDirectory(dataDir)) return
        Files.walk(dataDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                .filter { isSpeciesPath(root.relativize(it).toString().replace('\\', '/')) }
                .forEach { file ->
                    extractCatchRate(Files.newInputStream(file))?.let { cr ->
                        val name = file.fileName.toString().removeSuffix(".json").lowercase()
                        datapackOverrides[name] = cr
                    }
                }
        }
    }

    private fun scanDatapackZip(zipPath: Path) {
        FileSystems.newFileSystem(zipPath, emptyMap<String, Any>()).use { fs ->
            val root = fs.getPath("/")
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                    .filter { isSpeciesPath(it.toString().removePrefix("/").replace('\\', '/')) }
                    .forEach { entry ->
                        extractCatchRate(Files.newInputStream(entry))?.let { cr ->
                            val name = entry.fileName.toString().removeSuffix(".json").lowercase()
                            datapackOverrides[name] = cr
                        }
                    }
            }
        }
    }

    /** Match paths like data/{namespace}/species/generation{N}/{name}.json */
    private fun isSpeciesPath(relativePath: String): Boolean {
        val parts = relativePath.split('/')
        // data/<ns>/species/.../<name>.json — at least 4 segments
        return parts.size >= 4 && parts[0] == "data" && parts[2] == "species"
    }

    // -- Classpath loading --

    // All known Cobblemon generation folders — includes sub-generations like 7b (Meltan/Melmetal)
    // and 8a (Legends: Arceus Pokémon). Checked against Cobblemon 1.7.3 jar.
    private val generationFolders = listOf(
        "generation1", "generation2", "generation3", "generation4", "generation5",
        "generation6", "generation7", "generation7b", "generation8", "generation8a", "generation9"
    )

    private fun loadFromClasspath(species: Species): Int? {
        val namespace = species.resourceIdentifier?.namespace ?: "cobblemon"
        val name = species.name.lowercase()

        for (gen in generationFolders) {
            val path = "data/$namespace/species/$gen/$name.json"
            classpathStream(path)?.let { stream ->
                extractCatchRate(stream)?.let { return it }
            }
        }

        // Addon species without generation prefix
        for (prefix in listOf("", "custom/", "addon/")) {
            val path = "data/$namespace/species/$prefix$name.json"
            classpathStream(path)?.let { stream ->
                extractCatchRate(stream)?.let { return it }
            }
        }

        return null
    }

    private fun classpathStream(path: String): InputStream? =
        Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: SpeciesCatchRateCache::class.java.classLoader.getResourceAsStream(path)

    // -- JSON parsing --

    private fun extractCatchRate(stream: InputStream): Int? {
        return try {
            stream.use { s ->
                val obj = JsonParser.parseString(s.bufferedReader().readText()).asJsonObject
                if (obj.has("catchRate")) obj.get("catchRate").asInt else null
            }
        } catch (_: Throwable) { null }
    }

}
