package com.catchrate

import com.catchrate.platform.PlatformHelper
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.world.level.storage.LevelResource
import java.io.InputStream
import java.net.URI
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves the base catch rate for a species, form-aware, from the most authoritative
 * source available in the current environment.
 *
 * Resolution order:
 *
 *   1. Cobblemon's live species registry — but ONLY when it is provably trustworthy.
 *      Cobblemon does not encode catchRate in Species.encode/decode, so a client
 *      connected to a dedicated server sees the constructor default (45) for every
 *      species. It does, however, skip the whole data sync for memory connections
 *      (CobblemonDataProvider.sync bails on Connection.isMemoryConnection), which
 *      means singleplayer and LAN hosts keep the real datapack-loaded values.
 *      When trusted this tier is exact for anything Cobblemon loaded: custom species
 *      from datapacks or mods, form overrides, and species_additions alike.
 *
 *   2. Local files — world datapacks, game datapacks, and mod JARs, scanned for
 *      the data/<ns>/species/ and data/<ns>/species_additions/ subtrees. This
 *      carries remote-server play, where the client normally has the same packs
 *      installed locally even though the registry is defaulted.
 *
 *   3. Unknown. Deliberately NOT a made-up number: callers must render this as
 *      "unknown" rather than show a fabricated percentage.
 *
 * The only case that cannot be resolved is a datapack that exists solely on a remote
 * server. That needs a server-side component to fix and reports as unknown instead.
 */
object SpeciesCatchRateCache {

    enum class Source(val rank: Int, val label: String) {
        REGISTRY(4, "registry"),
        DATAPACK(3, "datapack"),
        MOD_JAR(2, "mod_jar"),
        CLASSPATH(1, "classpath"),
        UNRESOLVED(0, "unresolved")
    }

    data class CatchRateResolution(
        val catchRate: Int,
        /** False when no source could supply a real value. Callers must not display a number. */
        val isKnown: Boolean,
        val source: String,
        val sourcePath: String? = null
    ) {
        /** Retained name for existing call sites: an unresolved rate is not a real rate. */
        val isEstimate: Boolean get() = !isKnown
    }

    private data class FormCatchRateOverride(
        val formName: String,
        val formShowdownId: String,
        val aspects: Set<String>,
        val catchRate: Int
    )

    private data class SpeciesFileData(
        val catchRate: Int?,
        val formOverrides: List<FormCatchRateOverride>,
        val sourcePath: String,
        val source: Source
    )

    /**
     * Nominal value used when nothing could be resolved. It exists only so downstream
     * math does not divide by zero — every display path must check isKnown and show
     * "unknown" instead of rendering a percentage derived from this.
     */
    private const val UNRESOLVED_PLACEHOLDER = 3

    /** Species.catchRate's constructor default, and therefore the value an unsynced client sees. */
    private const val COBBLEMON_DEFAULT_CATCH_RATE = 45

    /** Below this many registered species the registry is too small to judge as trustworthy. */
    private const val TRUST_SAMPLE_MIN = 20

    private val cache = ConcurrentHashMap<String, CatchRateResolution>()
    private val speciesIndex = ConcurrentHashMap<String, SpeciesFileData>()
    private val additionIndex = ConcurrentHashMap<String, SpeciesFileData>()

    @Volatile private var localFilesScanned = false
    @Volatile private var scanning = false
    @Volatile private var preloading = false
    @Volatile private var preloaded = false

    @Volatile private var registryTrustResolved = false
    @Volatile private var registryTrusted = false
    @Volatile private var lastSession: Any? = null

    /**
     * Bumped on every invalidation. A scan that was already running when the player
     * changed world publishes nothing, so the index can never be a mix of two sessions'
     * datapacks or be marked complete when it is only partly filled.
     */
    @Volatile private var generation = 0

    // ==================== PUBLIC API ====================

    fun getCatchRate(species: Species, aspects: Set<String> = emptySet()): Int =
        getResolution(species, aspects).catchRate

    fun isEstimate(species: Species, aspects: Set<String> = emptySet()): Boolean =
        !getResolution(species, aspects).isKnown

