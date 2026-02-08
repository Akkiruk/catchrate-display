package com.catchrate.client

import com.catchrate.BallContextFactory
import com.catchrate.BallMultiplierCalculator
import com.catchrate.CatchRateMod
import com.catchrate.CatchRateFormula
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult

/**
 * Client-side calculator that computes catch rates for Pokemon.
 * 
 * Uses:
 * - BallMultiplierCalculator for ball-specific multipliers
 * - BallContextFactory for building context objects
 * - CatchRateFormula for catch rate math (single source of truth)
 * 
 * Handles both in-battle and out-of-battle scenarios.
 */
object BallComparisonCalculator {
    
    private val comparableBalls = listOf(
        "quick_ball", "ultra_ball", "timer_ball", "dusk_ball", "net_ball", "dive_ball",
        "nest_ball", "repeat_ball", "great_ball", "level_ball", "love_ball", "heavy_ball",
        "fast_ball", "moon_ball", "dream_ball", "lure_ball", "friend_ball", "luxury_ball",
        "heal_ball", "premier_ball", "poke_ball", "safari_ball", "sport_ball", "beast_ball"
    )
    
    // Pokemon lookup cache to avoid expensive entity queries every frame
    private var cachedLookedAtPokemon: PokemonEntity? = null
    private var lastPokemonLookupTick = 0L
    private const val POKEMON_LOOKUP_INTERVAL_TICKS = 3L
    private var lookupTickCounter = 0L
    
    data class BallCatchRate(
        val ballName: String,
        val displayName: String,
        val catchRate: Double,
        val multiplier: Double,
        val conditionMet: Boolean,
        val reason: String,
        val isGuaranteed: Boolean = false
    )
    
