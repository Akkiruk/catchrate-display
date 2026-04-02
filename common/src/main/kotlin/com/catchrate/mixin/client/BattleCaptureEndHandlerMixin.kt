package com.catchrate.mixin.client

import com.catchrate.CatchRateCaptureTracker
import com.cobblemon.mod.common.client.net.battle.BattleCaptureEndHandler
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureEndPacket
import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(BattleCaptureEndHandler::class)
abstract class BattleCaptureEndHandlerMixin {

    @Inject(
        method = ["handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleCaptureEndPacket;Lnet/minecraft/client/Minecraft;)V"],
        at = [At("TAIL")]
    )
    private fun onCaptureEndInjected(packet: BattleCaptureEndPacket, client: Minecraft, ci: CallbackInfo) {
        CatchRateCaptureTracker.onCaptureEnd(packet)
    }
}