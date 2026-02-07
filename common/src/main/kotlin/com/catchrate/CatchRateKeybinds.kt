package com.catchrate

import com.catchrate.config.CatchRateConfig
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW

/**
 * Translation helper for keybind feedback messages.
 */
object KeybindTranslations {
    fun enabled() = Component.translatable("catchrate.message.enabled").string
    fun disabled() = Component.translatable("catchrate.message.disabled").string
    fun positionReset() = Component.translatable("catchrate.message.position_reset").string
    fun anchor(anchorName: String) = Component.translatable("catchrate.message.anchor", anchorName).string
}

object CatchRateKeybinds {
    lateinit var toggleHudKey: KeyMapping
        private set
    lateinit var showComparisonKey: KeyMapping
        private set
    lateinit var resetPositionKey: KeyMapping
        private set
    lateinit var moveUpKey: KeyMapping
        private set
    lateinit var moveDownKey: KeyMapping
        private set
    lateinit var moveLeftKey: KeyMapping
        private set
    lateinit var moveRightKey: KeyMapping
        private set
    lateinit var cycleAnchorKey: KeyMapping
        private set
    
    var isComparisonHeld = false
        private set
    
    /** Display name of the comparison keybind, for use in UI text */
    val comparisonKeyName: String
        get() = showComparisonKey.translatedKeyMessage.string
    
    private fun createMapping(name: String, key: Int) = KeyMapping(
        "key.catchrate.$name", InputConstants.Type.KEYSYM, key, "category.catchrate"
    )
    
    /**
     * Creates all KeyMapping instances. Call this during client init.
     * Returns the list of all key mappings for platform-specific registration.
     */
    fun createKeyMappings(): List<KeyMapping> {
        toggleHudKey = createMapping("toggle_hud", GLFW.GLFW_KEY_K)
        showComparisonKey = createMapping("show_comparison", GLFW.GLFW_KEY_G)
        resetPositionKey = createMapping("reset_position", GLFW.GLFW_KEY_UNKNOWN)
        moveUpKey = createMapping("move_up", GLFW.GLFW_KEY_UNKNOWN)
        moveDownKey = createMapping("move_down", GLFW.GLFW_KEY_UNKNOWN)
        moveLeftKey = createMapping("move_left", GLFW.GLFW_KEY_UNKNOWN)
        moveRightKey = createMapping("move_right", GLFW.GLFW_KEY_UNKNOWN)
        cycleAnchorKey = createMapping("cycle_anchor", GLFW.GLFW_KEY_UNKNOWN)
        
        return listOf(
            toggleHudKey, showComparisonKey, resetPositionKey,
            moveUpKey, moveDownKey, moveLeftKey, moveRightKey, cycleAnchorKey
        )
    }
    
    fun tick(minecraft: Minecraft) {
        val config = CatchRateConfig.get()
        val player = minecraft.player
        
        while (toggleHudKey.consumeClick()) {
            config.hudEnabled = !config.hudEnabled
            config.save()
            val status = if (config.hudEnabled) KeybindTranslations.enabled() else KeybindTranslations.disabled()
            player?.displayClientMessage(Component.literal(status).withStyle(ChatFormatting.GOLD), true)
        }
        
        isComparisonHeld = showComparisonKey.isDown
        config.showBallComparison = isComparisonHeld
        
        val moveSpeed = 10
        while (moveUpKey.consumeClick()) config.adjustOffset(0, -moveSpeed)
        while (moveDownKey.consumeClick()) config.adjustOffset(0, moveSpeed)
        while (moveLeftKey.consumeClick()) config.adjustOffset(-moveSpeed, 0)
        while (moveRightKey.consumeClick()) config.adjustOffset(moveSpeed, 0)
        
        while (resetPositionKey.consumeClick()) {
            config.resetPosition()
            player?.displayClientMessage(Component.literal(KeybindTranslations.positionReset()).withStyle(ChatFormatting.GOLD), true)
        }
        
        while (cycleAnchorKey.consumeClick()) {
            config.cycleAnchor()
            player?.displayClientMessage(Component.literal(KeybindTranslations.anchor(config.hudAnchor.name.replace("_", " "))).withStyle(ChatFormatting.GOLD), true)
        }
        
        config.flushPendingSave()
    }
}
