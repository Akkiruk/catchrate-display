package com.catchrate.mixin.client

import com.catchrate.CatchRateCaptureTracker
import com.cobblemon.mod.common.client.net.battle.BattleCaptureStartHandler
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureStartPacket
import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(BattleCaptureStartHandler::class)
abstract class BattleCaptureStartHandlerMixin {

    @Inject(
        method = ["handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleCaptureStartPacket;Lnet/minecraft/client/Minecraft;)V"],
        at = [At("TAIL")]
    )
    private fun onCaptureStartInjected(packet: BattleCaptureStartPacket, client: Minecraft, ci: CallbackInfo) {
        CatchRateCaptureTracker.onCaptureStart(packet)
    }
}