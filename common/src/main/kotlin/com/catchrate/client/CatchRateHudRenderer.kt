package com.catchrate.client

import com.catchrate.BallContextFactory
import com.catchrate.CatchRateBattleMonitor
import com.catchrate.CatchRateCalculator
import com.catchrate.CatchRateCaptureTracker
import com.catchrate.CatchRateConstants.Colors
import com.catchrate.CatchRateDebugLog
import com.catchrate.CatchRateFormula
import com.catchrate.CatchRateKeybinds
import com.catchrate.CatchRateMod
import com.catchrate.CatchRatePredictionReliability
import com.catchrate.CatchRateResult
import com.catchrate.config.CatchRateConfig
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import net.minecraft.client.Minecraft
import net.minecraft.client.DeltaTracker
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack

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
    fun ballComparisonWild() = Component.translatable("catchrate.hud.ball_comparison_wild").string
    fun outOfCombatPenaltyNote() = Component.translatable("catchrate.hud.out_of_combat_penalty_note").string
    fun status(statusPath: String?) = Component.translatable(CatchRateFormula.getStatusTranslationKey(statusPath)).string
    fun unknownPokemon() = Component.translatable("catchrate.hud.unknown_pokemon").string
    fun unknownRate() = Component.translatable("catchrate.hud.unknown_rate").string
    fun notEncountered() = Component.translatable("catchrate.hud.not_encountered").string
}

/**
 * HUD renderer for catch rate display.
 * Platform entrypoints should call render() from their HUD render events.
 */
class CatchRateHudRenderer {
    
    private var lastBattleId: java.util.UUID? = null

    /** Composite fingerprint of every cheap input the battle calculation depends on. */
    private var lastBattleSignature: String? = null
    private var lastBattleCalcAtMs = 0L

    private var cachedClientResult: CatchRateResult? = null

    private var cachedComparison: List<BallComparisonCalculator.BallCatchRate>? = null

    private var cachedWorldComparison: List<BallComparisonCalculator.BallCatchRate>? = null
    private var lastWorldSignature: String? = null
    private var lastWorldCalcAtMs = 0L

    companion object {
        /**
         * Ceiling on how long a cached rate may live even when the signature is unchanged.
         * Covers inputs too expensive to fingerprint every frame (target underwater state,
         * light level, Pokedex sync arriving late, external catch rate buffs expiring).
         */
        private const val MAX_CACHE_AGE_MS = 500L
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
        
        val battle = CobblemonClient.battle
        
        // Handle out-of-combat display
        if (battle == null) {
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
            lastBattleId = battle.battleId
            cachedClientResult = null
            cachedComparison = null
            cachedWorldComparison = null
            lastBattleSignature = null
        }
        if (!battle.isPvW) {
            CatchRateMod.debugOnChange("battleType", "pvp", "Not PvW battle, HUD hidden")
            return
        }
        
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
        
        checkCacheInvalidation(opponentPokemon, heldItem, battle)
        
        val ballName = getBallId(heldItem).lowercase()
        val showComparison = CatchRateKeybinds.isComparisonHeld
        val targetSpecies = getEffectiveBattleSpecies(opponentPokemon)
        CatchRateMod.debugOnChange("target", "${targetSpecies.name}_${ballName}", 
            "Target: ${targetSpecies.name} Lv${opponentPokemon.level} with $ballName")
        
        if (showComparison) {
            // Block comparison panel for unencountered Pokémon
            if (!hasEncounteredSpecies(targetSpecies.resourceIdentifier)) {
                val result = getClientCalculation(opponentPokemon, heldItem) ?: return
                renderClientModeHud(guiGraphics, minecraft, result, ballName)
                return
            }
            try {
                renderBallComparisonPanel(guiGraphics, minecraft, opponentPokemon, battle)
                return
            } catch (e: Throwable) {
                CatchRateMod.debugOnChange("HudErr", "comparison", "Comparison panel failed, falling back: ${e.javaClass.simpleName}")
            }
        }
        
        val result = getClientCalculation(opponentPokemon, heldItem) ?: return
        renderClientModeHud(guiGraphics, minecraft, result, ballName)
    }
    
    private fun checkCacheInvalidation(pokemon: ClientBattlePokemon, heldItem: ItemStack, battle: ClientBattle) {
        val signature = buildBattleSignature(pokemon, heldItem, battle)
        val now = System.currentTimeMillis()
        val stale = now - lastBattleCalcAtMs >= MAX_CACHE_AGE_MS

        if (signature != lastBattleSignature || stale) {
            lastBattleSignature = signature
            lastBattleCalcAtMs = now
            cachedClientResult = null
            cachedComparison = null
        }
    }

