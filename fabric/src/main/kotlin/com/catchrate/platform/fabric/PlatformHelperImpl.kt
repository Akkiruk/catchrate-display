@file:JvmName("PlatformHelperImpl")

package com.catchrate.platform.fabric

import com.catchrate.network.CatchRateRequestPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

/**
 * Fabric implementation of PlatformHelper.
 * 
 * IMPORTANT: This class MUST be at com.catchrate.platform.fabric.PlatformHelperImpl
 * to match the @ExpectPlatform convention: {original_package}.{platform}.{ClassName}Impl
 */
object PlatformHelperImpl {
    
    @JvmStatic
    fun getConfigDir(): Path {
        return FabricLoader.getInstance().configDir
    }
    
    @JvmStatic
    fun isServerModPresent(): Boolean {
        return ClientPlayNetworking.canSend(CatchRateRequestPayload.TYPE)
    }
    
    @JvmStatic
    fun sendToServer(payload: CustomPacketPayload) {
        ClientPlayNetworking.send(payload)
    }
    
    @JvmStatic
    fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload) {
        ServerPlayNetworking.send(player, payload)
    }
}
