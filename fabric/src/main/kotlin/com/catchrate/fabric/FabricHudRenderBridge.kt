package com.catchrate.fabric

import com.catchrate.client.CatchRateHudRenderer
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics

object FabricHudRenderBridge {
    private val hudRenderer = CatchRateHudRenderer()

    fun render(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        hudRenderer.render(guiGraphics, deltaTracker)
    }
}