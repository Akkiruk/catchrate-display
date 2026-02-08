package com.catchrate

import com.catchrate.config.CatchRateConfig
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * Client-side commands for CatchRateDisplay.
 * /catchrate debug - toggles debug logging for this session
 */
object DebugCommands {
    
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("catchrate")
                .then(Commands.literal("debug")
                    .executes { ctx -> toggleDebug(ctx) }
                )
                .then(Commands.literal("info")
                    .executes { ctx -> showInfo(ctx) }
                )
        )
    }
    
    private fun toggleDebug(ctx: CommandContext<CommandSourceStack>): Int {
        val newState = CatchRateMod.toggleSessionDebug()
        val message = if (newState) {
            Component.translatable("catchrate.command.debug.enabled")
        } else {
            Component.translatable("catchrate.command.debug.disabled")
        }
        ctx.source.sendSuccess({ message }, false)
        return 1
    }
    
    private fun showInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val config = CatchRateConfig.get()
        
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.header") }, false)
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.version", CatchRateMod.VERSION) }, false)
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.debug_config", CatchRateMod.DEBUG_ENABLED.toString()) }, false)
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.debug_session", (CatchRateMod.sessionDebugOverride?.toString() ?: "not set")) }, false)
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.debug_active", CatchRateMod.isDebugActive.toString()) }, false)
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.hud_enabled", config.hudEnabled.toString()) }, false)
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.show_ooc", config.showOutOfCombat.toString()) }, false)
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.footer") }, false)
        
        CatchRateMod.logEnvironmentInfo()
        return 1
    }
}