    fun isKnown(species: Species, aspects: Set<String> = emptySet()): Boolean =
        getResolution(species, aspects).isKnown

    fun fallbackCatchRate(): Int = UNRESOLVED_PLACEHOLDER

    fun cacheSize(): Int = cache.size

    fun indexedSpeciesCount(): Int = speciesIndex.size

    fun indexedAdditionCount(): Int = additionIndex.size

    /** Human-readable name of the tier currently answering lookups, for diagnostics. */
    fun resolutionTierName(): String =
        if (isRegistryTrusted()) "registry (integrated server)" else "local files (remote server)"

    fun getResolution(species: Species, aspects: Set<String> = emptySet()): CatchRateResolution {
        val key = resolutionKey(species, aspects)
        cache[key]?.let { return it }

        val resolved = resolve(species, aspects)

        // Unknowns are never cached: a background scan or a world load can still supply
        // the answer, and a cached unknown would freeze the species as unknown forever.
        if (resolved.isKnown) {
            cache[key] = resolved
            CatchRateMod.debugOnChange(
                "CatchRate", key,
                "${species.name} catchRate=${resolved.catchRate} from ${resolved.source} (key=$key)"
            )
        }
        return resolved
    }

    /**
     * True when Cobblemon's species registry holds real catch rates rather than the
     * unsynced default. See the class docs for why this is knowable rather than guessed.
     */
    fun isRegistryTrusted(): Boolean {
        if (registryTrustResolved) return registryTrusted
        val decided = computeRegistryTrust() ?: return false // undecided — re-check later
        registryTrusted = decided
        registryTrustResolved = true
        CatchRateMod.LOGGER.info(
            "[CatchRate] Species registry ${if (decided) "TRUSTED (integrated server)" else "NOT trusted (remote server) — using local files"}"
        )
        return decided
    }

    /**
     * Returns null while the answer is not yet decidable (no world loaded, registry empty),
     * so the negative result is not memoized before the game has finished loading.
     */
    private fun computeRegistryTrust(): Boolean? {
        val minecraft = try { Minecraft.getInstance() } catch (_: Throwable) { return null }
        if (minecraft.level == null) return null

        val integratedServer = try { minecraft.hasSingleplayerServer() } catch (_: Throwable) { false }
        if (!integratedServer) return false

        // Second, independent gate: a defaulted registry reports 45 for literally every
        // species. Real data always has variety. This catches the case even if the
        // integrated-server check above were ever wrong.
        val all = try { PokemonSpecies.species } catch (_: Throwable) { return null }
        if (all.size < TRUST_SAMPLE_MIN) return null

        val distinct = HashSet<Int>()
        var sawNonDefault = false
        for (species in all) {
            val rate = try { species.catchRate } catch (_: Throwable) { continue }
            distinct.add(rate)
            if (rate != COBBLEMON_DEFAULT_CATCH_RATE) sawNonDefault = true
            if (distinct.size > 1 && sawNonDefault) return true
        }
        return false
    }

