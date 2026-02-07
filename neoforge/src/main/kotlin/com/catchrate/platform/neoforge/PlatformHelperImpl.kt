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
        // NeoForge negotiates payload channels during handshake.
        // If the server also has the mod, it registered CatchRateRequestPayload
        // and NeoForge's channel negotiation will have succeeded.
        // ICommonPacketListener.hasChannel() checks the negotiated channels.
        return try {
            val connection = net.minecraft.client.Minecraft.getInstance().connection ?: return false
            connection.hasChannel(CatchRateRequestPayload.TYPE)
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
