package com.catchrate.client

import com.catchrate.CatchRateCalculator
import com.catchrate.CatchRateConstants.Colors
import com.catchrate.CatchRateFormula
import com.catchrate.CatchRateResult
import com.catchrate.config.CatchRateConfig
import com.catchrate.network.CatchRateClientNetworking
import com.catchrate.network.CatchRateResponsePayload
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class CatchRateHudRenderer : HudRenderCallback {
    
    private var lastBattleId: java.util.UUID? = null
    private var turnCount = 1
    private var lastActionRequestCount = 0
    
    private var lastPokemonUuid: java.util.UUID? = null
    private var lastBallItem: String? = null
    private var lastHpValue: Float = -1F
    private var lastStatusName: String? = null
    
    private var cachedClientResult: CatchRateResult? = null
    private var lastClientCalcTime = 0L
    private val CLIENT_CALC_INTERVAL_MS = 250L
    
    private var lastServerResponse: CatchRateResponsePayload? = null
    
    private var cachedComparison: List<BallComparisonCalculator.BallCatchRate>? = null
    private var lastComparisonTime = 0L
    private val COMPARISON_CALC_INTERVAL_MS = 500L
    
    private val serverRequiredBalls = setOf("love_ball", "level_ball", "repeat_ball", "lure_ball")
    
    override fun onHudRender(drawContext: DrawContext, tickCounter: RenderTickCounter) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val config = CatchRateConfig.get()
        
        if (!config.hudEnabled) return
        
        val battle = CobblemonClient.battle
        
        // Handle out-of-combat display
        if (battle == null) {
            resetState()
            if (config.showOutOfCombat) {
                renderOutOfCombatHud(drawContext, client, player)
            }
            return
        }
        
        if (battle.battleId != lastBattleId) onBattleStart(battle)
        if (!battle.isPvW) return
        
        updateTurnCount(battle)
        
        val heldItem = player.mainHandStack
        if (!isPokeball(heldItem)) return
        
        val opponentPokemon = getOpponentPokemon(battle) ?: return
        
        checkCacheInvalidation(opponentPokemon, heldItem)
        
        val ballItemId = getBallId(heldItem)
        val ballName = ballItemId.substringAfter(":").lowercase()
        val serverHasMod = CatchRateClientNetworking.isServerModPresent()
        
        if (config.showBallComparison) {
            renderBallComparisonPanel(drawContext, client, opponentPokemon, battle)
            return
        }
        
        if (serverHasMod) {
            CatchRateClientNetworking.requestCatchRate(opponentPokemon.uuid, ballItemId, turnCount)
            val serverResponse = CatchRateClientNetworking.getCachedResponse(opponentPokemon.uuid)
            if (serverResponse != null) {
                lastServerResponse = serverResponse
                renderServerModeHud(drawContext, client, serverResponse)
            } else if (lastServerResponse != null) {
                renderServerModeHud(drawContext, client, lastServerResponse!!)
            }
        } else {
            if (ballName in serverRequiredBalls) {
                renderUnsupportedBallHud(drawContext, client, ballName)
            } else {
                val result = getClientCalculation(opponentPokemon, heldItem)
                renderClientModeHud(drawContext, client, result, ballName)
            }
        }
    }
    
    private fun onBattleStart(battle: ClientBattle) {
        lastBattleId = battle.battleId
        turnCount = 1
        lastActionRequestCount = 0
        CatchRateClientNetworking.clearCache()
        cachedClientResult = null
        lastServerResponse = null
    }
    
    private fun updateTurnCount(battle: ClientBattle) {
        val currentRequestCount = battle.pendingActionRequests.size
        if (currentRequestCount > 0 && currentRequestCount != lastActionRequestCount) {
            if (lastActionRequestCount > 0) {
                turnCount++
                CatchRateClientNetworking.clearCache()
                cachedClientResult = null
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
            CatchRateClientNetworking.invalidateCache(pokemon.uuid)
            cachedClientResult = null
        }
    }
    
    private fun resetState() {
        lastBattleId = null
        lastPokemonUuid = null
        lastBallItem = null
        lastHpValue = -1F
        lastStatusName = null
        cachedClientResult = null
        lastServerResponse = null
        cachedComparison = null
        CatchRateClientNetworking.clearCache()
    }
    
    private fun getClientCalculation(pokemon: ClientBattlePokemon, heldItem: ItemStack): CatchRateResult {
        val now = System.currentTimeMillis()
        if (cachedClientResult == null || (now - lastClientCalcTime) > CLIENT_CALC_INTERVAL_MS) {
            cachedClientResult = CatchRateCalculator.calculateCatchRate(pokemon, heldItem, turnCount, null, true)
            lastClientCalcTime = now
        }
        return cachedClientResult!!
    }
    
    private fun renderServerModeHud(drawContext: DrawContext, client: MinecraftClient, data: CatchRateResponsePayload) {
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        val boxWidth = 130
        val boxHeight = 72
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw styled panel using HudDrawing
        HudDrawing.drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, data.catchChance)
        
        // Pokemon name and level header
        val nameText = "${data.pokemonName} Lv${data.pokemonLevel}"
        drawContext.drawTextWithShadow(textRenderer, nameText, x + 6, y + 4, Colors.TEXT_WHITE)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (data.isGuaranteed) {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, "★ 100% CATCH ★", x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, data.catchChance, false)
            val percentText = "${String.format("%.1f", data.catchChance)}%"
            val percentColor = HudDrawing.getChanceColorInt(data.catchChance)
            drawContext.drawTextWithShadow(textRenderer, percentText, x + 6, barY + 12, percentColor)
        }
        
        // HP bar
        val hpY = barY + 24
        drawContext.drawTextWithShadow(textRenderer, "HP", x + 6, hpY, Colors.TEXT_GRAY)
        HudDrawing.drawHealthBar(drawContext, x + 22, hpY + 1, 60, data.hpPercent.toFloat() / 100f)
        
        // Status effect (if any)
        val infoY = hpY + 12
        if (data.statusMultiplier > 1.0) {
            val statusIcon = HudDrawing.getStatusIcon(data.statusEffect)
            drawContext.drawTextWithShadow(textRenderer, "$statusIcon ${data.statusEffect}", x + 6, infoY, Colors.TEXT_PURPLE)
        }
        
        // Ball multiplier
        val ballY = if (data.statusMultiplier > 1.0) infoY + 10 else infoY
        val ballColor = HudDrawing.getBallMultiplierColor(data.ballMultiplier)
        val ballIcon = if (data.ballConditionMet) "●" else "○"
        drawContext.drawTextWithShadow(textRenderer, "$ballIcon ${CatchRateFormula.formatBallNameCompact(data.ballName)} ${String.format("%.1f", data.ballMultiplier)}x", x + 6, ballY, ballColor)
    }
    
    private fun renderClientModeHud(drawContext: DrawContext, client: MinecraftClient, result: CatchRateResult, ballName: String) {
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        val boxWidth = 130
        val boxHeight = 68
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw styled panel using HudDrawing
        HudDrawing.drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, result.percentage)
        
        // Header
        drawContext.drawTextWithShadow(textRenderer, "Catch Rate", x + 6, y + 4, Colors.TEXT_WHITE)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (result.isGuaranteed) {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, "✓ GUARANTEED", x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, result.percentage, false)
            val percentText = "${String.format("%.1f", result.percentage)}%"
            val percentColor = HudDrawing.getChanceColorInt(result.percentage)
            drawContext.drawTextWithShadow(textRenderer, percentText, x + 6, barY + 12, percentColor)
        }
        
        // HP bar
        val hpY = barY + 24
        drawContext.drawTextWithShadow(textRenderer, "HP", x + 6, hpY, Colors.TEXT_GRAY)
        HudDrawing.drawHealthBar(drawContext, x + 22, hpY + 1, 60, (result.hpPercentage / 100.0).toFloat())
        
        // Status effect (if any)
        val infoY = hpY + 12
        var currentY = infoY
        if (result.statusMultiplier > 1.0) {
            val statusIcon = HudDrawing.getStatusIcon(result.statusName)
            drawContext.drawTextWithShadow(textRenderer, "$statusIcon ${result.statusName}", x + 6, currentY, Colors.TEXT_PURPLE)
            currentY += 10
        }
        
        // Ball multiplier
        val ballColor = HudDrawing.getBallMultiplierColor(result.ballMultiplier)
        val ballDisplay = CatchRateFormula.formatBallNameCompact(result.ballName)
        val turnInfo = if (ballName == "timer_ball" || ballName == "quick_ball") " T$turnCount" else ""
        drawContext.drawTextWithShadow(textRenderer, "● $ballDisplay ${String.format("%.1f", result.ballMultiplier)}x$turnInfo", x + 6, currentY, ballColor)
    }
    
    private fun renderUnsupportedBallHud(drawContext: DrawContext, client: MinecraftClient, ballName: String) {
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        val boxWidth = 130
        val boxHeight = 52
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw styled panel using HudDrawing
        HudDrawing.drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, 0.0)
        
        // Header
        drawContext.drawTextWithShadow(textRenderer, "Catch Rate", x + 6, y + 4, Colors.TEXT_WHITE)
        
        // Unknown percentage with bar
        val barY = y + 16
        HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 0.0, false)
        drawContext.drawTextWithShadow(textRenderer, "??%", x + 6, barY + 12, Colors.TEXT_YELLOW)
        
        // Ball name and server requirement
        val infoY = barY + 24
        drawContext.drawTextWithShadow(textRenderer, CatchRateFormula.formatBallNameCompact(ballName), x + 6, infoY, Colors.TEXT_RED)
        
        // All server-required balls show the same message
        drawContext.drawTextWithShadow(textRenderer, "Server required", x + 6, infoY + 10, Colors.TEXT_DARK_GRAY)
    }
    
    private fun renderOutOfCombatHud(drawContext: DrawContext, client: MinecraftClient, player: ClientPlayerEntity) {
        val heldItem = player.mainHandStack
        if (!isPokeball(heldItem)) return
        
        // Get the wild Pokemon the player is looking at (returns null for owned Pokemon)
        val pokemonEntity = BallComparisonCalculator.getLookedAtPokemon() ?: return
        val pokemon = pokemonEntity.pokemon
        
        val ballItemId = getBallId(heldItem)
        val ballName = ballItemId.substringAfter(":").lowercase()
        
        // Calculate the catch rate for the wild Pokemon
        val result = BallComparisonCalculator.calculateForWorldPokemon(pokemonEntity, ballName) ?: return
        
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        val boxWidth = 130
        val boxHeight = 74  // Extra space for 0.5x penalty indicator
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw styled panel with wild indicator
        HudDrawing.drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, result.catchRate, isWild = true)
        
        // Pokemon name and level header
        val nameText = "${pokemon.species.name} Lv${pokemon.level}"
        drawContext.drawTextWithShadow(textRenderer, nameText, x + 6, y + 4, Colors.TEXT_WHITE)
        
        // Wild indicator
        drawContext.drawTextWithShadow(textRenderer, "WILD", x + boxWidth - 28, y + 4, Colors.TEXT_WILD_RED)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (result.isGuaranteed) {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, "✓ GUARANTEED", x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, result.catchRate, false)
            val percentText = "${String.format("%.1f", result.catchRate)}%"
            val percentColor = HudDrawing.getChanceColorInt(result.catchRate)
            drawContext.drawTextWithShadow(textRenderer, percentText, x + 6, barY + 12, percentColor)
        }
        
        // HP (always full for wild)
        val hpY = barY + 24
        drawContext.drawTextWithShadow(textRenderer, "HP", x + 6, hpY, Colors.TEXT_GRAY)
        HudDrawing.drawHealthBar(drawContext, x + 22, hpY + 1, 60, 1.0f)
        
        // Ball multiplier with condition
        val ballY = hpY + 12
        val ballColor = HudDrawing.getBallMultiplierColor(result.multiplier.toFloat())
        val conditionIcon = if (result.conditionMet) "●" else "○"
        drawContext.drawTextWithShadow(textRenderer, "$conditionIcon ${CatchRateFormula.formatBallNameCompact(result.ballName)} ${String.format("%.1f", result.multiplier)}x", x + 6, ballY, ballColor)
        
        // Out of combat penalty indicator
        val penaltyY = ballY + 10
        drawContext.drawTextWithShadow(textRenderer, "⚠ Out of combat: 0.5x", x + 6, penaltyY, Colors.TEXT_ORANGE)
    }
    
    private fun renderBallComparisonPanel(drawContext: DrawContext, client: MinecraftClient, pokemon: ClientBattlePokemon, battle: ClientBattle) {
        val now = System.currentTimeMillis()
        if (cachedComparison == null || (now - lastComparisonTime) > COMPARISON_CALC_INTERVAL_MS) {
            cachedComparison = BallComparisonCalculator.calculateAllBalls(pokemon, turnCount, battle)
            lastComparisonTime = now
        }
        
        val comparison = cachedComparison ?: return
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        val lineHeight = 10
        val padding = 6
        val headerHeight = 20
        
        val lines = mutableListOf<Triple<Text, Text, Text>>()
        
        for ((index, ball) in comparison.take(12).withIndex()) {
            val medal = when (index) {
                0 -> "§6★ "
                1 -> "§f◆ "
                2 -> "§c◆ "
                else -> "   "
            }
            
            val ballText = Text.literal("$medal${ball.displayName}")
            val rateColor = HudDrawing.getChanceFormatting(ball.catchRate)
            val rateText = Text.literal("${String.format("%.1f", ball.catchRate)}%").formatted(rateColor)
            
            val multColor = HudDrawing.getBallMultiplierFormatting(ball.multiplier)
            val multText = Text.literal("${String.format("%.1f", ball.multiplier)}x").formatted(multColor)
            
            lines.add(Triple(ballText, rateText, multText))
        }
        
        val col1Width = lines.maxOfOrNull { textRenderer.getWidth(it.first) } ?: 100
        val col2Width = 45
        val col3Width = 35
        val boxWidth = col1Width + col2Width + col3Width + padding * 4
        val footerHeight = 18
        val boxHeight = headerHeight + lines.size * lineHeight + footerHeight + padding * 2
        
        val x = (screenWidth - boxWidth) / 2
        val y = (screenHeight - boxHeight) / 2
        
        // Use HudDrawing for consistent panel styling
        HudDrawing.drawComparisonPanel(drawContext, x, y, boxWidth, boxHeight, headerHeight)
        
        val header = Text.literal("⚔ Ball Comparison (Turn $turnCount)").formatted(Formatting.GOLD, Formatting.BOLD)
        drawContext.drawTextWithShadow(textRenderer, header, x + padding, y + padding, 0xFFFFFF)
        
        drawContext.drawHorizontalLine(x + padding, x + boxWidth - padding, y + headerHeight, 0xFF444455.toInt())
        
        var lineY = y + headerHeight + padding
        for ((index, triple) in lines.withIndex()) {
            val (ballText, rateText, multText) = triple
            
            // Alternating row background for readability
            if (index % 2 == 0) {
                drawContext.fill(x + 2, lineY - 1, x + boxWidth - 2, lineY + 9, 0x15FFFFFF)
            }
            
            drawContext.drawTextWithShadow(textRenderer, ballText, x + padding, lineY, 0xFFFFFF)
            drawContext.drawTextWithShadow(textRenderer, rateText, x + col1Width + padding * 2, lineY, 0xFFFFFF)
            drawContext.drawTextWithShadow(textRenderer, multText, x + col1Width + col2Width + padding * 3, lineY, 0xFFFFFF)
            lineY += lineHeight
        }
        
        // Footer separator and text
        val footerY = y + boxHeight - padding - 9
        drawContext.drawHorizontalLine(x + padding, x + boxWidth - padding, footerY - 4, Colors.BAR_BORDER)
        drawContext.drawTextWithShadow(textRenderer, "Release G to close", x + padding, footerY, Colors.TEXT_DARK_GRAY)
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
