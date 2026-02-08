package com.catchrate

import org.slf4j.LoggerFactory

/**
 * Shared mod constants and utilities available to all modules.
 */
object CatchRateMod {
    const val MOD_ID = "catchrate-display"
    /** NeoForge requires mod IDs with only [a-z0-9_], no hyphens */
    const val NEOFORGE_MOD_ID = "catchrate_display"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    /** Controlled by `debugLogging` in catchrate-display.json config. */
    var DEBUG_ENABLED = false

    private val lastDebugTimes = mutableMapOf<String, Long>()
    private const val DEBUG_THROTTLE_MS = 2000L

    /** Log a debug message (only when debugLogging is enabled in config). */
    fun debug(message: String) {
        if (DEBUG_ENABLED) LOGGER.info("[CatchRate DEBUG] $message")
    }

    /** Log a debug message with a category tag for easier filtering. */
    fun debug(category: String, message: String) {
        if (DEBUG_ENABLED) LOGGER.info("[CatchRate DEBUG/$category] $message")
    }

    /** Throttled debug: logs at most once per 2 seconds per category. */
    fun debugThrottled(category: String, message: String) {
        if (!DEBUG_ENABLED) return
        val now = System.currentTimeMillis()
        val last = lastDebugTimes[category] ?: 0
        if (now - last < DEBUG_THROTTLE_MS) return
        lastDebugTimes[category] = now
        LOGGER.info("[CatchRate DEBUG/$category] $message")
    }
}
