package com.catchrate

import com.catchrate.BallMultiplierCalculator.BallContext
import com.catchrate.BallMultiplierCalculator.PartyMember
import com.catchrate.CatchRateConstants.NIGHT_END_TICK
import com.catchrate.CatchRateConstants.NIGHT_START_TICK
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/**
 * Factory for creating BallContext objects from different Pokemon sources.
 * Centralizes context building logic that was duplicated across:
 * - BallComparisonCalculator.buildBattleContext()
 * - BallComparisonCalculator.buildWorldContext()
 * - CatchRateHudRenderer client-side context building
 */
object BallContextFactory {
    
    /**
     * Create a BallContext from a ClientBattlePokemon (client-side battle).
     * Used by BallComparisonCalculator for in-battle calculations.
     */
    fun fromBattlePokemon(
        pokemon: ClientBattlePokemon,
        turnCount: Int,
        player: Player,
        level: Level,
        battle: ClientBattle?
    ): BallContext {
        val species = pokemon.species
        val timeOfDay = level.dayTime % 24000
        val aspects = getPokemonAspectsFromBattle(pokemon)
        
        return BallContext(
            speciesId = species.resourceIdentifier.toString(),
            level = pokemon.level,
            gender = safeParseGender(pokemon.gender.name),
            primaryType = species.primaryType.name,
            secondaryType = species.secondaryType?.name,
            weight = species.weight,
            baseSpeed = getBaseSpeed(species.baseStats),
            labels = safeGetLabels(species),
            statusPath = pokemon.status?.name?.path,
            lightLevel = level.getMaxLocalRawBrightness(player.blockPosition()),
            isNight = timeOfDay in NIGHT_START_TICK..NIGHT_END_TICK,
            moonPhase = level.moonPhase,
            isPlayerUnderwater = player.isUnderWater,
            inBattle = true,
            turnCount = turnCount,
            activeBattler = getActiveBattlerFromClientBattle(battle),
            hasCaughtSpecies = checkHasCaughtSpecies(species.resourceIdentifier),
            pokemonAspects = aspects
        )
    }
    
    /**
     * Create a BallContext from a PokemonEntity (client-side world Pokemon).
     * Used by BallComparisonCalculator for out-of-battle calculations.
     */
    fun fromWorldPokemon(
        entity: PokemonEntity,
        player: Player,
        level: Level
    ): BallContext {
        val pokemon = entity.pokemon
        CatchRateMod.debugThrottled("Context", "fromWorldPokemon: ${pokemon.species.name} UUID=${entity.uuid} | entity.aspects=${entity.aspects}")
        return fromPokemon(
            pokemon, player, level,
            inBattle = false, turnCount = 0, activeBattler = null,
            hasCaughtSpecies = checkHasCaughtSpecies(pokemon.species.resourceIdentifier),
            pokemonAspects = entity.aspects
        )
    }
    
    /**
     * Create a BallContext from a Pokemon object (shared logic).
     * Can be used for both world Pokemon and server-side calculations.
     */
    fun fromPokemon(
        pokemon: Pokemon,
        player: Player,
        level: Level,
        inBattle: Boolean,
        turnCount: Int,
        activeBattler: PartyMember?,
        hasCaughtSpecies: Boolean? = null,
        pokemonAspects: Set<String> = emptySet()
    ): BallContext {
        val species = pokemon.species
        val timeOfDay = level.dayTime % 24000
        
        return BallContext(
            speciesId = species.resourceIdentifier.toString(),
            level = pokemon.level,
            gender = pokemon.gender,
            primaryType = pokemon.primaryType.name,
            secondaryType = pokemon.secondaryType?.name,
            weight = species.weight,
            baseSpeed = getBaseSpeed(species.baseStats),
            labels = safeGetLabels(species),
            statusPath = pokemon.status?.status?.name?.path,
            lightLevel = level.getMaxLocalRawBrightness(player.blockPosition()),
            isNight = timeOfDay in NIGHT_START_TICK..NIGHT_END_TICK,
            moonPhase = level.moonPhase,
            isPlayerUnderwater = player.isUnderWater,
            inBattle = inBattle,
            turnCount = turnCount,
            activeBattler = activeBattler,
            hasCaughtSpecies = hasCaughtSpecies,
            pokemonAspects = pokemonAspects
        )
    }
    

    
    // ==================== HELPER METHODS ====================
    
