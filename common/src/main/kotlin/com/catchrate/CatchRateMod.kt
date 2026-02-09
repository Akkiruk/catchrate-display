package com.catchrate

import org.slf4j.LoggerFactory

/**
 * Shared mod constants and utilities available to all modules.
 */
object CatchRateMod {
    const val MOD_ID = "catchrate-display"
    /** NeoForge requires mod IDs with only [a-z0-9_], no hyphens */
    const val NEOFORGE_MOD_ID = "catchrate_display"
    val VERSION = BUILD_VERSION
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    /** Controlled by `debugLogging` in config OR by /catchrate debug command. */
    var DEBUG_ENABLED = false
    
    /** Session toggle - overrides config when true. Reset on game restart. */
    var sessionDebugOverride: Boolean? = null
    
    val isDebugActive: Boolean
        get() = sessionDebugOverride ?: DEBUG_ENABLED

    private val lastStateValues = mutableMapOf<String, String>()

    /** Log a one-shot debug event (battle start, toggle, error). Not for loops. */
    fun debug(category: String, message: String) {
        if (isDebugActive) LOGGER.info("[CatchRate/$category] $message")
    }

    /** Log only when value changes. Use this for anything called in a render/tick loop. */
    fun debugOnChange(key: String, value: String, message: String) {
        if (!isDebugActive) return
        val last = lastStateValues[key]
        if (last != value) {
            lastStateValues[key] = value
            LOGGER.info("[CatchRate/$key] $message")
        }
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
        } catch (e: Throwable) { "unknown" }
    }
    
    private fun getLoaderInfo(): String {
        return try {
            if (Class.forName("net.fabricmc.loader.api.FabricLoader") != null) "Fabric"
            else "Unknown"
        } catch (e: Throwable) {
            try {
                if (Class.forName("net.neoforged.fml.loading.FMLLoader") != null) "NeoForge"
                else "Unknown"
            } catch (e2: Throwable) { "Unknown" }
        }
    }
    
    private fun getCobblemonVersion(): String {
        return try {
            com.cobblemon.mod.common.Cobblemon.VERSION
        } catch (e: Throwable) { "unknown" }
    }
}
