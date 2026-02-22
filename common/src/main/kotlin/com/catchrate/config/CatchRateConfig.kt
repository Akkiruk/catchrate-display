package com.catchrate.config

import com.catchrate.CatchRateMod
import com.catchrate.platform.PlatformHelper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.File

data class CatchRateConfig(
    var hudAnchor: HudAnchor = HudAnchor.BOTTOM_CENTER,
    var hudOffsetX: Int = 0,
    var hudOffsetY: Int = -50,
    var hudEnabled: Boolean = true,
    var showOutOfCombat: Boolean = true,
    var compactMode: Boolean = false,
    var showBallComparison: Boolean = false,
    var hideUnencounteredInfo: Boolean = true,
    var debugLogging: Boolean = false
) {
    @Transient private var pendingSave = false
    @Transient private var lastSaveRequest = 0L
    
    enum class HudAnchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }
    
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
        private val configFile: File by lazy {
            PlatformHelper.getConfigDir().resolve("catchrate-display.json").toFile()
        }
        
        private var instance: CatchRateConfig? = null
        
        fun get(): CatchRateConfig {
            if (instance == null) {
                instance = load()
            }
            return instance!!
        }
        
        fun load(): CatchRateConfig {
            val config = try {
                if (configFile.exists()) {
                    loadFromJson(configFile.readText())
                } else {
                    CatchRateConfig()
                }
            } catch (e: Exception) {
                CatchRateMod.LOGGER.warn("Failed to load config, using defaults: ${e.message}")
                CatchRateConfig()
            }
            // Always re-save so new fields get written to the file
            config.save()
            CatchRateMod.DEBUG_ENABLED = config.debugLogging
            if (config.debugLogging) {
                CatchRateMod.LOGGER.info("[CatchRate] Debug logging enabled via config")
            }
            return config
        }

        /**
         * Parse config from JSON, using Kotlin defaults for any missing fields.
         * Gson + Kotlin data classes don't reliably apply defaults for missing fields,
         * so we parse the JsonObject manually.
         */
        private fun loadFromJson(jsonText: String): CatchRateConfig {
            val json = JsonParser.parseString(jsonText).asJsonObject
            return CatchRateConfig(
                hudAnchor = json.get("hudAnchor")?.asString?.let {
                    try { HudAnchor.valueOf(it) } catch (_: Exception) { null }
                } ?: HudAnchor.BOTTOM_CENTER,
                hudOffsetX = json.get("hudOffsetX")?.asInt ?: 0,
                hudOffsetY = json.get("hudOffsetY")?.asInt ?: -50,
                hudEnabled = json.get("hudEnabled")?.asBoolean ?: true,
                showOutOfCombat = json.get("showOutOfCombat")?.asBoolean ?: true,
                compactMode = json.get("compactMode")?.asBoolean ?: false,
                hideUnencounteredInfo = json.get("hideUnencounteredInfo")?.asBoolean ?: true,
                debugLogging = json.get("debugLogging")?.asBoolean ?: false
            )
        }
        
        fun reload() {
            instance = load()
        }
    }
    
    fun save() {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(gson.toJson(this))
            pendingSave = false
        } catch (e: Exception) {
            CatchRateMod.LOGGER.error("Failed to save config: ${e.message}")
        }
    }
    
    fun requestSave() {
        pendingSave = true
        lastSaveRequest = System.currentTimeMillis()
    }
    
    fun flushPendingSave() {
        if (pendingSave && System.currentTimeMillis() - lastSaveRequest > 500) {
            save()
        }
    }
    
    fun getPosition(screenWidth: Int, screenHeight: Int, boxWidth: Int, boxHeight: Int): Pair<Int, Int> {
        val baseX = when (hudAnchor) {
            HudAnchor.TOP_LEFT, HudAnchor.MIDDLE_LEFT, HudAnchor.BOTTOM_LEFT -> 10
            HudAnchor.TOP_CENTER, HudAnchor.MIDDLE_CENTER, HudAnchor.BOTTOM_CENTER -> (screenWidth - boxWidth) / 2
            HudAnchor.TOP_RIGHT, HudAnchor.MIDDLE_RIGHT, HudAnchor.BOTTOM_RIGHT -> screenWidth - boxWidth - 10
        }
        
        val baseY = when (hudAnchor) {
            HudAnchor.TOP_LEFT, HudAnchor.TOP_CENTER, HudAnchor.TOP_RIGHT -> 10
            HudAnchor.MIDDLE_LEFT, HudAnchor.MIDDLE_CENTER, HudAnchor.MIDDLE_RIGHT -> (screenHeight - boxHeight) / 2
            HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_CENTER, HudAnchor.BOTTOM_RIGHT -> screenHeight - boxHeight - 10
        }
        
        return Pair(baseX + hudOffsetX, baseY + hudOffsetY)
    }
    
    fun cycleAnchor() {
        val anchors = HudAnchor.entries.toTypedArray()
        val currentIndex = anchors.indexOf(hudAnchor)
        hudAnchor = anchors[(currentIndex + 1) % anchors.size]
        requestSave()
    }

    fun adjustOffset(dx: Int, dy: Int) {
        hudOffsetX += dx
        hudOffsetY += dy
        requestSave()
    }

    fun resetPosition() {
        hudAnchor = HudAnchor.BOTTOM_CENTER
        hudOffsetX = 0
        hudOffsetY = -50
        save()
    }
}
