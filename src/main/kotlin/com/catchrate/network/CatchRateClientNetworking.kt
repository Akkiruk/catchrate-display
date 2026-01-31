package com.catchrate.network

import com.catchrate.CatchRateDisplayMod
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CatchRateClientNetworking {
    
    private val responseCache = ConcurrentHashMap<UUID, CatchRateResponsePayload>()
    
    private data class RequestKey(val pokemonUuid: UUID, val ballId: String, val turnCount: Int)
    private var lastRequest: RequestKey? = null
    private var lastRequestTime = 0L
    private const val REQUEST_COOLDOWN_MS = 250L
    
    private var hasShownServerWarning = false
    private var hasConnectedToServer = false
    private var serverCheckPerformed = false
    
    fun initialize() {
        try {
            PayloadTypeRegistry.playS2C().register(CatchRateResponsePayload.ID, CatchRateResponsePayload.CODEC)
            PayloadTypeRegistry.playC2S().register(CatchRateRequestPayload.ID, CatchRateRequestPayload.CODEC)
        } catch (e: IllegalArgumentException) {
            // Already registered in single-player
        }
        
        ClientPlayNetworking.registerGlobalReceiver(CatchRateResponsePayload.ID) { payload, _ ->
            responseCache[payload.pokemonUuid] = payload
            CatchRateDisplayMod.debug(
                "Received: ${payload.pokemonName} Lv${payload.pokemonLevel}, ${payload.ballName} ${payload.ballMultiplier}x = ${String.format("%.1f", payload.catchChance)}%"
            )
        }
        
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            clearCache()
            hasShownServerWarning = false
            hasConnectedToServer = false
            serverCheckPerformed = false
        }
        
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            hasConnectedToServer = true
            serverCheckPerformed = false
            hasShownServerWarning = false
        }
    }
    
    fun isServerModPresent() = ClientPlayNetworking.canSend(CatchRateRequestPayload.ID)
    
    fun requestCatchRate(pokemonUuid: UUID, ballItemId: String, turnCount: Int): Boolean {
        if (!isServerModPresent()) {
            if (!hasShownServerWarning && hasConnectedToServer && !serverCheckPerformed) {
                serverCheckPerformed = true
                showServerWarning()
            }
            return false
        }
        
        val now = System.currentTimeMillis()
        val requestKey = RequestKey(pokemonUuid, ballItemId, turnCount)
        
        if (requestKey == lastRequest && (now - lastRequestTime) < REQUEST_COOLDOWN_MS) {
            return false
        }
        
        lastRequest = requestKey
        lastRequestTime = now
        
        ClientPlayNetworking.send(CatchRateRequestPayload(pokemonUuid, ballItemId, turnCount))
        return true
    }
    
    fun getCachedResponse(pokemonUuid: UUID): CatchRateResponsePayload? = responseCache[pokemonUuid]
    
    fun invalidateCache(pokemonUuid: UUID) {
        responseCache.remove(pokemonUuid)
    }
    
    fun clearCache() {
        responseCache.clear()
        lastRequest = null
    }
    
    private fun showServerWarning() {
        hasShownServerWarning = true
        MinecraftClient.getInstance().execute {
            MinecraftClient.getInstance().player?.sendMessage(
                Text.literal("[Catch Rate] ").formatted(Formatting.GOLD)
                    .append(Text.literal("Server mod not installed - some balls may not calculate correctly.").formatted(Formatting.GRAY)),
                false
            )
        }
    }
}
