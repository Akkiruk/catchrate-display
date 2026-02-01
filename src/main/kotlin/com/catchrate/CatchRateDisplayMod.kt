package com.catchrate

import com.catchrate.client.CatchRateHudRenderer
import com.catchrate.config.CatchRateConfig
import com.catchrate.network.CatchRateClientNetworking
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import org.slf4j.LoggerFactory

class CatchRateDisplayMod : ClientModInitializer {
    companion object {
        const val MOD_ID = "catchrate-display"
        val LOGGER = LoggerFactory.getLogger(MOD_ID)
        var DEBUG_ENABLED = true
        
        fun debug(message: String) {
            if (DEBUG_ENABLED) LOGGER.info("[DEBUG] $message")
        }
    }

    override fun onInitializeClient() {
        CatchRateConfig.get()
        CatchRateKeybinds.register()
        CatchRateClientNetworking.initialize()
        ClientTickEvents.END_CLIENT_TICK.register { CatchRateKeybinds.tick(it) }
        HudRenderCallback.EVENT.register(CatchRateHudRenderer())
        LOGGER.info("Client initialized")
    }
}

