package com.catchrate.neoforge

import com.catchrate.CatchRateKeybinds
import com.catchrate.CatchRateMod
import com.catchrate.DebugCommands
import com.catchrate.client.CatchRateHudRenderer
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent

/**
 * NeoForge client-side event handler.
 * Pure client-side - no networking.
 */
@EventBusSubscriber(modid = CatchRateMod.NEOFORGE_MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object CatchRateDisplayNeoForgeClient {
    
    private val hudRenderer = CatchRateHudRenderer()
    
    @SubscribeEvent
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] NeoForge registering key mappings")
        val keyMappings = CatchRateKeybinds.createKeyMappings()
        keyMappings.forEach { event.register(it) }
    }
    
    @SubscribeEvent
    fun onRegisterGuiLayers(event: RegisterGuiLayersEvent) {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] NeoForge registering GUI layer v${CatchRateMod.VERSION}")
        if (CatchRateMod.isDebugActive) {
            CatchRateMod.logEnvironmentInfo()
        }
        event.registerAboveAll(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CatchRateMod.MOD_ID, "catch_rate_hud")
        ) { guiGraphics, deltaTracker ->
            hudRenderer.render(guiGraphics, deltaTracker)
        }
    }
}

/**
 * NeoForge game bus event subscriber for client-side runtime events.
 */
@EventBusSubscriber(modid = CatchRateMod.NEOFORGE_MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object CatchRateDisplayNeoForgeClientEvents {
    
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.player != null) {
            CatchRateKeybinds.tick(minecraft)
        }
    }
    
    @SubscribeEvent
    fun onRegisterClientCommands(event: RegisterClientCommandsEvent) {
        DebugCommands.register(event.dispatcher)
        CatchRateMod.LOGGER.info("[CatchRateDisplay] Registered /catchrate command")
    }
}
