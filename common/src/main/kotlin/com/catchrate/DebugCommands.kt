package com.catchrate

import com.catchrate.config.CatchRateConfig
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * Client-side commands for CatchRateDisplay.
 * /catchrate debug - toggles debug logging for this session
 * /catchrate info  - show version + config state
 * /catchrate log   - save debug report to local file
 */
object DebugCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        register(dispatcher, Commands::literal) { source, message ->
            source.sendSuccess({ message }, false)
        }
    }

    fun <S> register(
        dispatcher: CommandDispatcher<S>,
        literal: (String) -> LiteralArgumentBuilder<S>,
        feedback: (S, Component) -> Unit
    ) {
        dispatcher.register(buildRootCommand(literal, feedback))
    }

    private fun <S> buildRootCommand(
        literal: (String) -> LiteralArgumentBuilder<S>,
        feedback: (S, Component) -> Unit
    ): LiteralArgumentBuilder<S> {
        return literal("catchrate")
            .then(literal("debug")
                .executes { ctx -> toggleDebug(ctx.source, feedback) }
            )
            .then(literal("info")
                .executes { ctx -> showInfo(ctx.source, feedback) }
            )
            .then(literal("log")
                .executes { ctx -> uploadLog(ctx.source, feedback) }
            )
            .then(literal("export-rates")
                .executes { ctx -> exportRates(ctx.source, feedback) }
            )
    }

    private fun <S> toggleDebug(source: S, feedback: (S, Component) -> Unit): Int {
        val newState = CatchRateMod.toggleSessionDebug()
        val message = if (newState) {
            Component.translatable("catchrate.command.debug.enabled")
        } else {
            Component.translatable("catchrate.command.debug.disabled")
        }
        feedback(source, message)
        return 1
    }

    private fun <S> showInfo(source: S, feedback: (S, Component) -> Unit): Int {
        val config = CatchRateConfig.get()

        feedback(source, Component.translatable("catchrate.command.info.header"))
        feedback(source, Component.translatable("catchrate.command.info.version", CatchRateMod.VERSION))
        feedback(source, Component.translatable("catchrate.command.info.debug_config", CatchRateMod.DEBUG_ENABLED.toString()))
        feedback(source, Component.translatable("catchrate.command.info.debug_session", (CatchRateMod.sessionDebugOverride?.toString() ?: "not set")))
        feedback(source, Component.translatable("catchrate.command.info.debug_active", CatchRateMod.isDebugActive.toString()))
        feedback(source, Component.translatable("catchrate.command.info.hud_enabled", config.hudEnabled.toString()))
        feedback(source, Component.translatable("catchrate.command.info.show_ooc", config.showOutOfCombat.toString()))
        feedback(source, Component.translatable("catchrate.command.info.hide_unencountered", config.hideUnencounteredInfo.toString()))
        feedback(source, Component.translatable("catchrate.command.info.footer"))

        CatchRateMod.logEnvironmentInfo()
        return 1
    }

    private fun <S> uploadLog(source: S, feedback: (S, Component) -> Unit): Int {
        feedback(source, Component.translatable("catchrate.command.log.uploading"))

        val localPath = try { CatchRateDebugLog.saveToFile() } catch (_: Throwable) { null }
        if (localPath != null) {
            feedback(source, Component.translatable("catchrate.command.log.saved_locally", localPath))
        } else {
            feedback(source, Component.translatable("catchrate.command.log.failed", "Could not save file"))
        }
        return 1
    }

    private fun <S> exportRates(source: S, feedback: (S, Component) -> Unit): Int {
        feedback(source, Component.literal("§e[CatchRate] Exporting catch rate audit..."))

        val localPath = try { CatchRateAuditExport.saveCatchRateAudit() } catch (_: Throwable) { null }
        if (localPath != null) {
            feedback(source, Component.literal("§a[CatchRate] Catch rate audit saved to: §f$localPath"))
        } else {
            feedback(source, Component.literal("§c[CatchRate] Failed to save catch rate audit."))
        }
        return 1
    }
}
