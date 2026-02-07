package com.catchrate.network

import com.catchrate.BallContextFactory
import com.catchrate.BallMultiplierCalculator
import com.catchrate.BallMultiplierCalculator.PartyMember
import com.catchrate.CatchRateConstants
import com.catchrate.CatchRateMod
import com.catchrate.CatchRateFormula
import com.catchrate.platform.PlatformHelper
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.status.PersistentStatus
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceLocation
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Server-side catch rate calculation with accurate multipliers via Cobblemon API.
 * 
 * Platform-agnostic: uses PlatformHelper for sending packets.
 * Platform entrypoints should register a receiver that calls handleCatchRateRequest().
 */
object CatchRateServerNetworking {
    
    /**
     * Handle a catch rate request from a client player.
     * Called by platform-specific packet receiver.
     */
    fun handleCatchRateRequest(player: ServerPlayer, request: CatchRateRequestPayload) {
        try {
            val battle = BattleRegistry.getBattleByParticipatingPlayer(player) ?: return
            
            val pokemon = battle.activePokemon
                .find { it.battlePokemon?.uuid == request.pokemonUuid }
                ?.battlePokemon?.effectedPokemon ?: return
            
            val ballId = parseBallId(request.ballItemId)
            val pokeBall = PokeBalls.getPokeBall(ballId) ?: return
            val ballName = CatchRateFormula.formatBallName(ballId.path)
            
            if (pokeBall.catchRateModifier.isGuaranteed()) {
                sendResponse(player, buildGuaranteedResponse(request.pokemonUuid, pokemon, ballName))
                return
            }
            
            val currentHp = pokemon.currentHealth.toFloat()
            val maxHp = pokemon.maxHealth.toFloat()
            val hpPercent = currentHp / maxHp * 100.0
            val baseCatchRate = pokemon.form.catchRate.toFloat()
            
            // Get multiplier from Cobblemon API
            val modifier = pokeBall.catchRateModifier
            var ballIsValid = modifier.isValid(player, pokemon)
            var ballMultiplier = if (ballIsValid) modifier.value(player, pokemon) else 1F
            
            // Get the player's ACTIVE battler for Love Ball
            val playerActor = battle.actors.find { it.isForPlayer(player) }
            val activeBattler = playerActor?.activePokemon?.firstOrNull()?.let { active ->
                val p = active.battlePokemon?.effectedPokemon
                if (p != null) {
                    PartyMember(
                        speciesId = p.species.resourceIdentifier.toString(),
                        gender = p.gender
                    )
                } else null
            }
            
            // Use BallContextFactory for context building
            val level = player.level()
            val ctx = BallContextFactory.fromServerPokemon(pokemon, player, level, battle.turn, activeBattler)
            
            // Manual Love Ball override using unified calculator
            if (ballId.path.lowercase() == "love_ball") {
                CatchRateMod.debug("Love Ball: API returned isValid=$ballIsValid, multiplier=$ballMultiplier")
                val unifiedResult = BallMultiplierCalculator.calculate("love_ball", ctx)
                CatchRateMod.debug("Love Ball: Unified calc result = ${unifiedResult.conditionMet}, multiplier=${unifiedResult.multiplier}, reason=${unifiedResult.reason}")
                if (unifiedResult.conditionMet && ballMultiplier < 7F) {
                    ballMultiplier = unifiedResult.multiplier
                    ballIsValid = true
                    CatchRateMod.debug("Love Ball: OVERRIDE APPLIED - setting to ${ballMultiplier}x")
                } else if (!unifiedResult.conditionMet) {
                    CatchRateMod.debug("Love Ball: Check FAILED - no override")
                } else {
                    CatchRateMod.debug("Love Ball: API already correct (${ballMultiplier}x) - no override needed")
                }
            }
            
            // Get condition description from unified calculator
            val unifiedResult = BallMultiplierCalculator.calculate(ballId.path, ctx)
            val conditionMet = unifiedResult.conditionMet
            val conditionDesc = unifiedResult.reason
            
            // ============================================================
            // REPLICATE COBBLEMON'S EXACT FORMULA FROM CobblemonCaptureCalculator
            // ============================================================
            
            val status = pokemon.status?.status
            val bonusStatus = getStatusMultiplierFromStatus(status)
            val bonusLevel = if (pokemon.level < 13) max((36 - (2 * pokemon.level)) / 10, 1) else 1
            val inBattleModifier = 1F
            val darkGrass = 1F
            
            var modifiedCatchRate = modifier
                .behavior(player, pokemon)
                .mutator((3F * maxHp - 2F * currentHp) * darkGrass * baseCatchRate * inBattleModifier, ballMultiplier) / (3F * maxHp)
            modifiedCatchRate *= bonusStatus * bonusLevel
            
            val shakeProbability = (65536F / (255F / modifiedCatchRate).pow(0.1875F)).roundToInt()
            val isFormulaGuaranteed = shakeProbability >= 65537
            
            val catchChance: Float
            if (isFormulaGuaranteed) {
                catchChance = 100F
            } else {
                val perShakeProb = shakeProbability.toFloat() / 65537F
                catchChance = (perShakeProb.pow(4) * 100F).coerceIn(0F, 100F)
            }
            
            CatchRateMod.debug("=== CATCH RATE DEBUG (Cobblemon-exact) ===")
            CatchRateMod.debug("Pokemon: ${pokemon.species.name} Lv${pokemon.level} | HP: $currentHp/$maxHp | Base: $baseCatchRate")
            CatchRateMod.debug("Ball: ${ballId.path} ${ballMultiplier}x (valid=$ballIsValid) | Status: ${status?.name?.path ?: "none"} ${bonusStatus}x | LvBonus: $bonusLevel")
            CatchRateMod.debug("ModifiedRate: $modifiedCatchRate | Shake: $shakeProbability/65537 (guaranteed=$isFormulaGuaranteed) | Catch: $catchChance%")
            
            val response = CatchRateResponsePayload(
                pokemonUuid = request.pokemonUuid,
                catchChance = catchChance.toDouble(),
                pokemonName = pokemon.species.name,
                pokemonLevel = pokemon.level,
                hpPercent = hpPercent.coerceIn(0.0, 100.0),
                statusEffect = CatchRateFormula.getStatusDisplayName(status?.name?.path),
                ballName = ballName,
                ballMultiplier = ballMultiplier.toDouble(),
                ballConditionMet = conditionMet,
                ballConditionDesc = conditionDesc,
                statusMultiplier = bonusStatus.toDouble(),
                lowLevelBonus = bonusLevel.toDouble(),
                isGuaranteed = isFormulaGuaranteed,
                baseCatchRate = baseCatchRate.toInt()
            )
            
            sendResponse(player, response)
            CatchRateMod.debug("Server sent: ${pokemon.species.name} Lv${pokemon.level}, $ballName ${ballMultiplier}x = ${String.format("%.1f", catchChance)}%")
            
        } catch (e: Exception) {
            CatchRateMod.LOGGER.error("Error calculating catch rate", e)
        }
    }
    
