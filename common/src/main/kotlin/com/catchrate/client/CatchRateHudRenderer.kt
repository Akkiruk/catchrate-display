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

/**
 * Translation key helper object for HUD strings.
 */
object HudTranslations {
    fun header() = Component.translatable("catchrate.hud.header").string
    fun guaranteed() = Component.translatable("catchrate.hud.guaranteed").string
    fun guaranteedShort() = Component.translatable("catchrate.hud.guaranteed_short").string
    fun hp() = Component.translatable("catchrate.hud.hp").string
    fun wild() = Component.translatable("catchrate.hud.wild").string
    fun unknownPercent() = Component.translatable("catchrate.hud.unknown_percent").string
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
    private var lastActionRequestCount = 0
    
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
    
    companion object {
        private const val CLIENT_CALC_INTERVAL_TICKS = 5L
        private const val COMPARISON_CALC_INTERVAL_TICKS = 10L
    }
    
    /**
     * Main render method. Called by platform-specific HUD render events.
     */
    fun render(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
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
            resetState()
            if (config.showOutOfCombat) {
                renderOutOfCombatHud(guiGraphics, minecraft, player)
            }
            return
        }
        
        if (battle.battleId != lastBattleId) {
            CatchRateMod.debug("HUD", "Battle started: ${battle.battleId} isPvW=${battle.isPvW}")
            onBattleStart(battle)
        }
        if (!battle.isPvW) {
            CatchRateMod.debugOnChange("battleType", "pvp", "Not PvW battle, HUD hidden")
            return
        }
        
        updateTurnCount(battle)
        
        val heldItem = player.mainHandItem
        if (!isPokeball(heldItem)) {
            CatchRateMod.debugOnChange("heldItem", "not_pokeball", "Not holding Pokeball: ${heldItem.item}")
            return
        }
        
        val opponentPokemon = getOpponentPokemon(battle)
        if (opponentPokemon == null) {
            CatchRateMod.debugOnChange("opponent", "none", "No opponent Pokemon found")
            return
        }
        
        checkCacheInvalidation(opponentPokemon, heldItem)
        
        val ballName = getBallId(heldItem).lowercase()
        CatchRateMod.debugOnChange("target", "${opponentPokemon.species.name}_${ballName}", 
            "Target: ${opponentPokemon.species.name} Lv${opponentPokemon.level} with $ballName")
        
        if (config.showBallComparison) {
            renderBallComparisonPanel(guiGraphics, minecraft, opponentPokemon, battle)
            return
        }
        
