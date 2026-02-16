package com.catchrate.client

import com.catchrate.CatchRateCalculator
import com.catchrate.CatchRateConstants.Colors
import com.catchrate.CatchRateFormula
import com.catchrate.CatchRateKeybinds
import com.catchrate.CatchRateMod
import com.catchrate.CatchRateResult
import com.catchrate.config.CatchRateConfig
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.DeltaTracker
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import org.lwjgl.glfw.GLFW

/**
 * Translation key helper object for HUD strings.
 */
object HudTranslations {
    fun header() = Component.translatable("catchrate.hud.header").string
    fun guaranteed() = Component.translatable("catchrate.hud.guaranteed").string
    fun guaranteedShort() = Component.translatable("catchrate.hud.guaranteed_short").string
    fun hp() = Component.translatable("catchrate.hud.hp").string
    fun wild() = Component.translatable("catchrate.hud.wild").string
    fun outOfCombatPenalty() = Component.translatable("catchrate.hud.out_of_combat_penalty").string
    fun releaseToClose(key: String) = Component.translatable("catchrate.hud.release_to_close", key).string
    fun ballComparison(turn: Int) = Component.translatable("catchrate.hud.ball_comparison", turn).string
    fun status(statusPath: String?) = Component.translatable(CatchRateFormula.getStatusTranslationKey(statusPath)).string
}

/**
 * HUD renderer for catch rate display.
 * Platform entrypoints should call render() from their HUD render events.
 */
class CatchRateHudRenderer {
    
    private var lastBattleId: java.util.UUID? = null
    private var turnCount = 1
    private var lastMustChoose = false
    private var mustChooseTransitions = 0
    
    private var lastPokemonUuid: java.util.UUID? = null
    private var lastBallItem: String? = null
    private var lastHpValue: Float = -1F
    private var lastStatusName: String? = null
    
    private var cachedClientResult: CatchRateResult? = null
    private var lastClientCalcTick = 0L
    
    private var cachedComparison: List<BallComparisonCalculator.BallCatchRate>? = null
    private var lastComparisonTick = 0L
    private var lastComparisonTurnCount = 0
    
    private var tickCounter = 0L
    private var cursorHidden = false
    private var previousCursorMode = -1
    
    companion object {
        private const val CLIENT_CALC_INTERVAL_TICKS = 5L
        private const val COMPARISON_CALC_INTERVAL_TICKS = 10L
    }
    
