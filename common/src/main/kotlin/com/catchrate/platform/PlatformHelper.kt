package com.catchrate.platform

import dev.architectury.injectables.annotations.ExpectPlatform
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

/**
 * Platform abstraction via @ExpectPlatform.
 * Each platform provides implementations in {package}.fabric.platform.PlatformHelperImpl
 * or {package}.neoforge.platform.PlatformHelperImpl respectively.
 */
object PlatformHelper {

    @JvmStatic
    @ExpectPlatform
    fun getConfigDir(): Path = throw AssertionError("@ExpectPlatform not replaced")

    @JvmStatic
    @ExpectPlatform
    fun isServerModPresent(): Boolean = throw AssertionError("@ExpectPlatform not replaced")

    @JvmStatic
    @ExpectPlatform
    fun sendToServer(payload: CustomPacketPayload): Unit = throw AssertionError("@ExpectPlatform not replaced")

    @JvmStatic
    @ExpectPlatform
    fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload): Unit = throw AssertionError("@ExpectPlatform not replaced")
}
