package com.catchrate.network

import com.catchrate.CatchRateMod
import com.catchrate.platform.PlatformHelper
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side networking for catch rate display.
 * 
 * Platform-agnostic: uses PlatformHelper for sending packets and checking server presence.
 * Platform entrypoints should call:
 * - onResponseReceived() when a response payload arrives
 * - onConnect() when joining a server
 * - onDisconnect() when disconnecting
 */
object CatchRateClientNetworking {
    
    private val responseCache = ConcurrentHashMap<UUID, CatchRateResponsePayload>()
    
    private data class RequestKey(val pokemonUuid: UUID, val ballId: String, val turnCount: Int)
    private var lastRequest: RequestKey? = null
    private var lastRequestTime = 0L
    private const val REQUEST_COOLDOWN_MS = 250L
    
    // World (out-of-combat) request tracking (responses stored in main responseCache via onResponseReceived)
    private data class WorldRequestKey(val entityId: Int, val ballId: String)
    private var lastWorldRequest: WorldRequestKey? = null
    private var lastWorldRequestTime = 0L
    private const val WORLD_REQUEST_COOLDOWN_MS = 300L
    
    private var hasShownServerWarning = false
    private var hasConnectedToServer = false
    private var serverCheckPerformed = false
    
    /**
     * Called by platform layer when a CatchRateResponsePayload is received from the server.
     */
    fun onResponseReceived(payload: CatchRateResponsePayload) {
        responseCache[payload.pokemonUuid] = payload
        CatchRateMod.debug(
            "Received: ${payload.pokemonName} Lv${payload.pokemonLevel}, ${payload.ballName} ${payload.ballMultiplier}x = ${String.format("%.1f", payload.catchChance)}%"
        )
    }
    
    /**
     * Called by platform layer when client connects to a server.
     */
    fun onConnect() {
        hasConnectedToServer = true
        serverCheckPerformed = false
        hasShownServerWarning = false
    }
    
    /**
     * Called by platform layer when client disconnects from a server.
     */
    fun onDisconnect() {
        clearCache()
        hasShownServerWarning = false
        hasConnectedToServer = false
        serverCheckPerformed = false
    }
    
    fun isServerModPresent(): Boolean = PlatformHelper.isServerModPresent()
    
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
        
        PlatformHelper.sendToServer(CatchRateRequestPayload(pokemonUuid, ballItemId, turnCount))
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
    
    // ==================== WORLD (OUT-OF-COMBAT) REQUESTS ====================
    
    /**
     * Request catch rate for a world Pokemon (out of combat).
     * Uses entity network ID instead of UUID since that's always available client-side.
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
        
        PlatformHelper.sendToServer(WorldCatchRateRequestPayload(entityId, ballItemId))
        return true
    }
    
    /**
     * Get cached world response by Pokemon UUID (responses use UUID from server lookup).
     */
    fun getCachedWorldResponse(pokemonUuid: UUID): CatchRateResponsePayload? = responseCache[pokemonUuid]
    
    fun clearWorldCache() {
        lastWorldRequest = null
    }
    
    private fun showServerWarning() {
        hasShownServerWarning = true
        Minecraft.getInstance().execute {
            Minecraft.getInstance().player?.displayClientMessage(
                Component.literal("[Catch Rate] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Server mod not installed - some balls may not calculate correctly.").withStyle(ChatFormatting.GRAY)),
                false
            )
        }
    }
}
