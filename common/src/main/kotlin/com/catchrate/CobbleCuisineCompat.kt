package com.catchrate

import com.catchrate.platform.PlatformHelper
import com.google.gson.JsonParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import java.nio.file.Files

object CobbleCuisineCompat {

    private const val MOD_ID = "cobblecuisine"
    private val catchBoostEffectId = ResourceLocation.fromNamespaceAndPath("cobblecuisine", "catch_boost")
    private val configPath by lazy { PlatformHelper.getConfigDir().resolve("cobblecuisine.json") }

    @Volatile private var cachedConfigTimestamp = Long.MIN_VALUE
    @Volatile private var cachedCatchRateMultiplier = 2F

    fun getCatchRateMultiplier(player: Player?): Float {
        if (player == null) return 1F
        if (!PlatformHelper.isModLoaded(MOD_ID)) return 1F

        val effectHolder = try {
            BuiltInRegistries.MOB_EFFECT.getHolder(catchBoostEffectId).orElse(null)
        } catch (_: Throwable) {
            null
        } ?: return 1F

        if (!player.hasEffect(effectHolder)) return 1F

        val multiplier = loadConfiguredCatchRateMultiplier()
        CatchRateMod.debugOnChange(
            "CobbleCuisine",
            "${player.uuid}_$multiplier",
            "CobbleCuisine catch boost active -> ${String.format("%.2f", multiplier)}x"
        )
        return multiplier
    }

    private fun loadConfiguredCatchRateMultiplier(): Float {
        return try {
            val lastModified = if (Files.exists(configPath)) Files.getLastModifiedTime(configPath).toMillis() else Long.MIN_VALUE
            if (lastModified == cachedConfigTimestamp) return cachedCatchRateMultiplier

            val multiplier = if (Files.exists(configPath)) {
                Files.newBufferedReader(configPath).use { reader ->
                    val root = JsonParser.parseReader(reader).asJsonObject
                    val boostSettings = root.getAsJsonObject("boostSettings")
                    if (boostSettings != null && boostSettings.has("catchRateMultiplier")) {
                        boostSettings.get("catchRateMultiplier").asFloat
                    } else {
                        2F
                    }
                }
            } else {
                2F
            }

            cachedConfigTimestamp = lastModified
            cachedCatchRateMultiplier = if (multiplier > 0F) multiplier else 1F
            cachedCatchRateMultiplier
        } catch (e: Throwable) {
            CatchRateMod.debugOnChange("CobbleCuisineErr", "config", "Failed to read CobbleCuisine config: ${e.message}")
            2F
        }
    }
}