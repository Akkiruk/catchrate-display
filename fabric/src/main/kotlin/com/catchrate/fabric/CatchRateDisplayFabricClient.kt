package com.catchrate.fabric

import com.catchrate.CatchRateKeybinds
import com.catchrate.CatchRateMod
import com.catchrate.client.CatchRateHudRenderer
import com.catchrate.network.CatchRateClientNetworking
import com.catchrate.network.CatchRateRequestPayload
import com.catchrate.network.CatchRateResponsePayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry

/**
 * Fabric client-side entrypoint.
 * Registers keybinds, HUD renderer, and client networking.
 */
class CatchRateDisplayFabricClient : ClientModInitializer {
    
    private val hudRenderer = CatchRateHudRenderer()
    
    override fun onInitializeClient() {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] Fabric client init")
        
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
        
        // Register packet types (client side)
        try {
            PayloadTypeRegistry.playS2C().register(CatchRateResponsePayload.TYPE, CatchRateResponsePayload.CODEC)
            PayloadTypeRegistry.playC2S().register(CatchRateRequestPayload.TYPE, CatchRateRequestPayload.CODEC)
        } catch (e: IllegalArgumentException) {
            // Already registered
        }
        
        // Register client networking - response receiver
        ClientPlayNetworking.registerGlobalReceiver(CatchRateResponsePayload.TYPE) { payload, _ ->
            CatchRateClientNetworking.onResponseReceived(payload)
        }
        
        // Register connection events
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            CatchRateClientNetworking.onConnect()
        }
        
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            CatchRateClientNetworking.onDisconnect()
        }
    }
}
