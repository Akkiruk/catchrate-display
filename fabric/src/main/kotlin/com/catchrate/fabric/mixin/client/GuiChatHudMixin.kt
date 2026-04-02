package com.catchrate.fabric.mixin.client

import com.catchrate.fabric.FabricHudRenderBridge
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphics
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Gui::class)
abstract class GuiChatHudMixin {

    @Inject(
        method = ["renderChat(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"],
        at = [At("TAIL")]
    )
    private fun renderCatchRateHudAfterChat(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker, ci: CallbackInfo) {
        FabricHudRenderBridge.render(guiGraphics, deltaTracker)
    }
}