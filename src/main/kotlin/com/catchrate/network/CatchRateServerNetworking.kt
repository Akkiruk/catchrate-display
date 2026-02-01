package com.catchrate.network

import com.catchrate.BallMultiplierCalculator
import com.catchrate.BallMultiplierCalculator.BallContext
import com.catchrate.BallMultiplierCalculator.PartyMember
import com.catchrate.CatchRateDisplayMod
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.status.PersistentStatus
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Server-side catch rate calculation with accurate multipliers via Cobblemon API.
 * Uses unified BallMultiplierCalculator for condition descriptions and fallback logic.
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
            val ballName = BallMultiplierCalculator.formatBallName(ballId.path)
            
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
            
            // Build context for unified calculator (for condition info and Love Ball override)
            val playerActor = battle.actors.find { it.isForPlayer(player) }
            val partyPokemon = playerActor?.pokemonList?.map { bp ->
                val p = bp.effectedPokemon
                PartyMember(
                    speciesId = p.species.resourceIdentifier.toString(),
                    gender = p.gender
                )
            } ?: emptyList()
            
            val world = player.world
            val timeOfDay = world.timeOfDay % 24000
            val ctx = BallContext(
                speciesId = pokemon.species.resourceIdentifier.toString(),
                level = pokemon.level,
                gender = pokemon.gender,
                primaryType = pokemon.primaryType.name,
                secondaryType = pokemon.secondaryType?.name,
                weight = pokemon.species.weight,
                baseSpeed = pokemon.species.baseStats.entries.find { it.key.showdownId.equals("spe", true) }?.value ?: 0,
                labels = try { pokemon.species.labels.toList() } catch (e: Exception) { emptyList() },
                statusPath = pokemon.status?.status?.name?.path,
                lightLevel = world.getLightLevel(player.blockPos),
                isNight = timeOfDay in 12000..24000,
                moonPhase = world.moonPhase,
                isPlayerUnderwater = player.isSubmergedInWater,
                inBattle = true,
                turnCount = battle.turn,
                partyPokemon = partyPokemon
            )
            
            // Manual Love Ball override using unified calculator
            if (ballId.path.lowercase() == "love_ball") {
                CatchRateDisplayMod.debug("Love Ball: API returned isValid=$ballIsValid, multiplier=$ballMultiplier")
                val unifiedResult = BallMultiplierCalculator.calculate("love_ball", ctx)
                CatchRateDisplayMod.debug("Love Ball: Unified calc result = ${unifiedResult.conditionMet}, multiplier=${unifiedResult.multiplier}, reason=${unifiedResult.reason}")
                if (unifiedResult.conditionMet && ballMultiplier < 7F) {
                    ballMultiplier = 8F
                    ballIsValid = true
                    CatchRateDisplayMod.debug("Love Ball: OVERRIDE APPLIED - setting to 8x")
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
            
            val status = pokemon.status?.status
            val statusMultiplier = when (status) {
                Statuses.SLEEP, Statuses.FROZEN -> 2.5F
                Statuses.PARALYSIS, Statuses.BURN, Statuses.POISON, Statuses.POISON_BADLY -> 1.5F
                else -> 1F
            }
            
            val lowLevelBonus = if (pokemon.level < 13) {
                max((36 - (2 * pokemon.level)) / 10F, 1F)
            } else 1F
            
            val hpComponent = (3F * maxHp - 2F * currentHp) * baseCatchRate
            var modifiedCatchRate = modifier.behavior(player, pokemon).mutator(hpComponent, ballMultiplier) / (3F * maxHp)
            modifiedCatchRate *= statusMultiplier * lowLevelBonus
            
            val playerHighestLevel = playerActor?.pokemonList?.maxOfOrNull { it.effectedPokemon.level } ?: pokemon.level
            
            if (playerHighestLevel < pokemon.level) {
                modifiedCatchRate *= max(0.1F, min(1F, 1F - ((pokemon.level - playerHighestLevel) / 50F)))
            }
            
            val shakeProbability = when {
                modifiedCatchRate >= 255F -> 65536F
                modifiedCatchRate <= 0F -> 0F
                else -> 65536F / (255F / modifiedCatchRate).pow(0.1875F)
            }
            
            val catchChance = (shakeProbability / 65536F).pow(4F) * 100F
            
            val response = CatchRateResponsePayload(
                pokemonUuid = request.pokemonUuid,
                catchChance = catchChance.toDouble().coerceIn(0.0, 100.0),
                pokemonName = pokemon.species.name,
                pokemonLevel = pokemon.level,
                hpPercent = hpPercent.coerceIn(0.0, 100.0),
                statusEffect = getStatusName(status),
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
        statusEffect = getStatusName(pokemon.status?.status),
        ballName = ballName,
        ballMultiplier = 255.0,
        ballConditionMet = true,
        ballConditionDesc = "Guaranteed capture!",
        statusMultiplier = 1.0,
        lowLevelBonus = 1.0,
        isGuaranteed = true,
        baseCatchRate = pokemon.form.catchRate
    )
    
    private fun getStatusName(status: PersistentStatus?): String {
        return when (status) {
            Statuses.SLEEP -> "Asleep"
            Statuses.FROZEN -> "Frozen"
            Statuses.PARALYSIS -> "Paralyzed"
            Statuses.BURN -> "Burned"
            Statuses.POISON -> "Poisoned"
            Statuses.POISON_BADLY -> "Badly Poisoned"
            else -> "None"
        }
    }
}