    /**
     * Calculate catch rates for all balls during battle.
     */
    fun calculateAllBalls(pokemon: ClientBattlePokemon, turnCount: Int, battle: ClientBattle?): List<BallCatchRate> {
        return try {
            calculateAllBallsInternal(pokemon, turnCount, battle)
        } catch (e: Throwable) {
            CatchRateMod.debug("Comparison", "calculateAllBalls failed: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }
    
    private fun calculateAllBallsInternal(pokemon: ClientBattlePokemon, turnCount: Int, battle: ClientBattle?): List<BallCatchRate> {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return emptyList()
        val level = minecraft.level ?: return emptyList()
        
        val ctx = BallContextFactory.fromBattlePokemon(pokemon, turnCount, player, level, battle)
        
        val hpInfo = CatchRateFormula.calculateHpInfo(
            hpValue = pokemon.hpValue,
            maxHpValue = pokemon.maxHp,
            isFlat = pokemon.isHpFlat
        )
        
        val baseCatchRate = pokemon.species.catchRate.toFloat()
        val statusMult = CatchRateFormula.getStatusMultiplier(pokemon.status?.name?.path)
        val levelBonus = CatchRateFormula.getLowLevelBonus(pokemon.level)
        
        return comparableBalls.map { ballId ->
            val result = BallMultiplierCalculator.calculate(ballId, ctx)
            
            // Compute modified rate once per ball, derive percentage (avoids duplicate HP/modifier math x24 balls)
            val modifiedRate = CatchRateFormula.calculateModifiedCatchRate(
                baseCatchRate = baseCatchRate,
                maxHp = hpInfo.maxHp,
                currentHp = hpInfo.currentHp,
                ballMultiplier = result.multiplier,
                statusMultiplier = statusMult,
                levelBonus = levelBonus,
                inBattle = true
            )
            val isFormulaGuaranteed = result.isGuaranteed || CatchRateFormula.isGuaranteedByFormula(modifiedRate)
            val catchChance = CatchRateFormula.modifiedRateToPercentage(modifiedRate)
            
            BallCatchRate(
                ballName = ballId,
                displayName = CatchRateFormula.formatBallName(ballId),
                catchRate = catchChance.toDouble().coerceIn(0.0, 100.0),
                multiplier = result.multiplier.toDouble(),
                conditionMet = result.conditionMet,
                reason = result.reason,
                isGuaranteed = isFormulaGuaranteed
            )
        }.sortedByDescending { it.catchRate }
    }
    
    /**
     * Calculate catch rate for a world Pokemon (out of combat).
     */
    fun calculateForWorldPokemon(entity: PokemonEntity, ballId: String): BallCatchRate? {
        return try {
            calculateForWorldPokemonInternal(entity, ballId)
        } catch (e: Throwable) {
            CatchRateMod.debug("WorldCalc", "calculateForWorldPokemon failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
    
    private fun calculateForWorldPokemonInternal(entity: PokemonEntity, ballId: String): BallCatchRate? {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        
        val pokemon = entity.pokemon
        
        val ctx = BallContextFactory.fromWorldPokemon(entity, player, level)
        
        val result = BallMultiplierCalculator.calculate(ballId, ctx)
        
        val baseCatchRate = pokemon.species.catchRate.toFloat()
        val maxHp = pokemon.maxHealth.toFloat()
        val currentHp = pokemon.currentHealth.toFloat()
        val statusMult = CatchRateFormula.getStatusMultiplier(pokemon.status?.status?.name?.path)
        val levelBonus = CatchRateFormula.getLowLevelBonus(pokemon.level)
        
        val catchChance = CatchRateFormula.calculateCatchPercentage(
            baseCatchRate = baseCatchRate,
            maxHp = maxHp,
            currentHp = currentHp,
            ballMultiplier = result.multiplier,
            statusMultiplier = statusMult,
            levelBonus = levelBonus,
            inBattle = false
        )
        
        CatchRateMod.debugOnChange("WorldCalc",
            "${pokemon.species.name}_${ballId}_${currentHp.toInt()}_${statusMult}",
            "${pokemon.species.name} Lv${pokemon.level} | Ball: $ballId ${result.multiplier}x (${result.reason}) | " +
            "Base: $baseCatchRate | HP: ${currentHp.toInt()}/${maxHp.toInt()} | Status: ${statusMult}x | " +
            "Level bonus: ${levelBonus}x | Out-of-combat: 0.5x | Final: ${String.format("%.1f", catchChance)}%"
        )
        
        val isFormulaGuaranteed = result.isGuaranteed || catchChance >= 100F

        return BallCatchRate(
            ballName = ballId,
            displayName = CatchRateFormula.formatBallName(ballId),
            catchRate = catchChance.toDouble().coerceIn(0.0, 100.0),
            multiplier = result.multiplier.toDouble(),
            conditionMet = result.conditionMet,
            reason = result.reason,
            isGuaranteed = isFormulaGuaranteed
        )
    }
    
    /**
     * Get a WILD Pokemon entity the player is looking at.
     * Cached to avoid expensive entity queries every frame.
     */
    fun getLookedAtPokemon(): PokemonEntity? {
        lookupTickCounter++
        
        if ((lookupTickCounter - lastPokemonLookupTick) < POKEMON_LOOKUP_INTERVAL_TICKS) {
            val cached = cachedLookedAtPokemon
            if (cached != null && cached.isAlive) return cached
        }
        
        lastPokemonLookupTick = lookupTickCounter
        cachedLookedAtPokemon = findLookedAtPokemon()
        return cachedLookedAtPokemon
    }
    
    private fun findLookedAtPokemon(): PokemonEntity? {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return null
        val level = minecraft.level ?: return null
        
        // First try the crosshair target
        val hitResult = minecraft.hitResult
        if (hitResult != null && hitResult.type == HitResult.Type.ENTITY) {
            val entityHit = hitResult as EntityHitResult
            val entity = entityHit.entity
            if (entity is PokemonEntity) {
                val pokemonOwner = entity.pokemon.getOwnerUUID()
                val entityOwner = entity.ownerUUID
                if (pokemonOwner == null && entityOwner == null) return entity
                CatchRateMod.debug("WorldCalc", "Skipping owned Pokemon: ${entity.pokemon.species.name} (pokemonOwner: $pokemonOwner, entityOwner: $entityOwner)")
                return null
            }
        }
        
        // Fallback: Check nearby Pokemon via look direction
        val lookVec = player.lookAngle
        val eyePos = player.eyePosition
        val maxDistance = 8.0
        
        val nearbyPokemon = level.getEntitiesOfClass(
            PokemonEntity::class.java,
            player.boundingBox.inflate(maxDistance)
        ) { it.pokemon.getOwnerUUID() == null && it.ownerUUID == null }
        
        var closestPokemon: PokemonEntity? = null
        var closestDistance = maxDistance
        
        for (pokemon in nearbyPokemon) {
            val toEntity = pokemon.position().add(0.0, pokemon.bbHeight / 2.0, 0.0).subtract(eyePos)
            val distance = toEntity.length()
            if (distance > maxDistance) continue
            
            val dot = lookVec.dot(toEntity.normalize())
            if (dot > 0.95 && distance < closestDistance) {
                closestPokemon = pokemon
                closestDistance = distance
            }
        }
        
        return closestPokemon
    }
}
