package com.catchrate

import com.catchrate.config.CatchRateConfig
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * Client-side commands for CatchRateDisplay.
 * /catchrate debug - toggles debug logging for this session
 * /catchrate info  - show version + config state
 * /catchrate log   - upload debug report to mclo.gs
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
                .then(Commands.literal("log")
                    .executes { ctx -> uploadLog(ctx) }
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
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.hide_unencountered", config.hideUnencounteredInfo.toString()) }, false)
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.info.footer") }, false)
        
        CatchRateMod.logEnvironmentInfo()
        return 1
    }
    
    private fun uploadLog(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.sendSuccess({ Component.translatable("catchrate.command.log.uploading") }, false)
        
        // Also save locally as fallback
        val localPath = try { CatchRateDebugLog.saveToFile() } catch (_: Throwable) { null }
        
        CatchRateDebugLog.uploadToMcloGs { success, urlOrError ->
            val minecraft = net.minecraft.client.Minecraft.getInstance()
            minecraft.execute {
                val player = minecraft.player ?: return@execute
                if (success) {
                    player.sendSystemMessage(Component.translatable("catchrate.command.log.success"))
                    player.sendSystemMessage(
                        Component.literal("\u00a7b\u00a7n$urlOrError")
                            .withStyle(Style.EMPTY.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, urlOrError)))
                    )
                    player.sendSystemMessage(Component.translatable("catchrate.command.log.instructions"))
                } else {
                    player.sendSystemMessage(Component.translatable("catchrate.command.log.failed", urlOrError))
                    if (localPath != null) {
                        player.sendSystemMessage(Component.translatable("catchrate.command.log.saved_locally", localPath))
                    }
                }
            }
        }
        return 1
    }
}
