package com.catchrate

import com.catchrate.BallMultiplierCalculator.BallContext
import com.catchrate.BallMultiplierCalculator.PartyMember
import com.catchrate.CatchRateConstants.NIGHT_END_TICK
import com.catchrate.CatchRateConstants.NIGHT_START_TICK
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level

/**
 * Factory for creating BallContext objects from different Pokemon sources.
 * Centralizes context building logic that was duplicated across:
 * - BallComparisonCalculator.buildBattleContext()
 * - BallComparisonCalculator.buildWorldContext()
 * - CatchRateServerNetworking inline context building
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
            activeBattler = getActiveBattlerFromClientBattle(battle)
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
        return fromPokemon(pokemon, player, level, inBattle = false, turnCount = 0, activeBattler = null)
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
        activeBattler: PartyMember?
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
            activeBattler = activeBattler
        )
    }
    
    /**
     * Create a BallContext for server-side calculations.
     * Used by CatchRateServerNetworking.
     */
    fun fromServerPokemon(
        pokemon: Pokemon,
        player: ServerPlayer,
        level: Level,
        turnCount: Int,
        activeBattler: PartyMember?
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
            inBattle = true,
            turnCount = turnCount,
            activeBattler = activeBattler
        )
    }
    
    // ==================== HELPER METHODS ====================
    
    private fun getActiveBattlerFromClientBattle(battle: ClientBattle?): PartyMember? {
        if (battle == null) return null
        return try {
            val activePokemon = battle.side1.activeClientBattlePokemon.firstOrNull()?.battlePokemon
            if (activePokemon != null) {
                PartyMember(
                    speciesId = activePokemon.species.resourceIdentifier.toString(),
                    gender = safeParseGender(activePokemon.gender.name)
                )
            } else null
        } catch (e: Exception) {
            CatchRateMod.debug("Could not access active battler: ${e.message}")
            null
        }
    }
    
    private fun safeParseGender(genderName: String): Gender? {
        return try {
            Gender.valueOf(genderName)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun safeGetLabels(species: com.cobblemon.mod.common.pokemon.Species): List<String> {
        return try {
            species.labels.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun getBaseSpeed(baseStats: Map<out Any, Int>): Int {
        return baseStats.entries.find { entry ->
            val key = entry.key
            key.toString().equals("spe", ignoreCase = true) ||
            key.toString().equals("SPEED", ignoreCase = true) ||
            key.toString().contains("speed", ignoreCase = true)
        }?.value ?: 0
    }
}
