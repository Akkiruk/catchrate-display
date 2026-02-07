package com.catchrate.fabric

import com.catchrate.CatchRateMod
import com.catchrate.network.CatchRateRequestPayload
import com.catchrate.network.CatchRateResponsePayload
import com.catchrate.network.CatchRateServerNetworking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

/**
 * Fabric server/common entrypoint.
 * Registers packet types and server networking handlers.
 */
class CatchRateDisplayFabric : ModInitializer {
    override fun onInitialize() {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] Fabric common init")
        
        // Register payload types (server side)
        try {
            PayloadTypeRegistry.playC2S().register(CatchRateRequestPayload.TYPE, CatchRateRequestPayload.CODEC)
            PayloadTypeRegistry.playS2C().register(CatchRateResponsePayload.TYPE, CatchRateResponsePayload.CODEC)
        } catch (e: IllegalArgumentException) {
            // Already registered in single-player (client init ran first)
        }
        
        // Register server-side request handler
        ServerPlayNetworking.registerGlobalReceiver(CatchRateRequestPayload.TYPE) { payload, context ->
            CatchRateServerNetworking.handleCatchRateRequest(context.player(), payload)
        }
    }
}