    private fun getStatusMultiplierFromStatus(status: PersistentStatus?): Float {
        return when (status) {
            Statuses.SLEEP, Statuses.FROZEN -> CatchRateConstants.STATUS_SLEEP_FROZEN_MULT
            Statuses.PARALYSIS, Statuses.BURN, Statuses.POISON, Statuses.POISON_BADLY -> CatchRateConstants.STATUS_PARA_BURN_POISON_MULT
            else -> CatchRateConstants.STATUS_NONE_MULT
        }
    }
    
    private fun parseBallId(ballItemId: String): ResourceLocation {
        return try {
            val parts = ballItemId.split(":")
            if (parts.size == 2) ResourceLocation.fromNamespaceAndPath(parts[0], parts[1])
            else ResourceLocation.fromNamespaceAndPath("cobblemon", ballItemId)
        } catch (e: Exception) {
            ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")
        }
    }
    
    private fun sendResponse(player: ServerPlayer, response: CatchRateResponsePayload) {
        PlatformHelper.sendToPlayer(player, response)
    }
    
    private fun buildGuaranteedResponse(pokemonUuid: java.util.UUID, pokemon: Pokemon, ballName: String) = CatchRateResponsePayload(
        pokemonUuid = pokemonUuid,
        catchChance = 100.0,
        pokemonName = pokemon.species.name,
        pokemonLevel = pokemon.level,
        hpPercent = pokemon.currentHealth.toDouble() / pokemon.maxHealth * 100.0,
        statusEffect = CatchRateFormula.getStatusDisplayName(pokemon.status?.status?.name?.path),
        ballName = ballName,
        ballMultiplier = CatchRateConstants.BALL_GUARANTEED_MULT.toDouble(),
        ballConditionMet = true,
        ballConditionDesc = "Guaranteed capture!",
        statusMultiplier = 1.0,
        lowLevelBonus = 1.0,
        isGuaranteed = true,
        baseCatchRate = pokemon.form.catchRate
    )
}
