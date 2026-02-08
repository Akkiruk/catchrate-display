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
            Component.literal("§a[CatchRate] Debug logging §2ENABLED§a for this session. Check latest.log for detailed output.")
        } else {
            Component.literal("§c[CatchRate] Debug logging §4DISABLED§c for this session.")
        }
        ctx.source.sendSuccess({ message }, false)
        return 1
    }
    
    private fun showInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val config = CatchRateConfig.get()
        val lines = listOf(
            "§6========== CatchRateDisplay Info ==========",
            "§7  Version: §f${CatchRateMod.VERSION}",
            "§7  Debug (config): §f${CatchRateMod.DEBUG_ENABLED}",
            "§7  Debug (session): §f${CatchRateMod.sessionDebugOverride ?: "not set"}",
            "§7  Debug active: §f${CatchRateMod.isDebugActive}",
            "§7  HUD enabled: §f${config.hudEnabled}",
            "§7  Show out of combat: §f${config.showOutOfCombat}",
            "§6============================================"
        )
        lines.forEach { line ->
            ctx.source.sendSuccess({ Component.literal(line) }, false)
        }
        CatchRateMod.logEnvironmentInfo()
        return 1
    }
}
