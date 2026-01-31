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
        
        val battle = CobblemonClient.battle ?: run {
            resetState()
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
        val lines = mutableListOf<Text>()
        
        lines.add(Text.literal("${data.pokemonName} Lv${data.pokemonLevel}").formatted(Formatting.WHITE, Formatting.BOLD))
        
        if (data.isGuaranteed) {
            lines.add(Text.literal("GUARANTEED CATCH!").formatted(Formatting.GREEN, Formatting.BOLD))
        } else {
            val color = getChanceFormatting(data.catchChance)
            lines.add(Text.literal("${String.format("%.1f", data.catchChance)}% Catch Chance").formatted(color, Formatting.BOLD))
        }
        
        lines.add(Text.literal("-------------------").formatted(Formatting.DARK_GRAY))
        
        val hpFormatting = when { data.hpPercent <= 20 -> Formatting.GREEN; data.hpPercent <= 50 -> Formatting.YELLOW; else -> Formatting.RED }
        lines.add(Text.literal("HP: ${String.format("%.0f", data.hpPercent)}%").formatted(hpFormatting))
        
        if (data.statusMultiplier > 1.0) {
            lines.add(Text.literal("${data.statusEffect}: ${String.format("%.1f", data.statusMultiplier)}x").formatted(Formatting.LIGHT_PURPLE))
        }
        
        val ballFormatting = when { data.ballMultiplier >= 3.0 -> Formatting.GREEN; data.ballMultiplier >= 1.5 -> Formatting.AQUA; data.ballMultiplier < 1.0 -> Formatting.RED; else -> Formatting.GRAY }
        lines.add(Text.literal("${data.ballName}: ${String.format("%.1f", data.ballMultiplier)}x").formatted(ballFormatting))
        
        val descFormatting = if (data.ballConditionMet) Formatting.GREEN else Formatting.RED
        lines.add(Text.literal("  ${data.ballConditionDesc}").formatted(descFormatting))
        
        renderBox(drawContext, client, lines, data.catchChance)
    }
    
    private fun renderClientModeHud(drawContext: DrawContext, client: MinecraftClient, result: CatchRateResult, ballName: String) {
        val lines = mutableListOf<Text>()
        
        lines.add(Text.literal("Catch Rate").formatted(Formatting.WHITE, Formatting.BOLD))
        
        if (result.isGuaranteed) {
            lines.add(Text.literal("GUARANTEED CATCH!").formatted(Formatting.GREEN, Formatting.BOLD))
        } else {
            val color = getChanceFormatting(result.percentage)
            lines.add(Text.literal("${String.format("%.1f", result.percentage)}%").formatted(color, Formatting.BOLD))
        }
        
        lines.add(Text.literal("-------------------").formatted(Formatting.DARK_GRAY))
        
        val hpFormatting = when { result.hpPercentage <= 20 -> Formatting.GREEN; result.hpPercentage <= 50 -> Formatting.YELLOW; else -> Formatting.RED }
        lines.add(Text.literal("HP: ${String.format("%.0f", result.hpPercentage)}%").formatted(hpFormatting))
        
        if (result.statusMultiplier > 1.0) {
            lines.add(Text.literal("${result.statusName}: ${String.format("%.1f", result.statusMultiplier)}x").formatted(Formatting.LIGHT_PURPLE))
        }
        
        val ballDisplay = formatBallName(result.ballName)
        val ballFormatting = when { result.ballMultiplier >= 3.0 -> Formatting.GREEN; result.ballMultiplier >= 1.5 -> Formatting.AQUA; result.ballMultiplier < 1.0 -> Formatting.RED; else -> Formatting.GRAY }
        lines.add(Text.literal("$ballDisplay: ${String.format("%.1f", result.ballMultiplier)}x").formatted(ballFormatting))
        
        if (ballName == "timer_ball" || ballName == "quick_ball") {
            lines.add(Text.literal("  Turn $turnCount").formatted(Formatting.GOLD))
        }
        
        if (result.levelBonus > 1.0) {
            lines.add(Text.literal("Low Lv Bonus: ${String.format("%.1f", result.levelBonus)}x").formatted(Formatting.AQUA))
        }
        
        lines.add(Text.literal("(Client estimate)").formatted(Formatting.DARK_GRAY))
        
        renderBox(drawContext, client, lines, result.percentage)
    }
    
    private fun renderUnsupportedBallHud(drawContext: DrawContext, client: MinecraftClient, ballName: String) {
        val lines = mutableListOf<Text>()
        
        lines.add(Text.literal("Catch Rate").formatted(Formatting.WHITE, Formatting.BOLD))
        lines.add(Text.literal("??%").formatted(Formatting.YELLOW, Formatting.BOLD))
        lines.add(Text.literal("-------------------").formatted(Formatting.DARK_GRAY))
        lines.add(Text.literal(formatBallName(ballName)).formatted(Formatting.RED))
        lines.add(Text.literal("Server mod required").formatted(Formatting.RED))
        
        val requirement = when (ballName) {
            "love_ball" -> "(Checks your Pokemon)"
            "level_ball" -> "(Checks your levels)"
            "repeat_ball" -> "(Checks Pokedex)"
            "lure_ball" -> "(Checks spawn type)"
            else -> "(Server data needed)"
        }
        lines.add(Text.literal(requirement).formatted(Formatting.DARK_GRAY))
        
        renderBox(drawContext, client, lines, 0.0)
    }
    
    private fun renderLoadingHud(drawContext: DrawContext, client: MinecraftClient) {
        val lines = mutableListOf<Text>()
        lines.add(Text.literal("Catch Rate").formatted(Formatting.WHITE, Formatting.BOLD))
        lines.add(Text.literal("Calculating...").formatted(Formatting.GRAY))
        renderBox(drawContext, client, lines, 50.0)
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
        val boxHeight = headerHeight + lines.size * lineHeight + padding * 2
        
        val x = (screenWidth - boxWidth) / 2
        val y = (screenHeight - boxHeight) / 2
        
        drawContext.fill(x, y, x + boxWidth, y + boxHeight, 0xEE000000.toInt())
        
        val borderColor = 0xFFFFAA00.toInt()
        drawContext.drawHorizontalLine(x, x + boxWidth - 1, y, borderColor)
        drawContext.drawHorizontalLine(x, x + boxWidth - 1, y + boxHeight - 1, borderColor)
        drawContext.drawVerticalLine(x, y, y + boxHeight - 1, borderColor)
        drawContext.drawVerticalLine(x + boxWidth - 1, y, y + boxHeight - 1, borderColor)
        
        val header = Text.literal("Ball Comparison (Turn $turnCount)").formatted(Formatting.GOLD, Formatting.BOLD)
        drawContext.drawTextWithShadow(textRenderer, header, x + padding, y + padding, 0xFFFFFF)
        
        drawContext.drawHorizontalLine(x + padding, x + boxWidth - padding, y + headerHeight, 0xFF555555.toInt())
        
        var lineY = y + headerHeight + padding
        for ((ballText, rateText, multText) in lines) {
            drawContext.drawTextWithShadow(textRenderer, ballText, x + padding, lineY, 0xFFFFFF)
            drawContext.drawTextWithShadow(textRenderer, rateText, x + col1Width + padding * 2, lineY, 0xFFFFFF)
            drawContext.drawTextWithShadow(textRenderer, multText, x + col1Width + col2Width + padding * 3, lineY, 0xFFFFFF)
            lineY += lineHeight
        }
        
        val footer = Text.literal("Release G to close").formatted(Formatting.DARK_GRAY)
        drawContext.drawTextWithShadow(textRenderer, footer, x + padding, y + boxHeight - padding - 8, 0xFFFFFF)
    }
    
    private fun renderBox(drawContext: DrawContext, client: MinecraftClient, lines: List<Text>, catchChance: Double) {
        val config = CatchRateConfig.get()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        
        val lineHeight = 9
        val padding = 4
        val maxWidth = lines.maxOfOrNull { textRenderer.getWidth(it) } ?: 100
        val boxWidth = maxWidth + padding * 2
        val boxHeight = lines.size * lineHeight + padding * 2
        
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        drawContext.fill(x, y, x + boxWidth, y + boxHeight, 0xDD000000.toInt())
        
        val borderColor = getChanceColor(catchChance) or 0xFF000000.toInt()
        drawContext.drawHorizontalLine(x, x + boxWidth - 1, y, borderColor)
        drawContext.drawHorizontalLine(x, x + boxWidth - 1, y + boxHeight - 1, borderColor)
        drawContext.drawVerticalLine(x, y, y + boxHeight - 1, borderColor)
        drawContext.drawVerticalLine(x + boxWidth - 1, y, y + boxHeight - 1, borderColor)
        
        var lineY = y + padding
        for (text in lines) {
            drawContext.drawTextWithShadow(textRenderer, text, x + padding, lineY, 0xFFFFFF)
            lineY += lineHeight
        }
    }
    
    private fun getOpponentPokemon(battle: ClientBattle): ClientBattlePokemon? {
        return try { battle.side2.activeClientBattlePokemon.firstOrNull()?.battlePokemon } catch (e: Exception) { null }
    }
    
    private fun isPokeball(itemStack: ItemStack): Boolean {
        if (itemStack.isEmpty) return false
        val itemId = itemStack.item.toString().lowercase()
        return itemId.contains("_ball") && !itemId.contains("snowball") && !itemId.contains("slimeball") && !itemId.contains("fireball") && !itemId.contains("magma_ball")
    }
    
    private fun getBallId(itemStack: ItemStack): String = itemStack.item.toString().substringAfter("{").substringBefore("}").trim()
    
    private fun formatBallName(name: String): String = name.replace("_", " ").replace("cobblemon:", "").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    
    private fun getChanceFormatting(percentage: Double): Formatting = when { percentage >= 75.0 -> Formatting.GREEN; percentage >= 50.0 -> Formatting.YELLOW; percentage >= 25.0 -> Formatting.GOLD; else -> Formatting.RED }
    
    private fun getChanceColor(percentage: Double): Int = when { percentage >= 75.0 -> 0x55FF55; percentage >= 50.0 -> 0xFFFF55; percentage >= 25.0 -> 0xFFAA00; else -> 0xFF5555 }
}
