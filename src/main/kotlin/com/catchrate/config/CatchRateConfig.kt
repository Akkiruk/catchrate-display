package com.catchrate.config

import com.catchrate.CatchRateDisplayMod
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

data class CatchRateConfig(
    var hudAnchor: HudAnchor = HudAnchor.BOTTOM_CENTER,
    var hudOffsetX: Int = 0,
    var hudOffsetY: Int = -50,
    var hudEnabled: Boolean = true,
    var showOutOfCombat: Boolean = true,
    var compactMode: Boolean = false,
    var showBallComparison: Boolean = false
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
            FabricLoader.getInstance().configDir.resolve("catchrate-display.json").toFile()
        }
        
        private var instance: CatchRateConfig? = null
        
        fun get(): CatchRateConfig {
            if (instance == null) {
                instance = load()
            }
            return instance!!
        }
        
        fun load(): CatchRateConfig {
            return try {
                if (configFile.exists()) {
                    gson.fromJson(configFile.readText(), CatchRateConfig::class.java)
                } else {
                    CatchRateConfig().also { it.save() }
                }
            } catch (e: Exception) {
                CatchRateDisplayMod.LOGGER.warn("Failed to load config, using defaults: ${e.message}")
                CatchRateConfig().also { it.save() }
            }
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
            CatchRateDisplayMod.LOGGER.error("Failed to save config: ${e.message}")
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
