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
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.World

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
     * 
     * @param pokemon The wild Pokemon in battle
     * @param turnCount Current battle turn
     * @param player The player entity
     * @param world The world
     * @param battle The client battle (for getting active battler)
     */
    fun fromBattlePokemon(
        pokemon: ClientBattlePokemon,
        turnCount: Int,
        player: PlayerEntity,
        world: World,
        battle: ClientBattle?
    ): BallContext {
        val species = pokemon.species
        val timeOfDay = world.timeOfDay % 24000
        
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
            lightLevel = world.getLightLevel(player.blockPos),
            isNight = timeOfDay in NIGHT_START_TICK..NIGHT_END_TICK,
            moonPhase = world.moonPhase,
            isPlayerUnderwater = player.isSubmergedInWater,
            inBattle = true,
            turnCount = turnCount,
            activeBattler = getActiveBattlerFromClientBattle(battle)
        )
    }
    
    /**
     * Create a BallContext from a PokemonEntity (client-side world Pokemon).
     * Used by BallComparisonCalculator for out-of-battle calculations.
     * 
     * @param entity The wild Pokemon entity in the world
     * @param player The player entity
     * @param world The world
     */
    fun fromWorldPokemon(
        entity: PokemonEntity,
        player: PlayerEntity,
        world: World
    ): BallContext {
        val pokemon = entity.pokemon
        return fromPokemon(pokemon, player, world, inBattle = false, turnCount = 0, activeBattler = null)
    }
    
    /**
     * Create a BallContext from a Pokemon object (shared logic).
     * Can be used for both world Pokemon and server-side calculations.
     * 
     * @param pokemon The Pokemon object
     * @param player The player entity
     * @param world The world
     * @param inBattle Whether the Pokemon is in battle
     * @param turnCount Current battle turn (0 if not in battle)
     * @param activeBattler The player's active battler (for Love Ball)
     */
    fun fromPokemon(
        pokemon: Pokemon,
        player: PlayerEntity,
        world: World,
        inBattle: Boolean,
        turnCount: Int,
        activeBattler: PartyMember?
    ): BallContext {
        val species = pokemon.species
        val timeOfDay = world.timeOfDay % 24000
        
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
            lightLevel = world.getLightLevel(player.blockPos),
            isNight = timeOfDay in NIGHT_START_TICK..NIGHT_END_TICK,
            moonPhase = world.moonPhase,
            isPlayerUnderwater = player.isSubmergedInWater,
            inBattle = inBattle,
            turnCount = turnCount,
            activeBattler = activeBattler
        )
    }
    
    /**
     * Create a BallContext for server-side calculations.
     * Used by CatchRateServerNetworking.
     * 
     * @param pokemon The Pokemon object from the battle
     * @param player The server player entity
     * @param world The world
     * @param turnCount Current battle turn
     * @param activeBattler The player's active battler (for Love Ball)
     */
    fun fromServerPokemon(
        pokemon: Pokemon,
        player: ServerPlayerEntity,
        world: World,
        turnCount: Int,
        activeBattler: PartyMember?
    ): BallContext {
        val species = pokemon.species
        val timeOfDay = world.timeOfDay % 24000
        
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
            lightLevel = world.getLightLevel(player.blockPos),
            isNight = timeOfDay in NIGHT_START_TICK..NIGHT_END_TICK,
            moonPhase = world.moonPhase,
            isPlayerUnderwater = player.isSubmergedInWater,
            inBattle = true,
            turnCount = turnCount,
            activeBattler = activeBattler
        )
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Get the player's active battler from a client battle.
     * Returns null if no active battler found.
     */
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
            CatchRateDisplayMod.debug("Could not access active battler: ${e.message}")
            null
        }
    }
    
    /**
     * Safely parse a gender string to Gender enum.
     */
    private fun safeParseGender(genderName: String): Gender? {
        return try {
            Gender.valueOf(genderName)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Safely get labels from a species.
     */
    private fun safeGetLabels(species: com.cobblemon.mod.common.pokemon.Species): List<String> {
        return try {
            species.labels.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get base speed from a stats map.
     * Cobblemon stores stats with Stat enum keys that have showdownId property.
     */
    private fun getBaseSpeed(baseStats: Map<out Any, Int>): Int {
        return baseStats.entries.find { entry ->
            val key = entry.key
            key.toString().equals("spe", ignoreCase = true) ||
            key.toString().equals("SPEED", ignoreCase = true) ||
            key.toString().contains("speed", ignoreCase = true)
        }?.value ?: 0
    }
}
