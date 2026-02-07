package com.catchrate.client

import com.catchrate.CatchRateCalculator
import com.catchrate.CatchRateConstants.Colors
import com.catchrate.CatchRateFormula
import com.catchrate.CatchRateKeybinds
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
    private var lastClientCalcTick = 0L
    
    private var lastServerResponse: CatchRateResponsePayload? = null
    
    private var cachedComparison: List<BallComparisonCalculator.BallCatchRate>? = null
    private var lastComparisonTick = 0L
    private var lastComparisonTurnCount = 0
    
    private var tickCounter = 0L
    
    // Cached rendering data to avoid allocations every frame
    private var cachedServerHudData: ServerHudCache? = null
    
    private data class ServerHudCache(
        val pokemonUuid: java.util.UUID,
        val catchChance: Double,
        val nameText: String,
        val hpText: String,
        val statusText: String,
        val ballText: String,
        val boxWidth: Int,
        val hasStatus: Boolean
    )
    
    private val serverRequiredBalls = setOf("love_ball", "level_ball", "repeat_ball", "lure_ball")
    
    companion object {
        private const val CLIENT_CALC_INTERVAL_TICKS = 5L
        private const val COMPARISON_CALC_INTERVAL_TICKS = 10L
    }
    
    override fun onHudRender(drawContext: DrawContext, tickCounter: RenderTickCounter) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val config = CatchRateConfig.get()
        
        if (!config.hudEnabled) return
        
        // Respect F1 to hide HUD
        if (client.options.hudHidden) return
        
        this.tickCounter++
        
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
        cachedServerHudData = null
        CatchRateClientNetworking.clearCache()
    }
    
    private fun getClientCalculation(pokemon: ClientBattlePokemon, heldItem: ItemStack): CatchRateResult {
        if (cachedClientResult == null || (tickCounter - lastClientCalcTick) > CLIENT_CALC_INTERVAL_TICKS) {
            cachedClientResult = CatchRateCalculator.calculateCatchRate(pokemon, heldItem, turnCount, null, true)
            lastClientCalcTick = tickCounter
        }
        return cachedClientResult!!
    }
    
    private fun renderServerModeHud(drawContext: DrawContext, client: MinecraftClient, data: CatchRateResponsePayload) {
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        // Use cached HUD data if available and unchanged
        val cache = cachedServerHudData
        val needsRecalc = cache == null || cache.pokemonUuid != data.pokemonUuid || cache.catchChance != data.catchChance
        
        val hudCache = if (needsRecalc) {
            val nameText = "${data.pokemonName} Lv${data.pokemonLevel}"
            val hpMultiplier = (3.0 - 2.0 * data.hpPercent / 100.0) / 3.0
            val hpText = "HP ${String.format("%.2f", hpMultiplier)}x"
            val statusIcon = HudDrawing.getStatusIcon(data.statusEffect)
            val statusText = "$statusIcon ${data.statusEffect} ${String.format("%.1f", data.statusMultiplier)}x"
            val ballIcon = if (data.ballConditionMet) "●" else "○"
            val ballText = "$ballIcon ${CatchRateFormula.formatBallNameCompact(data.ballName)} ${String.format("%.1f", data.ballMultiplier)}x"
            
            // Calculate percentage text width (must include in box sizing)
            val percentText = if (data.isGuaranteed) {
                "★ 100% CATCH ★"
            } else {
                "${CatchRateFormula.formatCatchPercentage(data.catchChance, false)}%"
            }
            
            val textWidths = mutableListOf(
                textRenderer.getWidth(nameText),
                textRenderer.getWidth(hpText),
                textRenderer.getWidth(ballText),
                textRenderer.getWidth(data.ballConditionDesc),
                textRenderer.getWidth(percentText)
            )
            val hasStatus = data.statusMultiplier > 1.0
            if (hasStatus) textWidths.add(textRenderer.getWidth(statusText))
            
            val boxWidth = (textWidths.maxOrNull() ?: 100) + 16
            
            ServerHudCache(data.pokemonUuid, data.catchChance, nameText, hpText, statusText, ballText, boxWidth, hasStatus).also {
                cachedServerHudData = it
            }
        } else cache!!
        
        val boxHeight = if (hudCache.hasStatus) 82 else 72
        val (x, y) = config.getPosition(screenWidth, screenHeight, hudCache.boxWidth, boxHeight)
        
        // Draw styled panel using HudDrawing
        HudDrawing.drawStyledPanel(drawContext, x, y, hudCache.boxWidth, boxHeight, data.catchChance)
        
        // Pokemon name and level header
        drawContext.drawTextWithShadow(textRenderer, hudCache.nameText, x + 6, y + 4, Colors.TEXT_WHITE)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (data.isGuaranteed) {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, hudCache.boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, "★ 100% CATCH ★", x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, hudCache.boxWidth - 12, data.catchChance, false)
            val percentText = "${CatchRateFormula.formatCatchPercentage(data.catchChance, false)}%"
            val percentColor = HudDrawing.getChanceColorInt(data.catchChance)
            drawContext.drawTextWithShadow(textRenderer, percentText, x + 6, barY + 12, percentColor)
        }
        
        // HP multiplier row
        var currentY = barY + 26
        drawContext.drawTextWithShadow(textRenderer, hudCache.hpText, x + 6, currentY, Colors.TEXT_GRAY)
        
        // Status effect row (if any)
        if (hudCache.hasStatus) {
            currentY += 10
            drawContext.drawTextWithShadow(textRenderer, hudCache.statusText, x + 6, currentY, Colors.TEXT_PURPLE)
        }
        
        // Ball multiplier row
        currentY += 10
        val ballColor = HudDrawing.getBallMultiplierColor(data.ballMultiplier)
        drawContext.drawTextWithShadow(textRenderer, hudCache.ballText, x + 6, currentY, ballColor)
        
        // Ball condition description row
        currentY += 10
        val conditionColor = if (data.ballConditionMet) Colors.TEXT_DARK_GREEN else Colors.TEXT_DARK_GRAY
        drawContext.drawTextWithShadow(textRenderer, data.ballConditionDesc, x + 6, currentY, conditionColor)
    }
    
    private fun renderClientModeHud(drawContext: DrawContext, client: MinecraftClient, result: CatchRateResult, ballName: String) {
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        // Calculate dynamic box size based on content
        val headerText = "Catch Rate"
        val percentText = if (result.isGuaranteed) {
            "✓ GUARANTEED"
        } else {
            "${CatchRateFormula.formatCatchPercentage(result.percentage, result.isGuaranteed)}%"
        }
        val statusIcon = HudDrawing.getStatusIcon(result.statusName)
        val statusText = "$statusIcon ${result.statusName}"
        val ballDisplay = CatchRateFormula.formatBallNameCompact(result.ballName)
        val turnInfo = if (ballName == "timer_ball" || ballName == "quick_ball") " T$turnCount" else ""
        val ballText = "● $ballDisplay ${String.format("%.1f", result.ballMultiplier)}x$turnInfo"
        
        val textWidths = mutableListOf(
            textRenderer.getWidth(headerText),
            textRenderer.getWidth(percentText),
            textRenderer.getWidth(ballText),
            88  // HP label + health bar minimum width
        )
        if (result.statusMultiplier > 1.0) {
            textWidths.add(textRenderer.getWidth(statusText))
        }
        
        val boxWidth = (textWidths.maxOrNull() ?: 100) + 16
        val hasStatus = result.statusMultiplier > 1.0
        val boxHeight = if (hasStatus) 78 else 68
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw styled panel using HudDrawing
        HudDrawing.drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, result.percentage)
        
        // Header
        drawContext.drawTextWithShadow(textRenderer, "Catch Rate", x + 6, y + 4, Colors.TEXT_WHITE)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (result.isGuaranteed) {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, percentText, x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, result.percentage, false)
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
        if (hasStatus) {
            drawContext.drawTextWithShadow(textRenderer, statusText, x + 6, currentY, Colors.TEXT_PURPLE)
            currentY += 10
        }
        
        // Ball multiplier
        val ballColor = HudDrawing.getBallMultiplierColor(result.ballMultiplier)
        drawContext.drawTextWithShadow(textRenderer, ballText, x + 6, currentY, ballColor)
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
        
        // Match in-combat format: calculate HP multiplier
        val hpPercent = if (pokemon.maxHealth > 0) (pokemon.currentHealth.toDouble() / pokemon.maxHealth.toDouble()) * 100.0 else 100.0
        val hpMultiplier = (3.0 - 2.0 * hpPercent / 100.0) / 3.0
        val hpText = "HP ${String.format("%.2f", hpMultiplier)}x"
        
        // Check for status effect
        val statusName = pokemon.status?.status?.name?.path ?: ""
        val statusMult = CatchRateFormula.getStatusMultiplier(statusName)
        val hasStatus = statusMult > 1.0
        val statusIcon = HudDrawing.getStatusIcon(statusName)
        val statusText = "$statusIcon ${statusName.uppercase()} ${String.format("%.1f", statusMult)}x"
        
        // Ball text
        val ballIcon = if (result.conditionMet) "●" else "○"
        val ballText = "$ballIcon ${CatchRateFormula.formatBallNameCompact(result.ballName)} ${String.format("%.1f", result.multiplier)}x"
        
        // Penalty text (out of combat specific)
        val penaltyText = "⚠ Out of combat: 0.5x"
        
        // Pokemon name with WILD indicator
        val nameText = "${pokemon.species.name} Lv${pokemon.level}"
        
        // Calculate percentage text width (must include in box sizing)
        val percentText = if (result.isGuaranteed) {
            "★ 100% CATCH ★"
        } else {
            "${CatchRateFormula.formatCatchPercentage(result.catchRate, result.isGuaranteed)}%"
        }
        
        // Calculate dynamic box width like in-combat
        val textWidths = mutableListOf(
            textRenderer.getWidth(nameText) + textRenderer.getWidth(" WILD") + 8,
            textRenderer.getWidth(hpText),
            textRenderer.getWidth(ballText),
            textRenderer.getWidth(result.reason),
            textRenderer.getWidth(penaltyText),
            textRenderer.getWidth(percentText)
        )
        if (hasStatus) textWidths.add(textRenderer.getWidth(statusText))
        
        val boxWidth = (textWidths.maxOrNull() ?: 100) + 16
        val boxHeight = if (hasStatus) 92 else 82  // Extra row for penalty
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw styled panel with wild indicator
        HudDrawing.drawStyledPanel(drawContext, x, y, boxWidth, boxHeight, result.catchRate, isWild = true)
        
        // Pokemon name and level header
        drawContext.drawTextWithShadow(textRenderer, nameText, x + 6, y + 4, Colors.TEXT_WHITE)
        
        // Wild indicator (right side of header)
        drawContext.drawTextWithShadow(textRenderer, "WILD", x + boxWidth - textRenderer.getWidth("WILD") - 6, y + 4, Colors.TEXT_WILD_RED)
        
        // Catch rate display with progress bar
        val barY = y + 16
        if (result.isGuaranteed) {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, 100.0, true)
            drawContext.drawTextWithShadow(textRenderer, "★ 100% CATCH ★", x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(drawContext, x + 6, barY, boxWidth - 12, result.catchRate, false)
            val pctText = "${CatchRateFormula.formatCatchPercentage(result.catchRate, result.isGuaranteed)}%"
            val percentColor = HudDrawing.getChanceColorInt(result.catchRate)
            drawContext.drawTextWithShadow(textRenderer, pctText, x + 6, barY + 12, percentColor)
        }
        
        // HP multiplier row (matches in-combat format)
        var currentY = barY + 26
        drawContext.drawTextWithShadow(textRenderer, hpText, x + 6, currentY, Colors.TEXT_GRAY)
        
        // Status effect row (if any)
        if (hasStatus) {
            currentY += 10
            drawContext.drawTextWithShadow(textRenderer, statusText, x + 6, currentY, Colors.TEXT_PURPLE)
        }
        
        // Ball multiplier row
        currentY += 10
        val ballColor = HudDrawing.getBallMultiplierColor(result.multiplier.toFloat())
        drawContext.drawTextWithShadow(textRenderer, ballText, x + 6, currentY, ballColor)
        
        // Ball condition description row
        currentY += 10
        val conditionColor = if (result.conditionMet) Colors.TEXT_DARK_GREEN else Colors.TEXT_DARK_GRAY
        drawContext.drawTextWithShadow(textRenderer, result.reason, x + 6, currentY, conditionColor)
        
        // Out of combat penalty indicator (out-of-combat specific)
        currentY += 10
        drawContext.drawTextWithShadow(textRenderer, penaltyText, x + 6, currentY, Colors.TEXT_ORANGE)
    }
    
    private fun renderBallComparisonPanel(drawContext: DrawContext, client: MinecraftClient, pokemon: ClientBattlePokemon, battle: ClientBattle) {
        val turnChanged = turnCount != lastComparisonTurnCount
        if (cachedComparison == null || turnChanged || (tickCounter - lastComparisonTick) > COMPARISON_CALC_INTERVAL_TICKS) {
            cachedComparison = BallComparisonCalculator.calculateAllBalls(pokemon, turnCount, battle)
            lastComparisonTick = tickCounter
            lastComparisonTurnCount = turnCount
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
            val rateText = Text.literal("${CatchRateFormula.formatCatchPercentage(ball.catchRate, ball.isGuaranteed)}%").formatted(rateColor)
            
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
        val keyName = CatchRateKeybinds.comparisonKeyName
        drawContext.drawTextWithShadow(textRenderer, "Release $keyName to close", x + padding, footerY, Colors.TEXT_DARK_GRAY)
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
