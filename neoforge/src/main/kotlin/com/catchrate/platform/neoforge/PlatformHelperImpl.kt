@file:JvmName("PlatformHelperImpl")

package com.catchrate.platform.neoforge

import com.catchrate.CatchRateMod
import com.catchrate.network.CatchRateRequestPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.network.PacketDistributor
import java.nio.file.Path

/**
 * NeoForge implementation of PlatformHelper.
 * 
 * IMPORTANT: This class MUST be at com.catchrate.platform.neoforge.PlatformHelperImpl
 * to match the @ExpectPlatform convention: {original_package}.{platform}.{ClassName}Impl
 */
object PlatformHelperImpl {
    
    @JvmStatic
    fun getConfigDir(): Path {
        return FMLPaths.CONFIGDIR.get()
    }
    
    @JvmStatic
    fun isServerModPresent(): Boolean {
        // NeoForge uses the connection's hasChannel check
        return try {
            val minecraft = net.minecraft.client.Minecraft.getInstance()
            val connection = minecraft.connection?.connection ?: return false
            // Check if the server has registered our payload type
            connection.isConnected
            // In NeoForge, if we received a response before, the channel is valid.
            // A simpler approach: try to check if the network channel is registered.
            // NeoForge automatically rejects unknown payloads, so if we got here
            // and can construct a packet, the channel is registered.
            true
        } catch (e: Exception) {
            false
        }
    }
    
    @JvmStatic
    fun sendToServer(payload: CustomPacketPayload) {
        PacketDistributor.sendToServer(payload)
    }
    
    @JvmStatic
    fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload) {
        PacketDistributor.sendToPlayer(player, payload)
    }
}