    /**
     * Fingerprint of everything the catch rate depends on that is cheap to read each frame.
     *
     * Notably includes the player's own active Pokemon: Love Ball and Level Ball read it, so
     * switching party members mid-battle has to invalidate the cached rate. Anything too
     * expensive to sample here is covered by the MAX_CACHE_AGE_MS refresh instead.
     */
    private fun buildBattleSignature(pokemon: ClientBattlePokemon, heldItem: ItemStack, battle: ClientBattle): String {
        return try {
            val ally = battle.side1.activeClientBattlePokemon.firstOrNull()?.battlePokemon
            val player = Minecraft.getInstance().player
            buildString {
                append(pokemon.uuid).append('|')
                append(pokemon.species.resourceIdentifier).append('|')
                append(pokemon.level).append('|')
                append(pokemon.hpValue).append('/').append(pokemon.maxHp).append('|')
                append(pokemon.isHpFlat).append('|')
                append(pokemon.status?.name?.path).append('|')
                append(pokemon.state.currentAspects.sorted().joinToString(",")).append('|')
                append(getBallId(heldItem)).append('|')
                append(CatchRateBattleMonitor.getTurnCount(battle.battleId)).append('|')
                append(CatchRateCaptureTracker.hasConsumedQuickBallBonus(battle.battleId, pokemon.uuid)).append('|')
                append(ally?.uuid).append('/')
                append(ally?.species?.resourceIdentifier).append('/')
                append(ally?.gender?.name).append('/')
                append(ally?.level).append('|')
                append(player?.isUnderWater)
            }
        } catch (e: Throwable) {
            // Unknown state, so use a value that never matches and force a recalculation.
            "sig_error_" + System.nanoTime()
        }
    }

    private fun resetState() {
        lastBattleId = null
        lastBattleSignature = null
        lastBattleCalcAtMs = 0L
        cachedClientResult = null
        cachedComparison = null
        cachedWorldComparison = null
        lastWorldSignature = null
        lastWorldCalcAtMs = 0L
    }
    
    /**
     * Check if a species has been at least encountered in the Pokédex.
     * Returns true if the config is disabled, the Pokédex isn't synced, or the species has been encountered/caught.
     */
    private fun hasEncounteredSpecies(speciesId: net.minecraft.resources.ResourceLocation): Boolean {
        val config = CatchRateConfig.get()
        if (!config.hideUnencounteredInfo) return true
        return try {
            val knowledge = CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(speciesId)
            knowledge != PokedexEntryProgress.NONE
        } catch (e: Throwable) {
            true // fail open — don't block HUD if Pokédex check errors
        }
    }
    
