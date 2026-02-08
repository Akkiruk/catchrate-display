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
    var DEBUG_ENABLED = false  // Set to true only for development

    fun debug(message: String) {
        if (DEBUG_ENABLED) LOGGER.info("[DEBUG] $message")
    }
}
