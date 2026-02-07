package com.catchrate.neoforge

import com.catchrate.CatchRateKeybinds
import com.catchrate.CatchRateMod
import com.catchrate.client.CatchRateHudRenderer
import com.catchrate.network.CatchRateClientNetworking
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.gui.GuiLayerManager

/**
 * NeoForge client-side event handler.
 * Uses @EventBusSubscriber for mod bus events (keymappings, GUI layers)
 * and manual registration for game bus events (tick, connection).
 */
@EventBusSubscriber(modid = CatchRateMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object CatchRateDisplayNeoForgeClient {
    
    private val hudRenderer = CatchRateHudRenderer()
    
    @SubscribeEvent
    @JvmStatic
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] NeoForge registering key mappings")
        val keyMappings = CatchRateKeybinds.createKeyMappings()
        keyMappings.forEach { event.register(it) }
    }
    
    @SubscribeEvent
    @JvmStatic
    fun onRegisterGuiLayers(event: RegisterGuiLayersEvent) {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] NeoForge registering GUI layer")
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
@EventBusSubscriber(modid = CatchRateMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object CatchRateDisplayNeoForgeClientEvents {
    
    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.player != null) {
            CatchRateKeybinds.tick(minecraft)
        }
    }
    
    @SubscribeEvent
    @JvmStatic
    fun onClientConnect(event: ClientPlayerNetworkEvent.LoggingIn) {
        CatchRateClientNetworking.onConnect()
    }
    
    @SubscribeEvent
    @JvmStatic
    fun onClientDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        CatchRateClientNetworking.onDisconnect()
    }
}