    /**
     * Check if the player has caught this species using the client-synced Pokédex.
     * Returns null if the check fails (e.g., Pokédex not yet synced).
     */
    private fun checkHasCaughtSpecies(speciesId: ResourceLocation): Boolean? {
        return try {
            val knowledge = CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(speciesId)
            val caught = knowledge == PokedexEntryProgress.CAUGHT
            CatchRateMod.debugThrottled("Pokedex", "$speciesId -> knowledge=$knowledge, caught=$caught")
            caught
        } catch (e: Throwable) {
            CatchRateMod.debug("Pokedex", "Could not check Pokédex for $speciesId: ${e.message}")
            null
        }
    }
    
    /**
     * Check if the player has encountered (or caught) this species.
     * Returns true if knowledge >= ENCOUNTERED, false if NONE, null on error.
     */
    fun isSpeciesKnown(speciesId: ResourceLocation): Boolean? {
        return try {
            val knowledge = CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(speciesId)
            knowledge != PokedexEntryProgress.NONE
        } catch (e: Throwable) {
            null
        }
    }
    
    /**
     * Get Pokemon aspects from ClientBattlePokemon via its FloatingState.
     * The private `aspects` field is mirrored to `state.currentAspects` which is accessible.
     * This includes persistent aspects like "fished" from fishing encounters.
     */
    private fun getPokemonAspectsFromBattle(pokemon: ClientBattlePokemon): Set<String> {
        return try {
            val aspects = pokemon.state.currentAspects
            CatchRateMod.debugThrottled("Aspects", "${pokemon.species.name} battle aspects: $aspects")
            aspects
        } catch (e: Throwable) {
            CatchRateMod.debug("Aspects", "Could not get battle pokemon aspects: ${e.message}")
            emptySet()
        }
    }
    
    private fun getActiveBattlerFromClientBattle(battle: ClientBattle?): PartyMember? {
        if (battle == null) return null
        return try {
            val activePokemon = battle.side1.activeClientBattlePokemon.firstOrNull()?.battlePokemon
            if (activePokemon != null) {
                PartyMember(
                    speciesId = activePokemon.species.resourceIdentifier.toString(),
                    gender = safeParseGender(activePokemon.gender.name),
                    level = activePokemon.level
                )
            } else null
        } catch (e: Throwable) {
            CatchRateMod.debug("Battle", "Could not access active battler: ${e.message}")
            null
        }
    }
    
    private fun safeParseGender(genderName: String): Gender? {
        return try {
            Gender.valueOf(genderName)
        } catch (e: Throwable) {
            null
        }
    }
    
    private fun safeGetLabels(species: com.cobblemon.mod.common.pokemon.Species): List<String> {
        return try {
            species.labels.toList()
        } catch (e: Throwable) {
            emptyList()
        }
    }
    
    private fun getBaseSpeed(baseStats: Map<out Any, Int>): Int {
        // Direct lookup via Cobblemon's Stats enum avoids iterating all stats with string matching
        try {
            val speedStat = com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED
            baseStats[speedStat]?.let { return it }
        } catch (_: Throwable) { /* Fall through to string-based lookup */ }
        // Fallback for compatibility
        return baseStats.entries.find { entry ->
            entry.key.toString().let { k ->
                k.equals("spe", ignoreCase = true) || k.equals("SPEED", ignoreCase = true)
            }
        }?.value ?: 0
    }
}