    private fun getClientCalculation(pokemon: ClientBattlePokemon, heldItem: ItemStack): CatchRateResult? {
        val turnCount = CatchRateBattleMonitor.getTurnCount()
        if (cachedClientResult == null) {
            val result = try {
                CatchRateCalculator.calculateCatchRate(pokemon, heldItem, turnCount, null, true)
            } catch (e: Throwable) {
                CatchRateMod.debugOnChange("HudErr", "calc", "Calculation failed: ${e.message}")
                null
            }
            cachedClientResult = result
            
            // Log detailed calculation and track guaranteed predictions
            if (result != null) {
                val pokemonName = getEffectiveBattleSpecies(pokemon).name
                CatchRateDebugLog.logCalculation(pokemonName, pokemon.level, result, inBattle = true)
            }
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
        val isWild: Boolean,
        val isEncountered: Boolean = true,
        val isCatchRateEstimate: Boolean = false,
        val isPredictionReliable: Boolean = true
    )

    private fun getEffectiveBattleSpecies(pokemon: ClientBattlePokemon) =
        CatchRatePredictionReliability.analyzeBattleTarget(pokemon, CobblemonClient.battle).effectiveSpecies ?: pokemon.species
    
    private fun renderClientModeHud(guiGraphics: GuiGraphics, minecraft: Minecraft, result: CatchRateResult, ballName: String) {
        val opponent = cachedClientResult?.let {
            CobblemonClient.battle?.side2?.activeClientBattlePokemon?.firstOrNull()?.battlePokemon
        }
        val species = opponent?.let(::getEffectiveBattleSpecies)
        val encountered = species?.let { hasEncounteredSpecies(it.resourceIdentifier) } ?: true
        val hpMult = (3.0 - 2.0 * result.hpPercentage / 100.0) / 3.0
        renderUnifiedHud(guiGraphics, minecraft, HudData(
            pokemonName = species?.translatedName?.string ?: "???",
            level = opponent?.level ?: 0,
            catchPercentage = result.percentage,
            isGuaranteed = result.isGuaranteed,
            hpMultiplier = hpMult,
            statusName = result.statusName,
            statusMultiplier = result.statusMultiplier,
            ballDisplayName = CatchRateFormula.formatBallName(result.ballName),
            ballId = ballName,
            ballMultiplier = result.ballMultiplier,
            ballConditionMet = result.ballConditionMet,
            ballConditionReason = result.ballConditionReason,
            turnCount = result.turnCount,
            isWild = false,
            isEncountered = encountered,
            isCatchRateEstimate = result.isCatchRateEstimate,
            isPredictionReliable = result.isReliableGuaranteedPrediction
        ))
    }
    
    private fun renderOutOfCombatHud(guiGraphics: GuiGraphics, minecraft: Minecraft, player: LocalPlayer) {
        val heldItem = player.mainHandItem
        if (!isPokeball(heldItem)) return
        
        val pokemonEntity = BallComparisonCalculator.getLookedAtPokemon() ?: return
        
        val config = CatchRateConfig.get()
        val encountered = hasEncounteredSpecies(pokemonEntity.pokemon.species.resourceIdentifier)
        if (CatchRateKeybinds.isComparisonHeld && encountered) {
            try {
                renderWorldComparisonPanel(guiGraphics, minecraft, pokemonEntity)
                return
            } catch (e: Throwable) {
                CatchRateMod.debugOnChange("HudErr", "worldComparison", "World comparison panel failed: ${e.javaClass.simpleName}")
            }
        }
        
        val pokemon = pokemonEntity.pokemon
        val ballName = getBallId(heldItem).lowercase()
        val result = BallComparisonCalculator.calculateForWorldPokemon(pokemonEntity, ballName) ?: return
        
        val hpPercent = if (pokemon.maxHealth > 0) (pokemon.currentHealth.toDouble() / pokemon.maxHealth.toDouble()) * 100.0 else 100.0
        val statusPath = BallContextFactory.getEffectiveStatusPath(pokemonEntity) ?: ""
        
        renderUnifiedHud(guiGraphics, minecraft, HudData(
            pokemonName = pokemon.species.translatedName.string,
            level = pokemon.level,
            catchPercentage = result.catchRate,
            isGuaranteed = result.isGuaranteed,
            hpMultiplier = (3.0 - 2.0 * hpPercent / 100.0) / 3.0,
            statusName = statusPath,
            statusMultiplier = CatchRateFormula.getStatusMultiplier(statusPath).toDouble(),
            ballDisplayName = CatchRateFormula.formatBallName(result.ballName),
            ballId = ballName,
            ballMultiplier = result.multiplier,
            ballConditionMet = result.conditionMet,
            ballConditionReason = result.reason,
            turnCount = 0,
            isWild = true,
            isEncountered = encountered,
            isCatchRateEstimate = result.isCatchRateEstimate,
            isPredictionReliable = result.isPredictionReliable
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
        
        // Obfuscated HUD for unencountered Pokémon
        if (!data.isEncountered) {
            renderObfuscatedHud(guiGraphics, minecraft, data)
            return
        }
        
        val nameText = "${data.pokemonName} Lv${data.level}"
        val wildText = if (data.isWild) HudTranslations.wild() else null
        val hpText = "${HudTranslations.hp()} ${String.format("%.2f", data.hpMultiplier)}x"
        
        // The base catch rate could not be resolved from any source. Show that plainly
        // rather than a percentage derived from a placeholder, which would look like a
        // real (and very low) chance. See SpeciesCatchRateCache for the resolution order.
        val rateUnknown = data.isCatchRateEstimate

        // Don't claim GUARANTEED when the catch rate is unknown or the client-side
        // target data is not trustworthy (for example, disguised Pokemon).
        val effectiveGuaranteed = data.isGuaranteed && !rateUnknown && data.isPredictionReliable
        val percentText = when {
            rateUnknown -> HudTranslations.unknownRate()
            effectiveGuaranteed -> HudTranslations.guaranteedShort()
            else -> {
                val approx = if (!data.isPredictionReliable) "~" else ""
                "$approx${CatchRateFormula.formatCatchPercentage(data.catchPercentage, false)}%"
            }
        }
        // Bar and panel styling follow the same rule: nothing to fill when nothing is known.
        val displayPercentage = if (rateUnknown) 0.0 else data.catchPercentage
        
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
        
        HudDrawing.drawStyledPanel(guiGraphics, x, y, boxWidth, boxHeight, displayPercentage, isWild = data.isWild)
        
        // Header: Pokemon name + level, optional WILD tag
        guiGraphics.drawString(font, nameText, x + 6, y + 4, Colors.TEXT_WHITE)
        if (wildText != null) {
            guiGraphics.drawString(font, wildText, x + boxWidth - font.width(wildText) - 6, y + 4, Colors.TEXT_WILD_RED)
        }
        
        // Catch bar + percentage
        val barY = y + 16
        if (effectiveGuaranteed) {
            HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, 100.0, true)
            guiGraphics.drawString(font, percentText, x + 6, barY + 12, Colors.TEXT_GREEN)
        } else {
            HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, displayPercentage, false)
            val percentColor = if (rateUnknown) Colors.TEXT_ORANGE else HudDrawing.getChanceColorInt(displayPercentage)
            guiGraphics.drawString(font, percentText, x + 6, barY + 12, percentColor)
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
    
    /**
     * Renders a minimal obfuscated HUD for Pokémon the player hasn't encountered yet.
     * Shows "???" for name/level/catch rate and a "Not yet encountered" note.
     */
    private fun renderObfuscatedHud(guiGraphics: GuiGraphics, minecraft: Minecraft, data: HudData) {
        val config = CatchRateConfig.get()
        val font = minecraft.font
        val screenWidth = minecraft.window.guiScaledWidth
        val screenHeight = minecraft.window.guiScaledHeight
        
        val unknownName = "${HudTranslations.unknownPokemon()} Lv?"
        val wildText = if (data.isWild) HudTranslations.wild() else null
        val unknownRate = HudTranslations.unknownRate()
        val encounterNote = HudTranslations.notEncountered()
        
        val textWidths = mutableListOf(
            font.width(unknownName) + (if (wildText != null) font.width(" $wildText") + 8 else 0),
            font.width(unknownRate),
            font.width(encounterNote)
        )
        val boxWidth = (textWidths.maxOrNull() ?: 100) + 16
        val boxHeight = 52
        val (x, y) = config.getPosition(screenWidth, screenHeight, boxWidth, boxHeight)
        
        // Draw panel with 0% catch chance styling (unknown = red border)
        HudDrawing.drawStyledPanel(guiGraphics, x, y, boxWidth, boxHeight, 0.0, isWild = data.isWild)
        
        // Header: ??? Lv?
        guiGraphics.drawString(font, unknownName, x + 6, y + 4, Colors.TEXT_DARK_GRAY)
        if (wildText != null) {
            guiGraphics.drawString(font, wildText, x + boxWidth - font.width(wildText) - 6, y + 4, Colors.TEXT_WILD_RED)
        }
        
        // Empty catch bar
        val barY = y + 16
        HudDrawing.drawCatchBar(guiGraphics, x + 6, barY, boxWidth - 12, 0.0, false)
        guiGraphics.drawString(font, unknownRate, x + 6, barY + 12, Colors.TEXT_DARK_GRAY)
        
        // "Not yet encountered" note
        guiGraphics.drawString(font, encounterNote, x + 6, barY + 26, Colors.TEXT_ORANGE)
    }
    
    private fun renderBallComparisonPanel(guiGraphics: GuiGraphics, minecraft: Minecraft, pokemon: ClientBattlePokemon, battle: ClientBattle) {
        val turnCount = CatchRateBattleMonitor.getTurnCount(battle.battleId)
        // Invalidation is driven by checkCacheInvalidation(), which shares one signature with
        // the single-ball result so both panels refresh on exactly the same triggers.
        if (cachedComparison == null) {
            cachedComparison = BallComparisonCalculator.calculateAllBalls(pokemon, turnCount, battle)
        }
        val comparison = cachedComparison ?: return
        renderComparisonPanelContent(guiGraphics, minecraft, comparison, HudTranslations.ballComparison(turnCount), showPenaltyNote = false)
    }
    
    private fun renderWorldComparisonPanel(guiGraphics: GuiGraphics, minecraft: Minecraft, entity: com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
        val signature = buildWorldSignature(entity)
        val now = System.currentTimeMillis()
        val stale = now - lastWorldCalcAtMs >= MAX_CACHE_AGE_MS
        if (cachedWorldComparison == null || signature != lastWorldSignature || stale) {
            cachedWorldComparison = BallComparisonCalculator.calculateAllBallsForWorld(entity)
            lastWorldSignature = signature
            lastWorldCalcAtMs = now
        }
        val comparison = cachedWorldComparison ?: return
        renderComparisonPanelContent(guiGraphics, minecraft, comparison, HudTranslations.ballComparisonWild(), showPenaltyNote = true)
    }
    
    private fun buildWorldSignature(entity: com.cobblemon.mod.common.entity.pokemon.PokemonEntity): String {
        return try {
            val pokemon = entity.pokemon
            val player = Minecraft.getInstance().player
            buildString {
                append(entity.uuid).append('|')
                append(pokemon.species.resourceIdentifier).append('|')
                append(pokemon.level).append('|')
                append(pokemon.currentHealth).append('/').append(pokemon.maxHealth).append('|')
                append(BallContextFactory.getEffectiveStatusPath(entity)).append('|')
                append(entity.aspects.sorted().joinToString(",")).append('|')
                append(entity.isUnderWater).append('|')
                append(player?.isUnderWater)
            }
        } catch (e: Throwable) {
            "sig_error_" + System.nanoTime()
        }
    }

    private fun renderComparisonPanelContent(
        guiGraphics: GuiGraphics,
        minecraft: Minecraft,
        comparison: List<BallComparisonCalculator.BallCatchRate>,
        headerText: String,
        showPenaltyNote: Boolean
    ) {
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
            val effectiveGuaranteed = ball.isGuaranteed && ball.isPredictionReliable && !ball.isCatchRateEstimate
            // Ball multipliers stay accurate even when the species' base rate is unknown,
            // so the multiplier column is still worth showing next to an unknown percentage.
            val rateText = if (ball.isCatchRateEstimate) {
                Component.literal(HudTranslations.unknownRate()).withStyle(ChatFormatting.GRAY)
            } else {
                val approx = if (!ball.isPredictionReliable) "~" else ""
                Component.literal("$approx${CatchRateFormula.formatCatchPercentage(ball.catchRate, effectiveGuaranteed)}%")
                    .withStyle(HudDrawing.getChanceFormatting(ball.catchRate))
            }
            
            val multColor = HudDrawing.getBallMultiplierFormatting(ball.multiplier)
            val multText = Component.literal("${String.format("%.1f", ball.multiplier)}x").withStyle(multColor)
            
            lines.add(Triple(ballText, rateText, multText))
        }
        
        val col1Width = lines.maxOfOrNull { font.width(it.first) } ?: 100
        val col2Width = 45
        val col3Width = 35
        val contentWidth = col1Width + col2Width + col3Width + padding * 4
        val keyName = CatchRateKeybinds.comparisonKeyName
        val header = Component.literal(headerText).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        val headerWidth = font.width(header) + padding * 2
        val footerText = HudTranslations.releaseToClose("[${keyName}]")
        val penaltyNote = if (showPenaltyNote) HudTranslations.outOfCombatPenaltyNote() else null
        val footerWidth = font.width(footerText) + padding * 2
        val penaltyWidth = if (penaltyNote != null) font.width(penaltyNote) + padding * 2 else 0
        val boxWidth = maxOf(contentWidth, headerWidth, footerWidth, penaltyWidth)
        val footerHeight = if (showPenaltyNote) 28 else 18
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
        
        if (penaltyNote != null) {
            val footerY = y + boxHeight - padding - 19
            guiGraphics.hLine(x + padding, x + boxWidth - padding, footerY - 4, Colors.BAR_BORDER)
            guiGraphics.drawString(font, penaltyNote, x + padding, footerY, Colors.TEXT_ORANGE)
            guiGraphics.drawString(font, footerText, x + padding, footerY + 10, Colors.TEXT_DARK_GRAY)
        } else {
            val footerY = y + boxHeight - padding - 9
            guiGraphics.hLine(x + padding, x + boxWidth - padding, footerY - 4, Colors.BAR_BORDER)
            guiGraphics.drawString(font, footerText, x + padding, footerY, Colors.TEXT_DARK_GRAY)
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private fun getOpponentPokemon(battle: ClientBattle): ClientBattlePokemon? {
        return try { battle.side2.activeClientBattlePokemon.firstOrNull()?.battlePokemon } catch (e: Throwable) { null }
    }
    
    private fun isPokeball(itemStack: ItemStack): Boolean {
        return CatchRateCalculator.getBallIdFromItem(itemStack) != null
    }
    
    private fun getBallId(itemStack: ItemStack): String {
        return CatchRateCalculator.getBallIdFromItem(itemStack) ?: ""
    }
}
