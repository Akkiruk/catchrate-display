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
    private val worldResponseCache = ConcurrentHashMap<Int, CatchRateResponsePayload>()
    
    private data class RequestKey(val pokemonUuid: UUID, val ballId: String, val turnCount: Int)
    private var lastRequest: RequestKey? = null
    private var lastRequestTime = 0L
    private const val REQUEST_COOLDOWN_MS = 250L
    
    private data class WorldRequestKey(val entityId: Int, val ballId: String)
    private var lastWorldRequest: WorldRequestKey? = null
    private var lastWorldRequestTime = 0L
    private const val WORLD_REQUEST_COOLDOWN_MS = 300L
    
    private var hasShownServerWarning = false
    private var hasConnectedToServer = false
    private var serverCheckPerformed = false
    
    fun initialize() {
        try {
            PayloadTypeRegistry.playS2C().register(CatchRateResponsePayload.ID, CatchRateResponsePayload.CODEC)
            PayloadTypeRegistry.playC2S().register(CatchRateRequestPayload.ID, CatchRateRequestPayload.CODEC)
            PayloadTypeRegistry.playC2S().register(WorldCatchRateRequestPayload.ID, WorldCatchRateRequestPayload.CODEC)
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
        worldResponseCache.clear()
        lastRequest = null
        lastWorldRequest = null
    }
    
    // ==================== WORLD (OUT-OF-COMBAT) METHODS ====================
    
    /**
     * Request a catch rate calculation from the server for a world Pokemon (out-of-combat).
     * Uses the entity's network ID to identify the Pokemon on the server.
     */
    fun requestWorldCatchRate(entityId: Int, ballItemId: String): Boolean {
        if (!isServerModPresent()) return false
        
        val now = System.currentTimeMillis()
        val requestKey = WorldRequestKey(entityId, ballItemId)
        
        if (requestKey == lastWorldRequest && (now - lastWorldRequestTime) < WORLD_REQUEST_COOLDOWN_MS) {
            return false
        }
        
        lastWorldRequest = requestKey
        lastWorldRequestTime = now
        
        ClientPlayNetworking.send(WorldCatchRateRequestPayload(entityId, ballItemId))
        return true
    }
    
    /**
     * Get cached world catch rate response by Pokemon UUID.
     * The response uses the Pokemon's UUID (not entity ID), so we look up by UUID.
     */
    fun getCachedWorldResponse(pokemonUuid: UUID): CatchRateResponsePayload? {
        // World responses go into the same responseCache (keyed by Pokemon UUID)
        return responseCache[pokemonUuid]
    }
    
    fun clearWorldCache() {
        worldResponseCache.clear()
        lastWorldRequest = null
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
