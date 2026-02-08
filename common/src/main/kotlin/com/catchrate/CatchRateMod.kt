package com.catchrate

import org.slf4j.LoggerFactory

/**
 * Shared mod constants and utilities available to all modules.
 */
object CatchRateMod {
    const val MOD_ID = "catchrate-display"
    /** NeoForge requires mod IDs with only [a-z0-9_], no hyphens */
    const val NEOFORGE_MOD_ID = "catchrate_display"
    const val VERSION = "2.1.2"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    /** Controlled by `debugLogging` in config OR by /catchrate debug command. */
    var DEBUG_ENABLED = false
    
    /** Session toggle - overrides config when true. Reset on game restart. */
    var sessionDebugOverride: Boolean? = null
    
    val isDebugActive: Boolean
        get() = sessionDebugOverride ?: DEBUG_ENABLED

    private val lastDebugTimes = mutableMapOf<String, Long>()
    private const val DEBUG_THROTTLE_MS = 2000L
    
    private val lastStateValues = mutableMapOf<String, String>()

    /** Log a debug message. */
    fun debug(message: String) {
        if (isDebugActive) LOGGER.info("[CatchRate DEBUG] $message")
    }

    /** Log a debug message with a category tag. */
    fun debug(category: String, message: String) {
        if (isDebugActive) LOGGER.info("[CatchRate DEBUG/$category] $message")
    }

    /** Throttled debug: logs at most once per 2 seconds per category. */
    fun debugThrottled(category: String, message: String) {
        if (!isDebugActive) return
        val now = System.currentTimeMillis()
        val last = lastDebugTimes[category] ?: 0
        if (now - last < DEBUG_THROTTLE_MS) return
        lastDebugTimes[category] = now
        LOGGER.info("[CatchRate DEBUG/$category] $message")
    }
    
    /** Log only when value changes (for state tracking). */
    fun debugOnChange(key: String, value: String, message: String) {
        if (!isDebugActive) return
        val last = lastStateValues[key]
        if (last != value) {
            lastStateValues[key] = value
            LOGGER.info("[CatchRate CHANGE/$key] $message")
        }
    }
    
    /** Log a calculation breakdown. */
    fun debugCalc(pokemon: String, ball: String, hp: String, status: String, result: String) {
        if (!isDebugActive) return
        LOGGER.info("[CatchRate CALC] $pokemon | Ball=$ball | HP=$hp | Status=$status | Result=$result")
    }
    
    /** Log ball multiplier check. */
    fun debugBall(ballName: String, condition: String, multiplier: Float, met: Boolean) {
        if (!isDebugActive) return
        val status = if (met) "✓" else "✗"
        LOGGER.info("[CatchRate BALL] $ballName: $condition -> ${multiplier}x [$status]")
    }
    
    /** Toggle debug mode for this session. Returns new state. */
    fun toggleSessionDebug(): Boolean {
        sessionDebugOverride = !(sessionDebugOverride ?: DEBUG_ENABLED)
        val newState = sessionDebugOverride!!
        if (newState) {
            logEnvironmentInfo()
        }
        return newState
    }
    
    /** Log environment info (version, loader, etc). */
    fun logEnvironmentInfo() {
        LOGGER.info("========== CatchRateDisplay Debug Info ==========")
        LOGGER.info("  Mod Version: $VERSION")
        LOGGER.info("  Minecraft: ${getMinecraftVersion()}")
        LOGGER.info("  Loader: ${getLoaderInfo()}")
        LOGGER.info("  Cobblemon: ${getCobblemonVersion()}")
        LOGGER.info("  Config debugLogging: $DEBUG_ENABLED")
        LOGGER.info("  Session override: $sessionDebugOverride")
        LOGGER.info("==================================================")
    }
    
    private fun getMinecraftVersion(): String {
        return try {
            net.minecraft.SharedConstants.getCurrentVersion().name
        } catch (e: Exception) { "unknown" }
    }
    
    private fun getLoaderInfo(): String {
        return try {
            if (Class.forName("net.fabricmc.loader.api.FabricLoader") != null) "Fabric"
            else "Unknown"
        } catch (e: Exception) {
            try {
                if (Class.forName("net.neoforged.fml.loading.FMLLoader") != null) "NeoForge"
                else "Unknown"
            } catch (e2: Exception) { "Unknown" }
        }
    }
    
    private fun getCobblemonVersion(): String {
        return try {
            com.cobblemon.mod.common.Cobblemon.VERSION
        } catch (e: Exception) { "unknown" }
    }
}
