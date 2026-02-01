package com.catchrate.client

import com.catchrate.CatchRateCalculator
import com.catchrate.CatchRateDisplayMod
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
import kotlin.math.roundToInt

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
            renderBallComparisonPanel(drawContext, client, opponentPokemon)
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
                val result = getClientCalculation(opponentPokemon, heldItem, battle)
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
    
    private fun getClientCalculation(pokemon: ClientBattlePokemon, heldItem: ItemStack, battle: ClientBattle): CatchRateResult {
        val now = System.currentTimeMillis()
        if (cachedClientResult == null || (now - lastClientCalcTime) > CLIENT_CALC_INTERVAL_MS) {
            cachedClientResult = CatchRateCalculator.calculateCatchRate(pokemon, heldItem, turnCount, null, true, battle)
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
        
        // Draw styled panel
        drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, data.catchChance)
        
        // Pokemon name and level header
        val nameText = "${data.pokemonName} Lv${data.pokemonLevel}"
        drawContext.drawTextWithShadow(textRenderer, nameText, x + 6, y + 4, 0xFFFFFF)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (data.isGuaranteed) {
            drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, "✓ GUARANTEED", x + 6, barY + 12, 0x55FF55)
        } else {
            drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, data.catchChance, false)
            val percentText = "${String.format("%.1f", data.catchChance)}%"
            val percentColor = getChanceColorInt(data.catchChance)
            drawContext.drawTextWithShadow(textRenderer, percentText, x + 6, barY + 12, percentColor)
        }
        
        // HP bar
        val hpY = barY + 24
        drawContext.drawTextWithShadow(textRenderer, "HP", x + 6, hpY, 0xAAAAAA)
        drawHealthBar(drawContext, x + 22, hpY + 1, 60, data.hpPercent.toFloat() / 100f)
        
        // Status effect (if any)
        val infoY = hpY + 12
        if (data.statusMultiplier > 1.0) {
            val statusIcon = getStatusIcon(data.statusEffect)
            drawContext.drawTextWithShadow(textRenderer, "$statusIcon ${data.statusEffect}", x + 6, infoY, 0xAA55FF)
        }
        
        // Ball multiplier
        val ballY = if (data.statusMultiplier > 1.0) infoY + 10 else infoY
        val ballColor = getBallMultiplierColor(data.ballMultiplier)
        val ballIcon = if (data.ballConditionMet) "●" else "○"
        drawContext.drawTextWithShadow(textRenderer, "$ballIcon ${formatBallName(data.ballName)} ${String.format("%.1f", data.ballMultiplier)}x", x + 6, ballY, ballColor)
    }
    
    private fun renderClientModeHud(drawContext: DrawContext, client: MinecraftClient, result: CatchRateResult, ballName: String) {
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        val boxWidth = 130
        val boxHeight = 68
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw styled panel
        drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, result.percentage)
        
        // Header
        drawContext.drawTextWithShadow(textRenderer, "Catch Rate", x + 6, y + 4, 0xFFFFFF)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (result.isGuaranteed) {
            drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, "✓ GUARANTEED", x + 6, barY + 12, 0x55FF55)
        } else {
            drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, result.percentage, false)
            val percentText = "${String.format("%.1f", result.percentage)}%"
            val percentColor = getChanceColorInt(result.percentage)
            drawContext.drawTextWithShadow(textRenderer, percentText, x + 6, barY + 12, percentColor)
        }
        
        // HP bar
        val hpY = barY + 24
        drawContext.drawTextWithShadow(textRenderer, "HP", x + 6, hpY, 0xAAAAAA)
        drawHealthBar(drawContext, x + 22, hpY + 1, 60, (result.hpPercentage / 100.0).toFloat())
        
        // Status effect (if any)
        val infoY = hpY + 12
        var currentY = infoY
        if (result.statusMultiplier > 1.0) {
            val statusIcon = getStatusIcon(result.statusName)
            drawContext.drawTextWithShadow(textRenderer, "$statusIcon ${result.statusName}", x + 6, currentY, 0xAA55FF)
            currentY += 10
        }
        
        // Ball multiplier
        val ballColor = getBallMultiplierColor(result.ballMultiplier)
        val ballDisplay = formatBallName(result.ballName)
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
        
        // Draw styled panel
        drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, 0.0)
        
        // Header
        drawContext.drawTextWithShadow(textRenderer, "Catch Rate", x + 6, y + 4, 0xFFFFFF)
        
        // Unknown percentage with bar
        val barY = y + 16
        drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 0.0, false)
        drawContext.drawTextWithShadow(textRenderer, "??%", x + 6, barY + 12, 0xFFFF55)
        
        // Ball name and server requirement
        val infoY = barY + 24
        drawContext.drawTextWithShadow(textRenderer, formatBallName(ballName), x + 6, infoY, 0xFF5555)
        
        val requirement = when (ballName) {
            "love_ball" -> "Server required"
            "level_ball" -> "Server required"
            "repeat_ball" -> "Server required"
            "lure_ball" -> "Server required"
            else -> "Server required"
        }
        drawContext.drawTextWithShadow(textRenderer, requirement, x + 6, infoY + 10, 0x888888)
    }
    
    private fun renderLoadingHud(drawContext: DrawContext, client: MinecraftClient) {
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        val boxWidth = 130
        val boxHeight = 40
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw styled panel
        drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, 50.0)
        
        // Header
        drawContext.drawTextWithShadow(textRenderer, "Catch Rate", x + 6, y + 4, 0xFFFFFF)
        
        // Loading indicator
        val dots = ".".repeat((System.currentTimeMillis() / 500 % 4).toInt())
        drawContext.drawTextWithShadow(textRenderer, "Calculating$dots", x + 6, y + 18, 0xAAAAAA)
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
        drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, result.catchRate, isWild = true)
        
        // Pokemon name and level header
        val nameText = "${pokemon.species.name} Lv${pokemon.level}"
        drawContext.drawTextWithShadow(textRenderer, nameText, x + 6, y + 4, 0xFFFFFF)
        
        // Wild indicator
        drawContext.drawTextWithShadow(textRenderer, "WILD", x + boxWidth - 28, y + 4, 0xE43838)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (result.isGuaranteed) {
            drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, "✓ GUARANTEED", x + 6, barY + 12, 0x55FF55)
        } else {
            drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, result.catchRate, false)
            val percentText = "${String.format("%.1f", result.catchRate)}%"
            val percentColor = getChanceColorInt(result.catchRate)
            drawContext.drawTextWithShadow(textRenderer, percentText, x + 6, barY + 12, percentColor)
        }
        
        // HP (always full for wild)
        val hpY = barY + 24
        drawContext.drawTextWithShadow(textRenderer, "HP", x + 6, hpY, 0xAAAAAA)
        drawHealthBar(drawContext, x + 22, hpY + 1, 60, 1.0f)
        
        // Ball multiplier with condition
        val ballY = hpY + 12
        val ballColor = getBallMultiplierColor(result.multiplier.toFloat())
        val conditionIcon = if (result.conditionMet) "●" else "○"
        drawContext.drawTextWithShadow(textRenderer, "$conditionIcon ${formatBallName(result.ballName)} ${String.format("%.1f", result.multiplier)}x", x + 6, ballY, ballColor)
        
        // Out of combat penalty indicator
        val penaltyY = ballY + 10
        drawContext.drawTextWithShadow(textRenderer, "⚠ Out of combat: 0.5x", x + 6, penaltyY, 0xFFAA00)
    }
    
    private fun renderBallComparisonPanel(drawContext: DrawContext, client: MinecraftClient, pokemon: ClientBattlePokemon) {
        val now = System.currentTimeMillis()
        if (cachedComparison == null || (now - lastComparisonTime) > COMPARISON_CALC_INTERVAL_MS) {
            cachedComparison = BallComparisonCalculator.calculateAllBalls(pokemon, turnCount)
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
            val rateColor = getChanceFormatting(ball.catchRate)
            val rateText = Text.literal("${String.format("%.1f", ball.catchRate)}%").formatted(rateColor)
            
            val multColor = when {
                ball.multiplier >= 3.0 -> Formatting.GREEN
                ball.multiplier >= 1.5 -> Formatting.AQUA
                ball.multiplier < 1.0 -> Formatting.RED
                else -> Formatting.GRAY
            }
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
        
        // Styled background
        drawContext.fill(x + 1, y + 1, x + boxWidth - 1, y + boxHeight - 1, 0xE5101820.toInt())
        drawContext.fill(x + 2, y + 2, x + boxWidth - 2, y + headerHeight, 0x40000000)
        
        // Gold border with rounded corners (Cobblemon style)
        val borderColor = 0xFFFFAA00.toInt()
        drawContext.drawHorizontalLine(x + 2, x + boxWidth - 3, y, borderColor)
        drawContext.drawHorizontalLine(x + 2, x + boxWidth - 3, y + boxHeight - 1, borderColor)
        drawContext.drawVerticalLine(x, y + 2, y + boxHeight - 3, borderColor)
        drawContext.drawVerticalLine(x + boxWidth - 1, y + 2, y + boxHeight - 3, borderColor)
        
        // Corner pixels
        drawContext.fill(x + 1, y + 1, x + 2, y + 2, borderColor)
        drawContext.fill(x + boxWidth - 2, y + 1, x + boxWidth - 1, y + 2, borderColor)
        drawContext.fill(x + 1, y + boxHeight - 2, x + 2, y + boxHeight - 1, borderColor)
        drawContext.fill(x + boxWidth - 2, y + boxHeight - 2, x + boxWidth - 1, y + boxHeight - 1, borderColor)
        
        // Inner highlight
        drawContext.drawHorizontalLine(x + 3, x + boxWidth - 4, y + 1, 0x30FFFFFF)
        
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
        drawContext.drawHorizontalLine(x + padding, x + boxWidth - padding, footerY - 4, 0xFF333344.toInt())
        drawContext.drawTextWithShadow(textRenderer, "Release G to close", x + padding, footerY, 0x888888)
    }
    
    // ==================== POKEMON-STYLE UI RENDERING ====================
    
    private fun drawStyledPanel(drawContext: DrawContext, x: Int, y: Int, width: Int, height: Int, catchChance: Double, isWild: Boolean = false) {
        // Background with gradient effect (darker at edges)
        drawContext.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xE5101820.toInt())
        
        // Inner gradient panels for depth
        drawContext.fill(x + 2, y + 2, x + width - 2, y + 14, 0x40000000)
        
        // Outer border - main color based on catch chance
        val borderColor = getChanceColorInt(catchChance) or 0xFF000000.toInt()
        drawContext.drawHorizontalLine(x + 2, x + width - 3, y, borderColor)
        drawContext.drawHorizontalLine(x + 2, x + width - 3, y + height - 1, borderColor)
        drawContext.drawVerticalLine(x, y + 2, y + height - 3, borderColor)
        drawContext.drawVerticalLine(x + width - 1, y + 2, y + height - 3, borderColor)
        
        // Corner pixels for rounded effect
        drawContext.fill(x + 1, y + 1, x + 2, y + 2, borderColor)
        drawContext.fill(x + width - 2, y + 1, x + width - 1, y + 2, borderColor)
        drawContext.fill(x + 1, y + height - 2, x + 2, y + height - 1, borderColor)
        drawContext.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, borderColor)
        
        // Inner highlight line at top
        drawContext.drawHorizontalLine(x + 3, x + width - 4, y + 1, 0x30FFFFFF)
        
        // Wild Pokemon gets red accent
        if (isWild) {
            drawContext.drawHorizontalLine(x + 3, x + width - 4, y + height - 2, 0xFFE43838.toInt())
        }
    }
    
    private fun drawCatchBar(drawContext: DrawContext, x: Int, y: Int, width: Int, percentage: Double, isGuaranteed: Boolean) {
        val barHeight = 8
        
        // Bar background
        drawContext.fill(x, y, x + width, y + barHeight, 0xFF1A1A2E.toInt())
        
        // Border
        drawContext.drawHorizontalLine(x, x + width - 1, y, 0xFF333344.toInt())
        drawContext.drawHorizontalLine(x, x + width - 1, y + barHeight - 1, 0xFF333344.toInt())
        drawContext.drawVerticalLine(x, y, y + barHeight - 1, 0xFF333344.toInt())
        drawContext.drawVerticalLine(x + width - 1, y, y + barHeight - 1, 0xFF333344.toInt())
        
        // Fill bar
        val fillWidth = ((width - 2) * (percentage / 100.0)).roundToInt().coerceAtLeast(0)
        if (fillWidth > 0) {
            val fillColor = if (isGuaranteed) 0xFF55FF55.toInt() else getChanceColorInt(percentage) or 0xFF000000.toInt()
            drawContext.fill(x + 1, y + 1, x + 1 + fillWidth, y + barHeight - 1, fillColor)
            
            // Highlight on bar
            val highlightColor = if (isGuaranteed) 0x5055FF55 else (getChanceColorInt(percentage) and 0x50FFFFFF)
            drawContext.fill(x + 1, y + 1, x + 1 + fillWidth, y + 3, highlightColor)
        }
        
        // Tick marks at 25%, 50%, 75%
        for (tick in listOf(0.25, 0.5, 0.75)) {
            val tickX = x + ((width - 2) * tick).roundToInt()
            drawContext.drawVerticalLine(tickX, y + 1, y + barHeight - 2, 0x40FFFFFF)
        }
    }
    
    private fun drawHealthBar(drawContext: DrawContext, x: Int, y: Int, width: Int, ratio: Float) {
        val barHeight = 6
        
        // Bar background
        drawContext.fill(x, y, x + width, y + barHeight, 0xFF1A1A2E.toInt())
        
        // Calculate health colors (Cobblemon style)
        val (red, green) = getHealthBarColors(ratio)
        val healthColor = ((255).shl(24)) or ((red * 255).toInt().shl(16)) or ((green * 255).toInt().shl(8)) or (70)
        
        // Fill bar
        val fillWidth = ((width - 2) * ratio).roundToInt().coerceAtLeast(0)
        if (fillWidth > 0) {
            drawContext.fill(x + 1, y + 1, x + 1 + fillWidth, y + barHeight - 1, healthColor)
        }
        
        // Border
        drawContext.drawHorizontalLine(x, x + width - 1, y, 0xFF333344.toInt())
        drawContext.drawHorizontalLine(x, x + width - 1, y + barHeight - 1, 0xFF333344.toInt())
    }
    
    private fun getHealthBarColors(ratio: Float): Pair<Float, Float> {
        val redRatio = 0.2f
        val yellowRatio = 0.5f
        
        val r = if (ratio > redRatio) (-2 * ratio + 2).coerceIn(0f, 1f) else 1.0f
        val g = when {
            ratio > yellowRatio -> 1.0f
            ratio > redRatio -> (ratio / yellowRatio).coerceIn(0f, 1f)
            else -> 0.0f
        }
        return r to g
    }
    
    private fun getStatusIcon(status: String): String = when (status.lowercase()) {
        "asleep", "sleep" -> "💤"
        "frozen" -> "❄"
        "paralyzed", "paralysis" -> "⚡"
        "burned", "burn" -> "🔥"
        "poisoned", "poison", "badly poisoned", "poisonbadly" -> "☠"
        else -> "●"
    }
    
    private fun getBallMultiplierColor(multiplier: Number): Int = when {
        multiplier.toDouble() >= 3.0 -> 0x55FF55  // Excellent - Green
        multiplier.toDouble() >= 2.0 -> 0x55FFFF  // Great - Cyan
        multiplier.toDouble() >= 1.5 -> 0xFFFF55  // Good - Yellow
        multiplier.toDouble() < 1.0 -> 0xFF5555   // Poor - Red
        else -> 0xAAAAAA                           // Normal - Gray
    }
    
    private fun getChanceColorInt(percentage: Double): Int = when {
        percentage >= 75.0 -> 0x55FF55   // High - Green
        percentage >= 50.0 -> 0xFFFF55   // Medium - Yellow
        percentage >= 25.0 -> 0xFFAA00   // Low - Orange
        else -> 0xFF5555                  // Very Low - Red
    }
    
    // ==================== BALL COMPARISON PANEL (KEPT FOR REFERENCE) ====================
    
    private fun getOpponentPokemon(battle: ClientBattle): ClientBattlePokemon? {
        return try { battle.side2.activeClientBattlePokemon.firstOrNull()?.battlePokemon } catch (e: Exception) { null }
    }
    
    private fun isPokeball(itemStack: ItemStack): Boolean {
        if (itemStack.isEmpty) return false
        val itemId = itemStack.item.toString().lowercase()
        return itemId.contains("_ball") && !itemId.contains("snowball") && !itemId.contains("slimeball") && !itemId.contains("fireball") && !itemId.contains("magma_ball")
    }
    
    private fun getBallId(itemStack: ItemStack): String = itemStack.item.toString().substringAfter("{").substringBefore("}").trim()
    
    private fun formatBallName(name: String): String {
        val cleaned = name.replace("_", " ").replace("cobblemon:", "")
        // Shorten common ball names
        return cleaned.split(" ").joinToString(" ") { 
            it.replaceFirstChar { c -> c.uppercaseChar() } 
        }.replace(" Ball", "")
    }
    
    private fun getChanceFormatting(percentage: Double): Formatting = when {
        percentage >= 75.0 -> Formatting.GREEN
        percentage >= 50.0 -> Formatting.YELLOW
        percentage >= 25.0 -> Formatting.GOLD
        else -> Formatting.RED
    }
}