    /** Preload on a background thread so the first render-thread lookup is instant. */
    fun preloadAsync() {
        if (preloaded || preloading) return
        preloading = true
        Thread {
            try {
                val start = System.nanoTime()
                val startGeneration = generation
                // When the registry is trusted it answers every species exactly, so the
                // file index is dead weight. Anything the registry somehow misses still
                // triggers a lazy scan further down, on this same background thread.
                if (!isRegistryTrusted()) ensureLocalFilesScanned()
                if (generation != startGeneration) return@Thread
                val allSpecies = try { PokemonSpecies.species.toList() } catch (_: Throwable) { emptyList() }
                var resolved = 0
                for (species in allSpecies) {
                    if (getResolution(species).isKnown) resolved++
                }
                val elapsed = (System.nanoTime() - start) / 1_000_000
                CatchRateMod.LOGGER.info(
                    "[CatchRate] Preload complete: $resolved/${allSpecies.size} species resolved in ${elapsed}ms " +
                        "(source=${resolutionTierName()}, ${speciesIndex.size} indexed from files, " +
                        "${additionIndex.size} species_additions)"
                )
                val unresolved = allSpecies.size - resolved
                if (unresolved > 0) {
                    CatchRateMod.LOGGER.warn(
                        "[CatchRate] $unresolved species have no resolvable catch rate and will display as unknown"
                    )
                }
                preloaded = true
            } catch (e: Throwable) {
                CatchRateMod.LOGGER.warn("[CatchRate] Preload failed: ${e.message}")
            } finally {
                preloading = false
            }
        }.apply {
            isDaemon = true
            name = "CatchRate-Preload"
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    /**
     * Watches for session changes. Datapacks, and whether the registry can be trusted at
     * all, are both per-session, so everything is rebuilt when the player joins a
     * different world or server.
     *
     * Keyed on the network connection rather than the level: the level object is replaced
     * on every dimension change, which would otherwise throw away a good index and
     * re-scan every mod JAR each time somebody stepped through a nether portal.
     */
    fun onClientTick() {
        val connection = try { Minecraft.getInstance().connection } catch (_: Throwable) { null }
        if (connection === lastSession) return
        lastSession = connection
        invalidate()
        if (connection != null) preloadAsync()
    }

    fun invalidate() {
        generation++
        cache.clear()
        speciesIndex.clear()
        additionIndex.clear()
        localFilesScanned = false
        preloaded = false
        registryTrustResolved = false
        registryTrusted = false
        CatchRateMod.debug("Cache", "Catch rate cache invalidated")
    }

    // ==================== RESOLUTION ====================

    private fun resolve(species: Species, aspects: Set<String>): CatchRateResolution {
        // 1. Live registry, when it is provably real.
        if (isRegistryTrusted()) {
            registryCatchRate(species, aspects)?.let { rate ->
                return CatchRateResolution(rate, isKnown = true, source = Source.REGISTRY.label)
            }
        }

        // 2. Local files. Never scans inline: resolve() runs on the render thread, and
        // walking every mod JAR there would freeze the frame. The scan is kicked off in
        // the background and this lookup reports unknown until it lands — unknowns are
        // not cached, so the real value appears as soon as the index is published.
        requestLocalFileScan()
        resolveFromFiles(species, aspects)?.let { return it }

        // 3. Honest unknown.
        return CatchRateResolution(UNRESOLVED_PLACEHOLDER, isKnown = false, source = Source.UNRESOLVED.label)
    }

    /**
     * Reads the registry form-aware. FormData.getCatchRate() already falls back to the
     * species value when a form does not override it, so this handles regional forms,
     * megas and Minior-style form rates without any of our own aspect matching.
     */
    private fun registryCatchRate(species: Species, aspects: Set<String>): Int? {
        return try {
            val form = if (aspects.isEmpty()) species.standardForm else species.getForm(normalizeAspects(aspects))
            form.catchRate.takeIf { it > 0 }
        } catch (_: Throwable) {
            try { species.catchRate.takeIf { it > 0 } } catch (_: Throwable) { null }
        }
    }

    private fun resolveFromFiles(species: Species, aspects: Set<String>): CatchRateResolution? {
        val id = speciesId(species)
        // The classpath probe does its own file I/O (up to ~14 stream opens), so it must
        // not run until the background scan has actually finished — resolveFromFiles is
        // reached from resolve(), which runs on the render thread. Trying it while a scan
        // is still in flight would reintroduce the per-frame stall requestLocalFileScan()
        // exists to avoid. Once localFilesScanned is true this is just a rare-case safety
        // net (dev workspaces, non-standard launchers), so paying for it inline is fine.
        val base = speciesIndex[id] ?: (if (localFilesScanned) loadFromClasspath(species) else null)?.also { speciesIndex[id] = it }
        val addition = additionIndex[id]

        // species_additions are applied on top of the base file by Cobblemon, so they win here too.
        val effective = when {
            addition == null -> base
            base == null -> addition
            else -> SpeciesFileData(
                catchRate = addition.catchRate ?: base.catchRate,
                formOverrides = addition.formOverrides.ifEmpty { base.formOverrides },
                sourcePath = addition.sourcePath,
                source = addition.source
            )
        } ?: return null

        val formOverride = selectFormOverride(species, aspects, effective.formOverrides)
        val rate = formOverride?.catchRate ?: effective.catchRate ?: return null

        val sourceLabel = buildString {
            append(effective.source.label)
            if (addition != null) append("+addition")
            formOverride?.let { append(':').append(it.formShowdownId) }
        }
        return CatchRateResolution(rate, isKnown = true, source = sourceLabel, sourcePath = effective.sourcePath)
    }

    private fun selectFormOverride(
        species: Species,
        aspects: Set<String>,
        overrides: List<FormCatchRateOverride>
    ): FormCatchRateOverride? {
        if (aspects.isEmpty() || overrides.isEmpty()) return null

        val normalizedAspects = normalizeAspects(aspects)
        val resolvedFormId = try {
            species.getForm(normalizedAspects).formOnlyShowdownId()
        } catch (_: Throwable) {
            null
        }

        if (resolvedFormId != null) {
            overrides.firstOrNull { it.formShowdownId == resolvedFormId }?.let { return it }
        }

        return overrides
            .filter { it.aspects.isNotEmpty() && it.aspects.all(normalizedAspects::contains) }
            .maxByOrNull { it.aspects.size }
    }

    // ==================== KEYS ====================

    /** Fully qualified "namespace:path" so two packs cannot collide on a shared file name. */
    private fun speciesId(species: Species): String {
        val identifier = species.resourceIdentifier
        if (identifier != null) return "${identifier.namespace}:${identifier.path}".lowercase()
        return "cobblemon:" + species.name.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun resolutionKey(species: Species, aspects: Set<String>): String {
        val normalizedAspects = normalizeAspects(aspects)
        if (normalizedAspects.isEmpty()) return speciesId(species)
        return speciesId(species) + "::" + normalizedAspects.sorted().joinToString("&")
    }

    private fun normalizeAspects(aspects: Set<String>): Set<String> = aspects.map { it.lowercase() }.toSet()

    private fun showdownId(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]"), "")

    // ==================== FILE SCANNING ====================

    /**
     * Starts the local file scan on a background thread if it is neither done nor running.
     * Safe to call from the render thread; returns immediately either way.
     */
    private fun requestLocalFileScan() {
        if (localFilesScanned || scanning) return
        Thread {
            try { ensureLocalFilesScanned() } catch (_: Throwable) { }
        }.apply {
            isDaemon = true
            name = "CatchRate-FileScan"
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private fun ensureLocalFilesScanned() {
        if (localFilesScanned || scanning) return
        synchronized(this) {
            if (localFilesScanned || scanning) return
            scanning = true
        }
        try {
            val startGeneration = generation
            val gameDir = PlatformHelper.getGameDir()
            val target = ScanTarget()

            // Mod JARs first, then datapacks: rank ordering means a later scan cannot
            // demote a higher-priority source, so this order is only an optimisation.
            scanModJars(gameDir.resolve("mods"), target)
            scanDatapackDir(gameDir.resolve("datapacks"), target)

            // Only the world actually being played, never every folder under saves/.
            // This tier exists for remote-server play, where the local saves directory
            // can easily hold several unrelated worlds (old tests, other packs) — scanning
            // all of them let an unrelated world's datapack silently outrank the correct
            // one at the same DATAPACK rank, purely because of listing order. On genuine
            // remote play there is no local world at all, so this correctly scans nothing.
            currentWorldDatapacksDir()?.let { worldDatapacks -> scanDatapackDir(worldDatapacks, target) }

            // Publish only if this scan still describes the current session.
            if (generation != startGeneration) {
                CatchRateMod.debug("Cache", "Discarding superseded catch rate scan")
                return
            }
            speciesIndex.putAll(target.species)
            additionIndex.putAll(target.additions)
            localFilesScanned = true
            CatchRateMod.LOGGER.info(
                "[CatchRate] Indexed ${target.species.size} species and ${target.additions.size} species_additions from local files"
            )
        } catch (e: Throwable) {
            CatchRateMod.debug("Cache", "Local file scan failed: ${e.message}")
        } finally {
            scanning = false
        }
    }

    /**
     * The datapacks/ folder of the world actually being played, or null when there is
     * none — i.e. on genuine remote-server play, where an integrated server does not exist.
     */
    private fun currentWorldDatapacksDir(): Path? {
        val server = try { Minecraft.getInstance().singleplayerServer } catch (_: Throwable) { null } ?: return null
        return try {
            server.getWorldPath(LevelResource.ROOT).resolve("datapacks").takeIf { Files.isDirectory(it) }
        } catch (_: Throwable) { null }
    }

    /** Scratch index for one scan pass, published only once the pass completes. */
    private class ScanTarget {
        val species = HashMap<String, SpeciesFileData>()
        val additions = HashMap<String, SpeciesFileData>()
    }

    private fun scanModJars(modsDir: Path, target: ScanTarget) {
        if (!Files.isDirectory(modsDir)) return
        try {
            // Sorted so that when two mod JARs define the same species (same MOD_JAR rank),
            // which one wins is reproducible across launches instead of depending on
            // whatever order the filesystem happens to hand back.
            Files.list(modsDir).use { entries ->
                entries.filter { it.toString().endsWith(".jar", ignoreCase = true) }
                    .sorted()
                    .forEach { jar -> scanArchive(jar, Source.MOD_JAR, target) }
            }
        } catch (e: Throwable) {
            CatchRateMod.debug("Cache", "Mod JAR scan failed: ${e.message}")
        }
    }

    private fun scanDatapackDir(dir: Path, target: ScanTarget) {
        if (!Files.isDirectory(dir)) return
        try {
            // Sorted for the same reason as scanModJars: deterministic tie-breaking
            // between two datapacks that both touch the same species.
            Files.list(dir).use { entries ->
                entries.sorted().forEach { entry ->
                    try {
                        when {
                            Files.isDirectory(entry) -> scanDataRoot(entry.resolve("data"), Source.DATAPACK, entry.toString(), target)
                            entry.toString().endsWith(".zip", ignoreCase = true) -> scanArchive(entry, Source.DATAPACK, target)
                        }
                    } catch (e: Throwable) {
                        CatchRateMod.debug("Cache", "Failed to scan ${entry.fileName}: ${e.message}")
                    }
                }
            }
        } catch (_: Throwable) { }
    }

    private fun scanArchive(archive: Path, source: Source, target: ScanTarget) {
        withArchiveRoot(archive) { root ->
            scanDataRoot(root.resolve("data"), source, archive.toString(), target)
        }
    }

    /**
     * Indexes the species and species_additions subtrees under one data root, recursively.
     *
     * Only those two subtrees are walked rather than the whole data directory, which keeps
     * the cost of scanning several hundred mod JARs down to a directory probe for the
     * mods that carry no species data at all.
     */
    private fun scanDataRoot(dataDir: Path, source: Source, displayRoot: String, target: ScanTarget) {
        if (!Files.isDirectory(dataDir)) return
        Files.list(dataDir).use { namespaces ->
            namespaces.filter { Files.isDirectory(it) }.forEach { namespaceDir ->
                val namespace = namespaceDir.fileName.toString().trim('/').lowercase()
                indexTree(namespaceDir.resolve("species"), namespace, source, displayRoot, false, target)
                indexTree(namespaceDir.resolve("species_additions"), namespace, source, displayRoot, true, target)
            }
        }
    }

    private fun indexTree(
        root: Path,
        namespace: String,
        source: Source,
        displayRoot: String,
        isAddition: Boolean,
        target: ScanTarget
    ) {
        if (!Files.isDirectory(root)) return
        try {
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".json", ignoreCase = true) }
                    .forEach { file ->
                        try {
                            indexFile(file, namespace, source, displayRoot, isAddition, target)
                        } catch (_: Throwable) { }
                    }
            }
        } catch (e: Throwable) {
            CatchRateMod.debug("Cache", "Failed to walk $root: ${e.message}")
        }
    }

    private fun indexFile(
        file: Path,
        namespace: String,
        source: Source,
        displayRoot: String,
        isAddition: Boolean,
        target: ScanTarget
    ) {
        val json = Files.newInputStream(file).use { parseJson(it) } ?: return
        val data = extractCatchRateData(json, "$displayRoot!${file.toString().replace('\\', '/')}", source) ?: return

        val id = if (isAddition) {
            // species_additions carry an explicit "target" such as "cobblemon:aerodactyl".
            val target = json.takeIf { it.has("target") }?.get("target")?.asString?.lowercase() ?: return
            if (target.contains(':')) target else "cobblemon:$target"
        } else {
            val fileName = file.fileName.toString().removeSuffix(".json").removeSuffix(".JSON").lowercase()
            "$namespace:$fileName"
        }

        val index = if (isAddition) target.additions else target.species
        val existing = index[id]
        if (existing == null || source.rank >= existing.source.rank) {
            index[id] = data
        }
    }

    private fun <T> withArchiveRoot(archive: Path, block: (Path) -> T): T? {
        return try {
            FileSystems.newFileSystem(archive, emptyMap<String, Any>()).use { fs -> block(fs.getPath("/")) }
        } catch (_: FileSystemAlreadyExistsException) {
            // Already mounted by the loader (common for mod JARs) — reuse it and do not close it.
            try {
                val fs = FileSystems.getFileSystem(URI.create("jar:" + archive.toUri()))
                block(fs.getPath("/"))
            } catch (_: Throwable) { null }
        } catch (_: Throwable) {
            null
        }
    }

    // ==================== CLASSPATH FAST PATH ====================

    private val generationFolders = listOf(
        "generation1", "generation2", "generation3", "generation4", "generation5",
        "generation6", "generation7", "generation7b", "generation8", "generation8a", "generation9"
    )

    /**
     * Direct classpath lookup for the standard Cobblemon layout. The JAR walk above already
     * covers everything this does, but this stays as a cheap safety net for environments
     * where mods are not loaded from the mods directory (dev workspaces, custom launchers).
     */
    private fun loadFromClasspath(species: Species): SpeciesFileData? {
        val identifier = species.resourceIdentifier
        val namespace = identifier?.namespace ?: "cobblemon"
        val name = identifier?.path?.lowercase()
            ?: species.name.lowercase().replace(Regex("[^a-z0-9]"), "")

        val candidates = generationFolders.map { "data/$namespace/species/$it/$name.json" } +
            listOf("", "custom/", "addon/").map { "data/$namespace/species/$it$name.json" }

        for (path in candidates) {
            val json = classpathStream(path)?.use { parseJson(it) } ?: continue
            extractCatchRateData(json, path, Source.CLASSPATH)?.let { return it }
        }
        return null
    }

    private fun classpathStream(path: String): InputStream? =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: SpeciesCatchRateCache::class.java.classLoader?.getResourceAsStream(path)

    // ==================== JSON ====================

    private fun parseJson(stream: InputStream): JsonObject? = try {
        JsonParser.parseString(stream.bufferedReader().readText()).asJsonObject
    } catch (_: Throwable) {
        null
    }

    private fun extractCatchRateData(obj: JsonObject, sourcePath: String, source: Source): SpeciesFileData? {
        return try {
            val baseCatchRate = if (obj.has("catchRate")) obj.get("catchRate").asInt else null
            val formOverrides = mutableListOf<FormCatchRateOverride>()

            if (obj.has("forms") && obj.get("forms").isJsonArray) {
                obj.getAsJsonArray("forms").forEach { formElement ->
                    val formObject = formElement.asJsonObject
                    if (!formObject.has("catchRate")) return@forEach
                    val formName = if (formObject.has("name")) formObject.get("name").asString else return@forEach
                    val aspects = if (formObject.has("aspects") && formObject.get("aspects").isJsonArray) {
                        formObject.getAsJsonArray("aspects").map { it.asString.lowercase() }.toSet()
                    } else {
                        emptySet()
                    }
                    formOverrides += FormCatchRateOverride(
                        formName = formName,
                        formShowdownId = showdownId(formName),
                        aspects = aspects,
                        catchRate = formObject.get("catchRate").asInt
                    )
                }
            }

            if (baseCatchRate == null && formOverrides.isEmpty()) {
                null
            } else {
                SpeciesFileData(baseCatchRate, formOverrides, sourcePath, source)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
