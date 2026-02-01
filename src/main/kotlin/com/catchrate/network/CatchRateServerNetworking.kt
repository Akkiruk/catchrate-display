package com.catchrate.network

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
            val ballName = formatBallName(ballId.path)
            
            if (pokeBall.catchRateModifier.isGuaranteed()) {
                sendResponse(player, buildGuaranteedResponse(request.pokemonUuid, pokemon, ballName))
                return
            }
            
            val currentHp = pokemon.currentHealth.toFloat()
            val maxHp = pokemon.maxHealth.toFloat()
            val hpPercent = currentHp / maxHp * 100.0
            val baseCatchRate = pokemon.form.catchRate.toFloat()
            
            val modifier = pokeBall.catchRateModifier
            var ballIsValid = modifier.isValid(player, pokemon)
            var ballMultiplier = if (ballIsValid) modifier.value(player, pokemon) else 1F
            
            // Manual Love Ball override - Cobblemon API may not work correctly in all cases
            val playerActor = battle.actors.find { it.isForPlayer(player) }
            if (ballId.path.lowercase() == "love_ball") {
                CatchRateDisplayMod.debug("Love Ball: API returned isValid=$ballIsValid, multiplier=$ballMultiplier")
                val loveBallResult = checkLoveBallConditionInBattle(pokemon, player)
                CatchRateDisplayMod.debug("Love Ball: Manual check result = ${loveBallResult.first}, reason = ${loveBallResult.second}")
                if (loveBallResult.first && ballMultiplier < 7F) {
                    // Manual check passed but API returned wrong value - override
                    ballMultiplier = 8F
                    ballIsValid = true
                    CatchRateDisplayMod.debug("Love Ball: OVERRIDE APPLIED - setting to 8x")
                } else if (!loveBallResult.first) {
                    CatchRateDisplayMod.debug("Love Ball: Manual check FAILED - no override")
                } else {
                    CatchRateDisplayMod.debug("Love Ball: API already correct (${ballMultiplier}x) - no override needed")
                }
            }
            
            val (conditionMet, conditionDesc) = getBallConditionInfo(
                ballId.path, ballMultiplier, ballIsValid, battle.turn, pokemon, player
            )
            
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
    
    /**
     * Check Love Ball condition: requires same species AND opposite gender.
     * Checks the player's battle party from the given battle.
     * Returns (conditionMet, description).
     */
    private fun checkLoveBallConditionInBattle(
        wildPokemon: Pokemon,
        player: ServerPlayerEntity
    ): Pair<Boolean, String> {
        CatchRateDisplayMod.debug("=== LOVE BALL CHECK START ===")
        
        val battle = BattleRegistry.getBattleByParticipatingPlayer(player)
        if (battle == null) {
            CatchRateDisplayMod.debug("Love Ball: Player not in battle")
            return false to "Not in battle"
        }
        
        val playerActor = battle.actors.find { it.isForPlayer(player) }
        if (playerActor == null) {
            CatchRateDisplayMod.debug("Love Ball: Could not find player actor in battle")
            return false to "No party found"
        }
        
        val wildSpecies = wildPokemon.species.resourceIdentifier.toString()
        val wildGender = wildPokemon.gender
        
        CatchRateDisplayMod.debug("Love Ball: Wild Pokemon = $wildSpecies, Gender = $wildGender")
        
        // Genderless Pokemon cannot trigger Love Ball
        if (wildGender == Gender.GENDERLESS) {
            CatchRateDisplayMod.debug("Love Ball: Wild Pokemon is genderless - FAIL")
            return false to "Wild Pokémon is genderless"
        }
        
        // Check each Pokemon in the player's party
        val partySize = playerActor.pokemonList.count()
        CatchRateDisplayMod.debug("Love Ball: Checking $partySize party members...")
        
        for ((index, battlePokemon) in playerActor.pokemonList.withIndex()) {
            val partyPokemon = battlePokemon.effectedPokemon
            val partySpecies = partyPokemon.species.resourceIdentifier.toString()
            val partyGender = partyPokemon.gender
            
            CatchRateDisplayMod.debug("Love Ball: Party[$index] = $partySpecies, Gender = $partyGender")
            
            // Skip genderless party Pokemon
            if (partyGender == Gender.GENDERLESS) {
                CatchRateDisplayMod.debug("Love Ball: Party[$index] is genderless - skip")
                continue
            }
            
            // Check same species AND opposite gender
            val sameSpecies = wildSpecies == partySpecies
            val oppositeGender = (wildGender == Gender.MALE && partyGender == Gender.FEMALE) ||
                                 (wildGender == Gender.FEMALE && partyGender == Gender.MALE)
            
            CatchRateDisplayMod.debug("Love Ball: Party[$index] sameSpecies=$sameSpecies, oppositeGender=$oppositeGender")
            
            if (sameSpecies && oppositeGender) {
                val genderDesc = if (wildGender == Gender.MALE) "♂" else "♀"
                val partyGenderDesc = if (partyGender == Gender.MALE) "♂" else "♀"
                CatchRateDisplayMod.debug("Love Ball: MATCH FOUND! Wild $genderDesc + Party $partyGenderDesc (${partyPokemon.species.name})")
                return true to "Wild $genderDesc + Party $partyGenderDesc (${partyPokemon.species.name})"
            }
        }
        
        CatchRateDisplayMod.debug("Love Ball: No match found in party - FAIL")
        return false to "No matching species with opposite gender in party"
    }
    
    private fun getBallConditionInfo(
        ballName: String,
        multiplier: Float,
        isValid: Boolean,
        turn: Int,
        pokemon: Pokemon,
        player: ServerPlayerEntity
    ): Pair<Boolean, String> {
        val lower = ballName.lowercase()
        
        return when {
            lower.contains("master") -> true to "Guaranteed capture!"
            lower == "great_ball" -> true to "1.5x catch rate"
            lower == "ultra_ball" -> true to "2x catch rate"
            lower == "timer_ball" -> {
                val effective = multiplier > 1.01f
                effective to "Turn $turn: ${String.format("%.1f", multiplier)}x (increases each turn, max 4x)"
            }
            lower == "quick_ball" -> {
                val effective = turn == 1
                effective to if (effective) "5x on first turn!" else "Only effective on turn 1"
            }
            lower == "dusk_ball" -> {
                val effective = multiplier > 1.01f
                effective to if (effective) "${String.format("%.1f", multiplier)}x in darkness" else "Only effective in dark areas"
            }
            lower == "dive_ball" -> {
                val effective = multiplier > 1.01f
                effective to if (effective) "3.5x while underwater!" else "Only effective underwater"
            }
            lower == "net_ball" -> {
                val types = listOf(pokemon.primaryType.name, pokemon.secondaryType?.name).filterNotNull()
                val effective = types.any { it.equals("water", true) || it.equals("bug", true) }
                effective to if (effective) "3x for Bug/Water types!" else "Only effective on Bug/Water"
            }
            lower == "nest_ball" -> {
                val effective = pokemon.level < 30
                val mult = if (effective) ((41 - pokemon.level) / 10F).coerceAtLeast(1F) else 1F
                effective to if (effective) "${String.format("%.1f", mult)}x for Lv${pokemon.level}" else "Only effective below Lv30"
            }
            lower == "repeat_ball" -> {
                val effective = multiplier > 1.01f
                effective to if (effective) "3.5x - Already caught this species!" else "Only effective if you've caught this species"
            }
            lower == "love_ball" -> {
                // Use manual check result for more accurate information
                val (effective, detail) = checkLoveBallConditionInBattle(pokemon, player)
                if (effective) {
                    true to "8x - $detail"
                } else {
                    false to detail
                }
            }
            lower == "level_ball" -> {
                val effective = multiplier > 1.01f
                effective to if (effective) "${String.format("%.0f", multiplier)}x - Your Pokémon is higher level!" else "Your Pokémon needs higher level"
            }
            lower == "heavy_ball" -> {
                val weight = pokemon.species.weight / 10f  // Convert to kg
                val effective = multiplier > 1.01f
                effective to if (effective) "${String.format("%.1f", multiplier)}x for ${weight}kg Pokémon" else "Only effective on heavy Pokémon (100kg+)"
            }
            lower == "fast_ball" -> {
                val speed = pokemon.species.baseStats.entries.find { it.key.showdownId.equals("spe", true) }?.value ?: 0
                val effective = speed >= 100
                effective to if (effective) "4x for Speed $speed!" else "Only effective if base Speed ≥100"
            }
            lower == "moon_ball" -> {
                val effective = multiplier > 1.01f
                effective to if (effective) "${String.format("%.1f", multiplier)}x during night!" else "Only effective at night"
            }
            lower == "dream_ball" -> {
                val effective = pokemon.status?.status == Statuses.SLEEP
                effective to if (effective) "4x while asleep!" else "Only effective on sleeping Pokémon"
            }
            lower == "lure_ball" -> {
                val effective = multiplier > 1.01f
                effective to if (effective) "4x for fishing encounter!" else "Only effective on fished Pokémon"
            }
            lower == "beast_ball" -> {
                val effective = multiplier > 1.01f
                effective to if (effective) "5x for Ultra Beast!" else "0.1x penalty (not an Ultra Beast)"
            }
            lower == "safari_ball" -> true to "1.5x outside battle (1x in battle)"
            lower == "sport_ball" -> true to "1.5x catch rate"
            lower == "friend_ball" || lower == "luxury_ball" || lower == "heal_ball" -> {
                true to "1x catch rate (special effect on capture)"
            }
            else -> (multiplier >= 1f) to "1x catch rate"
        }
    }
    
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
    
    private fun formatBallName(path: String): String {
        return path.split("_").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
