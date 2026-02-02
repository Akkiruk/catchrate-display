package com.catchrate.network

import com.catchrate.BallContextFactory
import com.catchrate.BallMultiplierCalculator
import com.catchrate.BallMultiplierCalculator.PartyMember
import com.catchrate.CatchRateConstants
import com.catchrate.CatchRateDisplayMod
import com.catchrate.CatchRateFormula
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.status.PersistentStatus
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Server-side catch rate calculation with accurate multipliers via Cobblemon API.
 * 
 * Uses:
 * - CatchRateFormula for catch rate math (single source of truth)
 * - BallMultiplierCalculator for condition descriptions and fallback logic
 * - BallContextFactory for building context objects
 */
object CatchRateServerNetworking {
    
    fun initialize() {
        try {
            PayloadTypeRegistry.playC2S().register(CatchRateRequestPayload.ID, CatchRateRequestPayload.CODEC)
            PayloadTypeRegistry.playS2C().register(CatchRateResponsePayload.ID, CatchRateResponsePayload.CODEC)
        } catch (e: IllegalArgumentException) {
            // Already registered in single-player
        }
        
        ServerPlayNetworking.registerGlobalReceiver(CatchRateRequestPayload.ID) { payload, context ->
            handleCatchRateRequest(context.player(), payload)
        }
    }
    
    private fun handleCatchRateRequest(player: ServerPlayerEntity, request: CatchRateRequestPayload) {
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
            
            // Get the player's ACTIVE battler (the one currently in play) for Love Ball
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
            val world = player.world
            val ctx = BallContextFactory.fromServerPokemon(pokemon, player, world, battle.turn, activeBattler)
            
            // Ancient balls use DUMMY catchRateModifier in Cobblemon (always 1x)
            // Their throwPower only affects ball travel, not catch rate - no override needed
            
            // Manual Love Ball override using unified calculator
            if (ballId.path.lowercase() == "love_ball") {
                CatchRateDisplayMod.debug("Love Ball: API returned isValid=$ballIsValid, multiplier=$ballMultiplier")
                val unifiedResult = BallMultiplierCalculator.calculate("love_ball", ctx)
                CatchRateDisplayMod.debug("Love Ball: Unified calc result = ${unifiedResult.conditionMet}, multiplier=${unifiedResult.multiplier}, reason=${unifiedResult.reason}")
                if (unifiedResult.conditionMet && ballMultiplier < 7F) {
                    ballMultiplier = unifiedResult.multiplier
                    ballIsValid = true
                    CatchRateDisplayMod.debug("Love Ball: OVERRIDE APPLIED - setting to ${ballMultiplier}x")
                } else if (!unifiedResult.conditionMet) {
                    CatchRateDisplayMod.debug("Love Ball: Check FAILED - no override")
                } else {
                    CatchRateDisplayMod.debug("Love Ball: API already correct (${ballMultiplier}x) - no override needed")
                }
            }
            
            // Get condition description from unified calculator
            val unifiedResult = BallMultiplierCalculator.calculate(ballId.path, ctx)
            val conditionMet = unifiedResult.conditionMet
            val conditionDesc = unifiedResult.reason
            
            // Use CatchRateFormula for status multiplier
            val status = pokemon.status?.status
            val statusMultiplier = getStatusMultiplierFromStatus(status)
            
            // Use CatchRateFormula for level bonus
            val lowLevelBonus = CatchRateFormula.getLowLevelBonus(pokemon.level)
            
            // NOTE: Cobblemon's findHighestThrowerLevel() always returns null for wild battles
            // due to a logic issue in their code, so the level penalty is never applied.
            // We match Cobblemon's actual behavior by not applying level penalty.
            
            // Use Cobblemon's behavior mutator for the HP component, then apply our formula
            val hpComponent = (3F * maxHp - 2F * currentHp) * baseCatchRate
            var modifiedCatchRate = modifier.behavior(player, pokemon).mutator(hpComponent, ballMultiplier) / (3F * maxHp)
            modifiedCatchRate *= statusMultiplier * lowLevelBonus
            
            // Use CatchRateFormula to convert modified rate to percentage
            val catchChance = CatchRateFormula.modifiedRateToPercentage(modifiedCatchRate)
            
            val response = CatchRateResponsePayload(
                pokemonUuid = request.pokemonUuid,
                catchChance = catchChance.toDouble().coerceIn(0.0, 100.0),
                pokemonName = pokemon.species.name,
                pokemonLevel = pokemon.level,
                hpPercent = hpPercent.coerceIn(0.0, 100.0),
                statusEffect = CatchRateFormula.getStatusDisplayName(status?.name?.path),
                ballName = ballName,
                ballMultiplier = ballMultiplier.toDouble(),
                ballConditionMet = conditionMet,
                ballConditionDesc = conditionDesc,
                statusMultiplier = statusMultiplier.toDouble(),
                lowLevelBonus = lowLevelBonus.toDouble(),
                isGuaranteed = false,
                baseCatchRate = baseCatchRate.toInt()
            )
            
            sendResponse(player, response)
            CatchRateDisplayMod.debug("Server sent: ${pokemon.species.name} Lv${pokemon.level}, $ballName ${ballMultiplier}x = ${String.format("%.1f", catchChance)}%")
            
        } catch (e: Exception) {
            CatchRateDisplayMod.LOGGER.error("Error calculating catch rate", e)
        }
    }
    
    /**
     * Get status multiplier from Cobblemon's PersistentStatus.
     * This handles the server-side Statuses enum properly.
     */
    private fun getStatusMultiplierFromStatus(status: PersistentStatus?): Float {
        return when (status) {
            Statuses.SLEEP, Statuses.FROZEN -> CatchRateConstants.STATUS_SLEEP_FROZEN_MULT
            Statuses.PARALYSIS, Statuses.BURN, Statuses.POISON, Statuses.POISON_BADLY -> CatchRateConstants.STATUS_PARA_BURN_POISON_MULT
            else -> CatchRateConstants.STATUS_NONE_MULT
        }
    }
    
    private fun parseBallId(ballItemId: String): Identifier {
        return try {
            val parts = ballItemId.split(":")
            if (parts.size == 2) Identifier.of(parts[0], parts[1])
            else Identifier.of("cobblemon", ballItemId)
        } catch (e: Exception) {
            Identifier.of("cobblemon", "poke_ball")
        }
    }
    
    private fun sendResponse(player: ServerPlayerEntity, response: CatchRateResponsePayload) {
        ServerPlayNetworking.send(player, response)
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
