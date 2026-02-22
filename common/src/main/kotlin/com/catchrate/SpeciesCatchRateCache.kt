package com.catchrate

import com.cobblemon.mod.common.pokemon.Species
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap

/**
 * Workaround for Cobblemon's species sync not including catchRate on the client.
 *
 * When a client joins a server, the SpeciesRegistrySyncPacket creates new Species objects
 * with the default catchRate (45). The encode/decode cycle may not preserve catchRate,
 * so the client ends up with wrong values for ALL species.
 *
 * This cache loads catch rates directly from Cobblemon's species JSON files on the classpath,
 * which are always correct regardless of sync issues.
 */
object SpeciesCatchRateCache {

    private const val SPECIES_DEFAULT_CATCH_RATE = 45
    private val cache = ConcurrentHashMap<String, Int>()
    private var loggedWarning = false

    /**
     * Get the correct catch rate for a species, using the classpath cache as override
     * when the species object has the default (likely unsynced) value.
     */
    fun getCatchRate(species: Species): Int {
        val registryValue = species.catchRate
        // If the registry already has a non-default value, trust it
        if (registryValue != SPECIES_DEFAULT_CATCH_RATE) return registryValue

        val speciesName = species.name.lowercase()
        val cached = cache[speciesName]
        if (cached != null) {
            if (cached != SPECIES_DEFAULT_CATCH_RATE) {
                CatchRateMod.debugOnChange(
                    "CatchRateOverride", speciesName,
                    "${species.name} catchRate override: registry=$registryValue, jar=$cached"
                )
            }
            return cached
        }

        // Try loading from classpath
        val loaded = loadFromClasspath(species)
        cache[speciesName] = loaded
        if (loaded != registryValue) {
            CatchRateMod.debugOnChange(
                "CatchRateOverride", speciesName,
                "${species.name} catchRate override: registry=$registryValue, jar=$loaded"
            )
            if (!loggedWarning) {
                CatchRateMod.LOGGER.warn("[CatchRate] Species catchRate mismatch detected — using JAR values. " +
                    "This is a known issue with Cobblemon's client sync.")
                loggedWarning = true
            }
        }
        return loaded
    }

    private fun loadFromClasspath(species: Species): Int {
        val namespace = species.resourceIdentifier?.namespace ?: "cobblemon"
        val name = species.name.lowercase()

        // Cobblemon stores species at data/{namespace}/species/generation{N}/{name}.json
        for (gen in 1..9) {
            val path = "data/$namespace/species/generation$gen/$name.json"
            try {
                val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
                    ?: SpeciesCatchRateCache::class.java.classLoader.getResourceAsStream(path)
                    ?: continue
                val catchRate = stream.use { s ->
                    val json = s.bufferedReader().readText()
                    val obj = JsonParser.parseString(json).asJsonObject
                    if (obj.has("catchRate")) obj.get("catchRate").asInt else null
                }
                if (catchRate != null) return catchRate
            } catch (_: Throwable) {
                // Corrupted file or parse error — skip
            }
        }

        // Also try without generation prefix (addon species)
        for (prefix in listOf("", "custom/", "addon/")) {
            val path = "data/$namespace/species/$prefix$name.json"
            try {
                val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
                    ?: continue
                val catchRate = stream.use { s ->
                    val json = s.bufferedReader().readText()
                    val obj = JsonParser.parseString(json).asJsonObject
                    if (obj.has("catchRate")) obj.get("catchRate").asInt else null
                }
                if (catchRate != null) return catchRate
            } catch (_: Throwable) { }
        }

        // Couldn't find a JSON file — fall back to the registry value
        return species.catchRate
    }
}
