package com.catchrate.fabric

import com.catchrate.CatchRateKeybinds
import com.catchrate.CatchRateBattleMonitor
import com.catchrate.CatchRateMod
import com.catchrate.SpeciesCatchRateCache
import com.catchrate.DebugCommands
import com.catchrate.client.CatchRateHudRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback

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
            CatchRateBattleMonitor.onClientTick()
            CatchRateKeybinds.tick(minecraft)
        }
        
        // Register /catchrate command using Fabric's client command API
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            DebugCommands.register(dispatcher, ClientCommandManager::literal) { source, message ->
                source.sendFeedback(message)
            }
            CatchRateMod.LOGGER.info("[CatchRateDisplay] Registered /catchrate command")
        }
    }
}