        val result = getClientCalculation(opponentPokemon, heldItem)
        renderClientModeHud(guiGraphics, minecraft, result, ballName)
    }
    
    private fun onBattleStart(battle: ClientBattle) {
        lastBattleId = battle.battleId
        turnCount = 1
        lastActionRequestCount = 0
        cachedClientResult = null
        cachedComparison = null
    }
    
    private fun updateTurnCount(battle: ClientBattle) {
        val currentRequestCount = battle.pendingActionRequests.size
        if (currentRequestCount > 0 && currentRequestCount != lastActionRequestCount) {
            if (lastActionRequestCount > 0) {
                turnCount++
                cachedClientResult = null
                cachedComparison = null
            }
            lastActionRequestCount = currentRequestCount
        }
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
        lastBattleId = null
        lastPokemonUuid = null
        lastBallItem = null
        lastHpValue = -1F
        lastStatusName = null
        cachedClientResult = null
        cachedComparison = null
    }
    
    private fun getClientCalculation(pokemon: ClientBattlePokemon, heldItem: ItemStack): CatchRateResult {
        if (cachedClientResult == null || (tickCounter - lastClientCalcTick) > CLIENT_CALC_INTERVAL_TICKS) {
            cachedClientResult = CatchRateCalculator.calculateCatchRate(pokemon, heldItem, turnCount, null, true)
            lastClientCalcTick = tickCounter
        }
        return cachedClientResult!!
    }
    
    
    private fun renderClientModeHud(guiGraphics: GuiGraphics, minecraft: Minecraft, result: CatchRateResult, ballName: String) {
        val config = CatchRateConfig.get()
        val font = minecraft.font
        val screenWidth = minecraft.window.guiScaledWidth
        val screenHeight = minecraft.window.guiScaledHeight
        
        val headerText = HudTranslations.header()
        val percentText = if (result.isGuaranteed) {
            HudTranslations.guaranteedShort()
        } else {
            "${CatchRateFormula.formatCatchPercentage(result.percentage, result.isGuaranteed)}%"
        }
        val statusIcon = HudDrawing.getStatusIcon(result.statusName)
        val statusText = "$statusIcon ${HudTranslations.status(result.statusName)}"
        val ballDisplay = CatchRateFormula.formatBallNameCompact(result.ballName)
        val turnInfo = if (ballName == "timer_ball" || ballName == "quick_ball") " T$turnCount" else ""
        val ballIcon = if (result.ballConditionMet) "●" else "○"
        val ballText = "$ballIcon $ballDisplay ${String.format("%.1f", result.ballMultiplier)}x$turnInfo"
        
        val hasStatus = result.statusMultiplier > 1.0
        val hasConditionDesc = result.ballConditionReason.isNotBlank()
        
        val textWidths = mutableListOf(
            font.width(headerText),
            font.width(percentText),
            font.width(ballText),
            88
        )
        if (hasStatus) textWidths.add(font.width(statusText))
        if (hasConditionDesc) textWidths.add(font.width(result.ballConditionReason))
        
        val boxWidth = (textWidths.maxOrNull() ?: 100) + 16
        // Dynamic height: header section (header + bar + percent + HP bar) = 52px base, then 10px per detail row, 6px bottom
        var clientDetailRows = 1 // ball (always present)
        if (hasStatus) clientDetailRows++
        if (hasConditionDesc) clientDetailRows++
        val boxHeight = 52 + clientDetailRows * 10 + 6
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        HudDrawing.drawStyledPanel(guiGraphics, x, y, boxWidth, boxHeight, result.percentage)
        
        guiGraphics.drawString(font, HudTranslations.header(), x + 6, y + 4, Colors.TEXT_WHITE)
        
        val barY = y + 16
        if (result.isGuaranteed) {
            HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, 100.0, true)
            guiGraphics.drawString(font, percentText, x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, result.percentage, false)
            val percentColor = HudDrawing.getChanceColorInt(result.percentage)
            guiGraphics.drawString(font, percentText, x + 6, barY + 12, percentColor)
        }
        
        val hpY = barY + 24
        guiGraphics.drawString(font, HudTranslations.hp(), x + 6, hpY, Colors.TEXT_GRAY)
        HudDrawing.drawHealthBar(guiGraphics, x + 22, hpY + 1, 60, (result.hpPercentage / 100.0).toFloat())
        
        val infoY = hpY + 12
        var currentY = infoY
        if (hasStatus) {
            guiGraphics.drawString(font, statusText, x + 6, currentY, Colors.TEXT_PURPLE)
            currentY += 10
        }
        
        val ballColor = HudDrawing.getBallMultiplierColor(result.ballMultiplier)
        guiGraphics.drawString(font, ballText, x + 6, currentY, ballColor)
        
        if (hasConditionDesc) {
            currentY += 10
            val conditionColor = if (result.ballConditionMet) Colors.TEXT_DARK_GREEN else Colors.TEXT_DARK_GRAY
            guiGraphics.drawString(font, result.ballConditionReason, x + 6, currentY, conditionColor)
        }
    }
    
    private fun renderOutOfCombatHud(guiGraphics: GuiGraphics, minecraft: Minecraft, player: LocalPlayer) {
        val heldItem = player.mainHandItem
        if (!isPokeball(heldItem)) return
        
        val pokemonEntity = BallComparisonCalculator.getLookedAtPokemon() ?: return
        val pokemon = pokemonEntity.pokemon
        
        val ballName = getBallId(heldItem).lowercase()
        
        renderOutOfCombatClientHud(guiGraphics, minecraft, pokemonEntity, ballName)
    }
    
    /**
     * Render out-of-combat HUD using client-side data (fallback when server mod not installed).
     */
    private fun renderOutOfCombatClientHud(guiGraphics: GuiGraphics, minecraft: Minecraft, pokemonEntity: com.cobblemon.mod.common.entity.pokemon.PokemonEntity, ballName: String) {
        val pokemon = pokemonEntity.pokemon
        
        val result = BallComparisonCalculator.calculateForWorldPokemon(pokemonEntity, ballName) ?: return
        
        val config = CatchRateConfig.get()
        val font = minecraft.font
        val screenWidth = minecraft.window.guiScaledWidth
        val screenHeight = minecraft.window.guiScaledHeight
        
        val hpPercent = if (pokemon.maxHealth > 0) (pokemon.currentHealth.toDouble() / pokemon.maxHealth.toDouble()) * 100.0 else 100.0
        val hpMultiplier = (3.0 - 2.0 * hpPercent / 100.0) / 3.0
        val hpText = "${HudTranslations.hp()} ${String.format("%.2f", hpMultiplier)}x"
        
        val statusName = pokemon.status?.status?.name?.path ?: ""
        val statusMult = CatchRateFormula.getStatusMultiplier(statusName)
        val hasStatus = statusMult > 1.0
        val statusIcon = HudDrawing.getStatusIcon(statusName)
        val statusText = "$statusIcon ${HudTranslations.status(statusName).uppercase()} ${String.format("%.1f", statusMult)}x"
        
        val ballIcon = if (result.conditionMet) "●" else "○"
        val ballText = "$ballIcon ${CatchRateFormula.formatBallNameCompact(result.ballName)} ${String.format("%.1f", result.multiplier)}x"
        
        val penaltyText = HudTranslations.outOfCombatPenalty()
        val nameText = "${pokemon.species.name} Lv${pokemon.level}"
        
        val percentText = if (result.isGuaranteed) {
            HudTranslations.guaranteed()
        } else {
            "${CatchRateFormula.formatCatchPercentage(result.catchRate, result.isGuaranteed)}%"
        }
        
        val wildText = HudTranslations.wild()
        val hasConditionDesc = result.reason.isNotBlank()
        val textWidths = mutableListOf(
            font.width(nameText) + font.width(" $wildText") + 8,
            font.width(hpText),
            font.width(ballText),
            font.width(penaltyText),
            font.width(percentText)
        )
        if (hasConditionDesc) textWidths.add(font.width(result.reason))
        if (hasStatus) textWidths.add(font.width(statusText))
        
        val boxWidth = (textWidths.maxOrNull() ?: 100) + 16
        // Dynamic height: header section (name + bar + percent) = 42px base, then 10px per detail row
        var oocDetailRows = 3 // HP + ball + penalty (always present)
        if (hasStatus) oocDetailRows++
        if (hasConditionDesc) oocDetailRows++
        val boxHeight = 42 + oocDetailRows * 10
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        HudDrawing.drawStyledPanel(guiGraphics, x, y, boxWidth, boxHeight, result.catchRate, isWild = true)
        
        guiGraphics.drawString(font, nameText, x + 6, y + 4, Colors.TEXT_WHITE)
        guiGraphics.drawString(font, wildText, x + boxWidth - font.width(wildText) - 6, y + 4, Colors.TEXT_WILD_RED)
        
        val barY = y + 16
        if (result.isGuaranteed) {
            HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, 100.0, true)
            guiGraphics.drawString(font, HudTranslations.guaranteed(), x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, result.catchRate, false)
            val pctText = "${CatchRateFormula.formatCatchPercentage(result.catchRate, result.isGuaranteed)}%"
            val percentColor = HudDrawing.getChanceColorInt(result.catchRate)
            guiGraphics.drawString(font, pctText, x + 6, barY + 12, percentColor)
        }
        
        var currentY = barY + 26
        guiGraphics.drawString(font, hpText, x + 6, currentY, Colors.TEXT_GRAY)
        
        if (hasStatus) {
            currentY += 10
            guiGraphics.drawString(font, statusText, x + 6, currentY, Colors.TEXT_PURPLE)
        }
        
        currentY += 10
        val ballColor = HudDrawing.getBallMultiplierColor(result.multiplier.toFloat())
        guiGraphics.drawString(font, ballText, x + 6, currentY, ballColor)
        
        if (hasConditionDesc) {
            currentY += 10
            val conditionColor = if (result.conditionMet) Colors.TEXT_DARK_GREEN else Colors.TEXT_DARK_GRAY
            guiGraphics.drawString(font, result.reason, x + 6, currentY, conditionColor)
        }
        
        currentY += 10
        guiGraphics.drawString(font, penaltyText, x + 6, currentY, Colors.TEXT_ORANGE)
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
        val boxWidth = col1Width + col2Width + col3Width + padding * 4
        val footerHeight = 18
        val boxHeight = headerHeight + lines.size * lineHeight + footerHeight + padding * 2
        
        val x = (screenWidth - boxWidth) / 2
        val y = (screenHeight - boxHeight) / 2
        
        HudDrawing.drawComparisonPanel(guiGraphics, x, y, boxWidth, boxHeight, headerHeight)
        
        val header = Component.literal(HudTranslations.ballComparison(turnCount)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
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
        val keyName = CatchRateKeybinds.comparisonKeyName
        guiGraphics.drawString(font, HudTranslations.releaseToClose(keyName), x + padding, footerY, Colors.TEXT_DARK_GRAY)
    }
    
    // ==================== HELPER METHODS ====================
    
    private fun getOpponentPokemon(battle: ClientBattle): ClientBattlePokemon? {
        return try { battle.side2.activeClientBattlePokemon.firstOrNull()?.battlePokemon } catch (e: Exception) { null }
    }
    
    private fun isPokeball(itemStack: ItemStack): Boolean {
        return CatchRateCalculator.getPokeBallFromItem(itemStack) != null
    }
    
    private fun getBallId(itemStack: ItemStack): String {
        return CatchRateCalculator.getPokeBallFromItem(itemStack)?.name?.path ?: ""
    }
}
