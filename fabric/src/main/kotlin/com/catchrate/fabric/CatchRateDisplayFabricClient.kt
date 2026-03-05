package com.catchrate.fabric

import com.catchrate.CatchRateDebugLog
import com.catchrate.CatchRateKeybinds
import com.catchrate.CatchRateMod
import com.catchrate.SpeciesCatchRateCache
import com.catchrate.client.CatchRateHudRenderer
import com.catchrate.config.CatchRateConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * Fabric client-side entrypoint.
 * Registers keybinds and HUD renderer. Pure client-side - no networking.
 */
class CatchRateDisplayFabricClient : ClientModInitializer {
    
    private val hudRenderer = CatchRateHudRenderer()
    
    override fun onInitializeClient() {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] Fabric client init v${CatchRateMod.VERSION}")
        
        if (CatchRateMod.isDebugActive) {
            CatchRateMod.logEnvironmentInfo()
        }
        
        // Preload catch rate cache on background thread to avoid lag spike on first Pokemon lookup
        SpeciesCatchRateCache.preloadAsync()
        
        // Register keybinds
        val keyMappings = CatchRateKeybinds.createKeyMappings()
        keyMappings.forEach { KeyBindingHelper.registerKeyBinding(it) }
        
        // Register HUD render callback
        HudRenderCallback.EVENT.register { guiGraphics, deltaTracker ->
            hudRenderer.render(guiGraphics, deltaTracker)
        }
        
        // Register client tick for keybinds
        ClientTickEvents.END_CLIENT_TICK.register { minecraft ->
            CatchRateKeybinds.tick(minecraft)
        }
        
        // Register /catchrate command using Fabric's client command API
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("catchrate")
                    .then(ClientCommandManager.literal("debug")
                        .executes { ctx -> toggleDebug(ctx.source) }
                    )
                    .then(ClientCommandManager.literal("info")
                        .executes { ctx -> showInfo(ctx.source) }
                    )
                    .then(ClientCommandManager.literal("log")
                        .executes { ctx -> uploadLog(ctx.source) }
                    )
            )
            CatchRateMod.LOGGER.info("[CatchRateDisplay] Registered /catchrate command")
        }
    }
    
    private fun toggleDebug(source: FabricClientCommandSource): Int {
        val newState = CatchRateMod.toggleSessionDebug()
        val message = if (newState) {
            Component.translatable("catchrate.command.debug.enabled")
        } else {
            Component.translatable("catchrate.command.debug.disabled")
        }
        source.sendFeedback(message)
        return 1
    }
    
    private fun showInfo(source: FabricClientCommandSource): Int {
        val config = CatchRateConfig.get()
        
        source.sendFeedback(Component.translatable("catchrate.command.info.header"))
        source.sendFeedback(Component.translatable("catchrate.command.info.version", CatchRateMod.VERSION))
        source.sendFeedback(Component.translatable("catchrate.command.info.debug_config", CatchRateMod.DEBUG_ENABLED.toString()))
        source.sendFeedback(Component.translatable("catchrate.command.info.debug_session", (CatchRateMod.sessionDebugOverride?.toString() ?: "not set")))
        source.sendFeedback(Component.translatable("catchrate.command.info.debug_active", CatchRateMod.isDebugActive.toString()))
        source.sendFeedback(Component.translatable("catchrate.command.info.hud_enabled", config.hudEnabled.toString()))
        source.sendFeedback(Component.translatable("catchrate.command.info.show_ooc", config.showOutOfCombat.toString()))
        source.sendFeedback(Component.translatable("catchrate.command.info.hide_unencountered", config.hideUnencounteredInfo.toString()))
        source.sendFeedback(Component.translatable("catchrate.command.info.footer"))
        
        CatchRateMod.logEnvironmentInfo()
        return 1
    }
    
    private fun uploadLog(source: FabricClientCommandSource): Int {
        source.sendFeedback(Component.translatable("catchrate.command.log.uploading"))
        
        val localPath = try { CatchRateDebugLog.saveToFile() } catch (_: Throwable) { null }
        
        CatchRateDebugLog.uploadToMcloGs { success, urlOrError ->
            val minecraft = Minecraft.getInstance()
            minecraft.execute {
                val player = minecraft.player ?: return@execute
                if (success) {
                    player.sendSystemMessage(Component.translatable("catchrate.command.log.success"))
                    player.sendSystemMessage(
                        Component.literal("\u00a7b\u00a7n$urlOrError")
                            .withStyle(Style.EMPTY.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, urlOrError)))
                    )
                    player.sendSystemMessage(Component.translatable("catchrate.command.log.instructions"))
                } else {
                    player.sendSystemMessage(Component.translatable("catchrate.command.log.failed", urlOrError))
                    if (localPath != null) {
                        player.sendSystemMessage(Component.translatable("catchrate.command.log.saved_locally", localPath))
                    }
                }
            }
        }
        return 1
    }
}
