package com.catchrate.fabric

import com.catchrate.CatchRateKeybinds
import com.catchrate.CatchRateMod
import com.catchrate.DebugCommands
import com.catchrate.client.CatchRateHudRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext

/**
 * Fabric client-side entrypoint.
 * Registers keybinds and HUD renderer. Pure client-side - no networking.
 */
class CatchRateDisplayFabricClient : ClientModInitializer {
    
    private val hudRenderer = CatchRateHudRenderer()
    
    override fun onInitializeClient() {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] Fabric client init v${CatchRateMod.VERSION}")
        
        if (CatchRateMod.isDebugActive) {
            CatchRateMod.logEnvironmentInfo()
        }
        
        // Register keybinds
        val keyMappings = CatchRateKeybinds.createKeyMappings()
        keyMappings.forEach { KeyBindingHelper.registerKeyBinding(it) }
        
        // Register HUD render callback
        HudRenderCallback.EVENT.register { guiGraphics, deltaTracker ->
            hudRenderer.render(guiGraphics, deltaTracker)
        }
        
        // Register client tick for keybinds
        ClientTickEvents.END_CLIENT_TICK.register { minecraft ->
            CatchRateKeybinds.tick(minecraft)
        }
        
        // Register /catchrate command
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            registerCommands(dispatcher)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun registerCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        val adapted = dispatcher as CommandDispatcher<net.minecraft.commands.CommandSourceStack>
        DebugCommands.register(adapted)
        CatchRateMod.LOGGER.info("[CatchRateDisplay] Registered /catchrate command")
    }
}
