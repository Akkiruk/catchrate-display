package com.catchrate

import com.catchrate.config.CatchRateConfig
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.lwjgl.glfw.GLFW

object CatchRateKeybinds {
    private lateinit var toggleHudKey: KeyBinding
    private lateinit var showComparisonKey: KeyBinding
    private lateinit var resetPositionKey: KeyBinding
    private lateinit var moveUpKey: KeyBinding
    private lateinit var moveDownKey: KeyBinding
    private lateinit var moveLeftKey: KeyBinding
    private lateinit var moveRightKey: KeyBinding
    private lateinit var cycleAnchorKey: KeyBinding
    
    var isComparisonHeld = false
        private set
    
    /** Display name of the comparison keybind, for use in UI text */
    val comparisonKeyName: String
        get() = showComparisonKey.boundKeyLocalizedText.string
    
    private fun bind(name: String, key: Int) = KeyBindingHelper.registerKeyBinding(
        KeyBinding("key.catchrate.$name", InputUtil.Type.KEYSYM, key, "category.catchrate")
    )
    
    fun register() {
        toggleHudKey = bind("toggle_hud", GLFW.GLFW_KEY_K)
        showComparisonKey = bind("show_comparison", GLFW.GLFW_KEY_G)
        resetPositionKey = bind("reset_position", GLFW.GLFW_KEY_UNKNOWN)
        moveUpKey = bind("move_up", GLFW.GLFW_KEY_UNKNOWN)
        moveDownKey = bind("move_down", GLFW.GLFW_KEY_UNKNOWN)
        moveLeftKey = bind("move_left", GLFW.GLFW_KEY_UNKNOWN)
        moveRightKey = bind("move_right", GLFW.GLFW_KEY_UNKNOWN)
        cycleAnchorKey = bind("cycle_anchor", GLFW.GLFW_KEY_UNKNOWN)
    }
    
    fun tick(client: MinecraftClient) {
        val config = CatchRateConfig.get()
        val player = client.player
        
        while (toggleHudKey.wasPressed()) {
            config.hudEnabled = !config.hudEnabled
            config.save()
            val status = if (config.hudEnabled) "§aEnabled" else "§cDisabled"
            player?.sendMessage(Text.literal("Catch Rate: $status").formatted(Formatting.GOLD), true)
        }
        
        isComparisonHeld = showComparisonKey.isPressed
        config.showBallComparison = isComparisonHeld
        
        val moveSpeed = 10
        while (moveUpKey.wasPressed()) config.adjustOffset(0, -moveSpeed)
        while (moveDownKey.wasPressed()) config.adjustOffset(0, moveSpeed)
        while (moveLeftKey.wasPressed()) config.adjustOffset(-moveSpeed, 0)
        while (moveRightKey.wasPressed()) config.adjustOffset(moveSpeed, 0)
        
        while (resetPositionKey.wasPressed()) {
            config.resetPosition()
            player?.sendMessage(Text.literal("HUD position reset").formatted(Formatting.GOLD), true)
        }
        
        while (cycleAnchorKey.wasPressed()) {
            config.cycleAnchor()
            player?.sendMessage(Text.literal("Anchor: ${config.hudAnchor.name.replace("_", " ")}").formatted(Formatting.GOLD), true)
        }
        
        config.flushPendingSave()
    }
}