    /**
     * Main render method. Called by platform-specific HUD render events.
     */
    fun render(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        try {
            renderInternal(guiGraphics, deltaTracker)
        } catch (e: Throwable) {
            CatchRateMod.debugOnChange("HudErr", "render", "Render error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    private fun renderInternal(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val config = CatchRateConfig.get()
        
        if (!config.hudEnabled) return
        
        // Respect F1 to hide HUD
        if (minecraft.options.hideGui) return
        
        this.tickCounter++
        
        val battle = CobblemonClient.battle
        
        // Handle out-of-combat display
        if (battle == null) {
            showCursor()
            resetState()
            if (config.showOutOfCombat) {
                try {
                    renderOutOfCombatHud(guiGraphics, minecraft, player)
                } catch (e: Throwable) {
                    CatchRateMod.debugOnChange("HudErr", "ooc", "Out-of-combat render failed: ${e.javaClass.simpleName}")
                }
            }
            return
        }
        
        if (battle.battleId != lastBattleId) {
            CatchRateMod.debug("HUD", "Battle started: ${battle.battleId} isPvW=${battle.isPvW}")
            onBattleStart(battle)
        }
        if (!battle.isPvW) {
            showCursor()
            CatchRateMod.debugOnChange("battleType", "pvp", "Not PvW battle, HUD hidden")
            return
        }
        
        updateTurnCount(battle)
        
        val heldItem = player.mainHandItem
        if (!isPokeball(heldItem)) {
            showCursor()
            CatchRateMod.debugOnChange("heldItem", "not_pokeball", "Not holding Pokeball: ${heldItem.item}")
            return
        }
        
        val opponentPokemon = getOpponentPokemon(battle)
        if (opponentPokemon == null) {
            showCursor()
            CatchRateMod.debugOnChange("opponent", "none", "No opponent Pokemon found")
            return
        }
        
        checkCacheInvalidation(opponentPokemon, heldItem)
        
        val ballName = getBallId(heldItem).lowercase()
        CatchRateMod.debugOnChange("target", "${opponentPokemon.species.name}_${ballName}", 
            "Target: ${opponentPokemon.species.name} Lv${opponentPokemon.level} with $ballName")
        
        if (config.showBallComparison) {
            try {
                hideCursor()
                renderBallComparisonPanel(guiGraphics, minecraft, opponentPokemon, battle)
                return
            } catch (e: Throwable) {
                showCursor()
                CatchRateMod.debugOnChange("HudErr", "comparison", "Comparison panel failed, falling back: ${e.javaClass.simpleName}")
                // Fall through to single-ball HUD
            }
        }
        
        showCursor()
        val result = getClientCalculation(opponentPokemon, heldItem) ?: return
        renderClientModeHud(guiGraphics, minecraft, result, ballName)
    }
    
    private fun onBattleStart(battle: ClientBattle) {
        lastBattleId = battle.battleId
        turnCount = 1
        lastMustChoose = false
        mustChooseTransitions = 0
        cachedClientResult = null
        cachedComparison = null
    }
    
    private fun updateTurnCount(battle: ClientBattle) {
        val nowMustChoose = battle.mustChoose
        if (nowMustChoose && !lastMustChoose) {
            mustChooseTransitions++
            if (mustChooseTransitions > 1) {
                turnCount++
                cachedClientResult = null
                cachedComparison = null
            }
        }
        lastMustChoose = nowMustChoose
    }
    
    private fun checkCacheInvalidation(pokemon: ClientBattlePokemon, heldItem: ItemStack) {
        val ballId = getBallId(heldItem)
        val statusName = pokemon.status?.name?.path
        var needsInvalidation = false
        
        if (pokemon.uuid != lastPokemonUuid) { needsInvalidation = true; lastPokemonUuid = pokemon.uuid }
        if (ballId != lastBallItem) { needsInvalidation = true; lastBallItem = ballId }
        if (pokemon.hpValue != lastHpValue) { needsInvalidation = true; lastHpValue = pokemon.hpValue }
        if (statusName != lastStatusName) { needsInvalidation = true; lastStatusName = statusName }
        
        if (needsInvalidation) {
            cachedClientResult = null
            cachedComparison = null
        }
    }
    
    private fun resetState() {
        showCursor()
        lastBattleId = null
        turnCount = 1
        lastMustChoose = false
        mustChooseTransitions = 0
        lastPokemonUuid = null
        lastBallItem = null
        lastHpValue = -1F
        lastStatusName = null
        cachedClientResult = null
        cachedComparison = null
    }
    
    private fun getClientCalculation(pokemon: ClientBattlePokemon, heldItem: ItemStack): CatchRateResult? {
        if (cachedClientResult == null || (tickCounter - lastClientCalcTick) > CLIENT_CALC_INTERVAL_TICKS) {
            cachedClientResult = try {
                CatchRateCalculator.calculateCatchRate(pokemon, heldItem, turnCount, null, true)
            } catch (e: Throwable) {
                CatchRateMod.debugOnChange("HudErr", "calc", "Calculation failed: ${e.message}")
                null
            }
            lastClientCalcTick = tickCounter
        }
        return cachedClientResult
    }
    
    
    /**
     * Common data for the unified HUD, built from either in-battle or out-of-combat sources.
     */
    private data class HudData(
        val pokemonName: String,
        val level: Int,
        val catchPercentage: Double,
        val isGuaranteed: Boolean,
        val hpMultiplier: Double,
        val statusName: String,
        val statusMultiplier: Double,
        val ballDisplayName: String,
        val ballId: String,
        val ballMultiplier: Double,
        val ballConditionMet: Boolean,
        val ballConditionReason: String,
        val turnCount: Int,
        val isWild: Boolean
    )
    
    private fun renderClientModeHud(guiGraphics: GuiGraphics, minecraft: Minecraft, result: CatchRateResult, ballName: String) {
        val opponent = cachedClientResult?.let {
            CobblemonClient.battle?.side2?.activeClientBattlePokemon?.firstOrNull()?.battlePokemon
        }
        val species = opponent?.species
        val hpMult = (3.0 - 2.0 * result.hpPercentage / 100.0) / 3.0
        renderUnifiedHud(guiGraphics, minecraft, HudData(
            pokemonName = species?.name ?: "???",
            level = opponent?.level ?: 0,
            catchPercentage = result.percentage,
            isGuaranteed = result.isGuaranteed,
            hpMultiplier = hpMult,
            statusName = result.statusName,
            statusMultiplier = result.statusMultiplier,
            ballDisplayName = CatchRateFormula.formatBallNameCompact(result.ballName),
            ballId = ballName,
            ballMultiplier = result.ballMultiplier,
            ballConditionMet = result.ballConditionMet,
            ballConditionReason = result.ballConditionReason,
            turnCount = turnCount,
            isWild = false
        ))
    }
    
    private fun renderOutOfCombatHud(guiGraphics: GuiGraphics, minecraft: Minecraft, player: LocalPlayer) {
        val heldItem = player.mainHandItem
        if (!isPokeball(heldItem)) return
        
        val pokemonEntity = BallComparisonCalculator.getLookedAtPokemon() ?: return
        val pokemon = pokemonEntity.pokemon
        val ballName = getBallId(heldItem).lowercase()
        val result = BallComparisonCalculator.calculateForWorldPokemon(pokemonEntity, ballName) ?: return
        
        val hpPercent = if (pokemon.maxHealth > 0) (pokemon.currentHealth.toDouble() / pokemon.maxHealth.toDouble()) * 100.0 else 100.0
        val statusPath = pokemon.status?.status?.name?.path ?: ""
        
        renderUnifiedHud(guiGraphics, minecraft, HudData(
            pokemonName = pokemon.species.name,
            level = pokemon.level,
            catchPercentage = result.catchRate,
            isGuaranteed = result.isGuaranteed,
            hpMultiplier = (3.0 - 2.0 * hpPercent / 100.0) / 3.0,
            statusName = statusPath,
            statusMultiplier = CatchRateFormula.getStatusMultiplier(statusPath).toDouble(),
            ballDisplayName = CatchRateFormula.formatBallNameCompact(result.ballName),
            ballId = ballName,
            ballMultiplier = result.multiplier,
            ballConditionMet = result.conditionMet,
            ballConditionReason = result.reason,
            turnCount = 0,
            isWild = true
        ))
    }
    
    /**
     * Single unified HUD renderer used for both in-battle and out-of-combat display.
     */
    private fun renderUnifiedHud(guiGraphics: GuiGraphics, minecraft: Minecraft, data: HudData) {
        val config = CatchRateConfig.get()
        val font = minecraft.font
        val screenWidth = minecraft.window.guiScaledWidth
        val screenHeight = minecraft.window.guiScaledHeight
        
        val nameText = "${data.pokemonName} Lv${data.level}"
        val wildText = if (data.isWild) HudTranslations.wild() else null
        val hpText = "${HudTranslations.hp()} ${String.format("%.2f", data.hpMultiplier)}x"
        
        val percentText = if (data.isGuaranteed) {
            HudTranslations.guaranteedShort()
        } else {
            "${CatchRateFormula.formatCatchPercentage(data.catchPercentage, data.isGuaranteed)}%"
        }
        
        val hasStatus = data.statusMultiplier > 1.0
        val statusIcon = HudDrawing.getStatusIcon(data.statusName)
        val statusText = "$statusIcon ${HudTranslations.status(data.statusName)} ${String.format("%.1f", data.statusMultiplier)}x"
        
        val turnInfo = if (data.ballId == "timer_ball" || data.ballId == "quick_ball") " T${data.turnCount}" else ""
        val ballIcon = if (data.ballConditionMet) "●" else "○"
        val ballText = "$ballIcon ${data.ballDisplayName} ${String.format("%.1f", data.ballMultiplier)}x$turnInfo"
        
        val hasConditionDesc = data.ballConditionReason.isNotBlank()
        val penaltyText = if (data.isWild) HudTranslations.outOfCombatPenalty() else null
        
        val textWidths = mutableListOf(
            font.width(nameText) + (if (wildText != null) font.width(" $wildText") + 8 else 0),
            font.width(percentText),
            font.width(hpText),
            font.width(ballText)
        )
        if (hasStatus) textWidths.add(font.width(statusText))
        if (hasConditionDesc) textWidths.add(font.width(data.ballConditionReason))
        if (penaltyText != null) textWidths.add(font.width(penaltyText))
        
        val boxWidth = (textWidths.maxOrNull() ?: 100) + 16
        
        // Layout: name(10) + 2 + bar(8) + 2 + percent(10) + 2 = 34 top section + detail rows * 10 + 6 bottom pad
        var detailRows = 2 // HP + ball always present
        if (hasStatus) detailRows++
        if (hasConditionDesc) detailRows++
        if (penaltyText != null) detailRows++
        val boxHeight = 40 + detailRows * 10 + 6
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        HudDrawing.drawStyledPanel(guiGraphics, x, y, boxWidth, boxHeight, data.catchPercentage, isWild = data.isWild)
        
        // Header: Pokemon name + level, optional WILD tag
        guiGraphics.drawString(font, nameText, x + 6, y + 4, Colors.TEXT_WHITE)
        if (wildText != null) {
            guiGraphics.drawString(font, wildText, x + boxWidth - font.width(wildText) - 6, y + 4, Colors.TEXT_WILD_RED)
        }
        
        // Catch bar + percentage
        val barY = y + 16
        if (data.isGuaranteed) {
            HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, 100.0, true)
            guiGraphics.drawString(font, percentText, x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, data.catchPercentage, false)
            guiGraphics.drawString(font, percentText, x + 6, barY + 12, HudDrawing.getChanceColorInt(data.catchPercentage))
        }
        
        // Detail rows
        var currentY = barY + 26
        
        val hpColor = HudDrawing.getHpMultiplierColor(data.hpMultiplier)
        guiGraphics.drawString(font, hpText, x + 6, currentY, hpColor)
        currentY += 10
        
        if (hasStatus) {
            guiGraphics.drawString(font, statusText, x + 6, currentY, Colors.TEXT_PURPLE)
            currentY += 10
        }
        
        guiGraphics.drawString(font, ballText, x + 6, currentY, HudDrawing.getBallMultiplierColor(data.ballMultiplier))
        
        if (hasConditionDesc) {
            currentY += 10
            val conditionColor = if (data.ballConditionMet) Colors.TEXT_DARK_GREEN else Colors.TEXT_DARK_GRAY
            guiGraphics.drawString(font, data.ballConditionReason, x + 6, currentY, conditionColor)
        }
        
        if (penaltyText != null) {
            currentY += 10
            guiGraphics.drawString(font, penaltyText, x + 6, currentY, Colors.TEXT_ORANGE)
        }
    }
    
    private fun renderBallComparisonPanel(guiGraphics: GuiGraphics, minecraft: Minecraft, pokemon: ClientBattlePokemon, battle: ClientBattle) {
        val turnChanged = turnCount != lastComparisonTurnCount
        if (cachedComparison == null || turnChanged || (tickCounter - lastComparisonTick) > COMPARISON_CALC_INTERVAL_TICKS) {
            cachedComparison = BallComparisonCalculator.calculateAllBalls(pokemon, turnCount, battle)
            lastComparisonTick = tickCounter
            lastComparisonTurnCount = turnCount
        }
        
        val comparison = cachedComparison ?: return
        val font = minecraft.font
        val screenWidth = minecraft.window.guiScaledWidth
        val screenHeight = minecraft.window.guiScaledHeight
        
        val lineHeight = 10
        val padding = 6
        val headerHeight = 20
        
        val lines = mutableListOf<Triple<Component, Component, Component>>()
        
        for ((index, ball) in comparison.take(12).withIndex()) {
            val medal = when (index) {
                0 -> "§6★ "
                1 -> "§f◆ "
                2 -> "§c◆ "
                else -> "   "
            }
            
            val ballText = Component.literal("$medal${ball.displayName}")
            val rateColor = HudDrawing.getChanceFormatting(ball.catchRate)
            val rateText = Component.literal("${CatchRateFormula.formatCatchPercentage(ball.catchRate, ball.isGuaranteed)}%").withStyle(rateColor)
            
            val multColor = HudDrawing.getBallMultiplierFormatting(ball.multiplier)
            val multText = Component.literal("${String.format("%.1f", ball.multiplier)}x").withStyle(multColor)
            
            lines.add(Triple(ballText, rateText, multText))
        }
        
        val col1Width = lines.maxOfOrNull { font.width(it.first) } ?: 100
        val col2Width = 45
        val col3Width = 35
        val contentWidth = col1Width + col2Width + col3Width + padding * 4
        val keyName = CatchRateKeybinds.comparisonKeyName
        val header = Component.literal(HudTranslations.ballComparison(turnCount)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        val headerWidth = font.width(header) + padding * 2
        val footerText = HudTranslations.releaseToClose("[${keyName}]")
        val footerWidth = font.width(footerText) + padding * 2
        val boxWidth = maxOf(contentWidth, headerWidth, footerWidth)
        val footerHeight = 18
        val boxHeight = headerHeight + lines.size * lineHeight + footerHeight + padding * 2
        
        val x = (screenWidth - boxWidth) / 2
        val y = (screenHeight - boxHeight) / 2
        
        HudDrawing.drawComparisonPanel(guiGraphics, x, y, boxWidth, boxHeight, headerHeight)
        
        guiGraphics.drawString(font, header, x + padding, y + padding, 0xFFFFFF)
        
        guiGraphics.hLine(x + padding, x + boxWidth - padding, y + headerHeight, 0xFF444455.toInt())
        
        var lineY = y + headerHeight + padding
        for ((index, triple) in lines.withIndex()) {
            val (ballText, rateText, multText) = triple
            
            if (index % 2 == 0) {
                guiGraphics.fill(x + 2, lineY - 1, x + boxWidth - 2, lineY + 9, 0x15FFFFFF)
            }
            
            guiGraphics.drawString(font, ballText, x + padding, lineY, 0xFFFFFF)
            guiGraphics.drawString(font, rateText, x + col1Width + padding * 2, lineY, 0xFFFFFF)
            guiGraphics.drawString(font, multText, x + col1Width + col2Width + padding * 3, lineY, 0xFFFFFF)
            lineY += lineHeight
        }
        
        val footerY = y + boxHeight - padding - 9
        guiGraphics.hLine(x + padding, x + boxWidth - padding, footerY - 4, Colors.BAR_BORDER)
        guiGraphics.drawString(font, footerText, x + padding, footerY, Colors.TEXT_DARK_GRAY)
    }
    
    // ==================== HELPER METHODS ====================
    
    private fun getOpponentPokemon(battle: ClientBattle): ClientBattlePokemon? {
        return try { battle.side2.activeClientBattlePokemon.firstOrNull()?.battlePokemon } catch (e: Throwable) { null }
    }
    
    private fun isPokeball(itemStack: ItemStack): Boolean {
        return CatchRateCalculator.getPokeBallFromItem(itemStack) != null
    }
    
    private fun getBallId(itemStack: ItemStack): String {
        return CatchRateCalculator.getPokeBallFromItem(itemStack)?.name?.path ?: ""
    }
    
    private fun hideCursor() {
        if (!cursorHidden) {
            val minecraft = Minecraft.getInstance()
            val window = minecraft.window.window
            previousCursorMode = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR)
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN)
            cursorHidden = true
        }
    }
    
    private fun showCursor() {
        if (cursorHidden) {
            val minecraft = Minecraft.getInstance()
            val window = minecraft.window.window
            val restoreMode = if (previousCursorMode >= 0) previousCursorMode else GLFW.GLFW_CURSOR_NORMAL
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, restoreMode)
            cursorHidden = false
            previousCursorMode = -1
        }
    }
}
