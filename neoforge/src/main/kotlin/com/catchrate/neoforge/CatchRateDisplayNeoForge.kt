package com.catchrate.neoforge

import com.catchrate.CatchRateMod
import com.catchrate.network.CatchRateRequestPayload
import com.catchrate.network.CatchRateResponsePayload
import com.catchrate.network.CatchRateServerNetworking
import com.catchrate.network.WorldCatchRateRequestPayload
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * NeoForge main mod entrypoint.
 * Registers packet payloads and server-side handlers.
 */
@Mod(CatchRateMod.MOD_ID)
class CatchRateDisplayNeoForge(modBus: IEventBus) {
    
    init {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] NeoForge init")
        modBus.addListener(::onRegisterPayloadHandlers)
    }
    
    private fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(CatchRateMod.MOD_ID)
        
        // Register C2S (client → server) request payload (in-battle)
        registrar.playToServer(
            CatchRateRequestPayload.TYPE,
            CatchRateRequestPayload.CODEC
        ) { payload: CatchRateRequestPayload, context: IPayloadContext ->
            context.enqueueWork {
                val player = context.player()
                if (player is net.minecraft.server.level.ServerPlayer) {
                    CatchRateServerNetworking.handleCatchRateRequest(player, payload)
                }
            }
        }
        
        // Register C2S (client → server) world request payload (out-of-combat)
        registrar.playToServer(
            WorldCatchRateRequestPayload.TYPE,
            WorldCatchRateRequestPayload.CODEC
        ) { payload: WorldCatchRateRequestPayload, context: IPayloadContext ->
            context.enqueueWork {
                val player = context.player()
                if (player is net.minecraft.server.level.ServerPlayer) {
                    CatchRateServerNetworking.handleWorldCatchRateRequest(player, payload)
                }
            }
        }
        
        // Register S2C (server → client) response payload
        registrar.playToClient(
            CatchRateResponsePayload.TYPE,
            CatchRateResponsePayload.CODEC
        ) { payload: CatchRateResponsePayload, context: IPayloadContext ->
            context.enqueueWork {
                com.catchrate.network.CatchRateClientNetworking.onResponseReceived(payload)
            }
        }
    }
}
