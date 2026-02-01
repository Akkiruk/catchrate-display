package com.catchrate.client

import com.catchrate.BallContextFactory
import com.catchrate.BallMultiplierCalculator
import com.catchrate.CatchRateDisplayMod
import com.catchrate.CatchRateFormula
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult

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
    
    // Debug log throttling to prevent spam
    private var lastDebugLogTime = 0L
    private var lastDebugPokemon = ""
    private const val DEBUG_LOG_COOLDOWN_MS = 2000L
    
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
     * Uses the unified BallMultiplierCalculator for ball-specific multipliers.
     * @param pokemon The wild Pokemon being targeted
     * @param turnCount Current battle turn
     * @param battle The client battle (needed for Love Ball to check active battler)
     */
    fun calculateAllBalls(pokemon: ClientBattlePokemon, turnCount: Int, battle: ClientBattle?): List<BallCatchRate> {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return emptyList()
        val world = client.world ?: return emptyList()
        
        // Use BallContextFactory for context building
        val ctx = BallContextFactory.fromBattlePokemon(pokemon, turnCount, player, world, battle)
        
        // Use CatchRateFormula for HP calculation
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
            
            // Use CatchRateFormula for catch chance calculation
            val catchChance = CatchRateFormula.calculateCatchPercentage(
                baseCatchRate = baseCatchRate,
                maxHp = hpInfo.maxHp,
                currentHp = hpInfo.currentHp,
                ballMultiplier = result.multiplier,
                statusMultiplier = statusMult,
                levelBonus = levelBonus,
                inBattle = true
            )
            
            BallCatchRate(
                ballName = ballId,
                displayName = CatchRateFormula.formatBallName(ballId),
                catchRate = catchChance.toDouble().coerceIn(0.0, 100.0),
                multiplier = result.multiplier.toDouble(),
                conditionMet = result.conditionMet,
                reason = result.reason,
                isGuaranteed = result.isGuaranteed
            )
        }.sortedByDescending { it.catchRate }
    }
    
    /**
     * Calculate catch rate for a world Pokemon (out of combat).
     * Uses the unified BallMultiplierCalculator for ball-specific multipliers.
     */
    fun calculateForWorldPokemon(entity: PokemonEntity, ballId: String): BallCatchRate? {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return null
        val world = client.world ?: return null
        
        val pokemon = entity.pokemon
        
        // Use BallContextFactory for context building
        val ctx = BallContextFactory.fromWorldPokemon(entity, player, world)
        
        val result = BallMultiplierCalculator.calculate(ballId, ctx)
        
        val baseCatchRate = pokemon.species.catchRate.toFloat()
        val maxHp = pokemon.maxHealth.toFloat()
        val currentHp = pokemon.currentHealth.toFloat()
        val statusMult = CatchRateFormula.getStatusMultiplier(pokemon.status?.status?.name?.path)
        val levelBonus = CatchRateFormula.getLowLevelBonus(pokemon.level)
        
        // Use CatchRateFormula for catch chance (inBattle=false applies 0.5x penalty)
        val catchChance = CatchRateFormula.calculateCatchPercentage(
            baseCatchRate = baseCatchRate,
            maxHp = maxHp,
            currentHp = currentHp,
            ballMultiplier = result.multiplier,
            statusMultiplier = statusMult,
            levelBonus = levelBonus,
            inBattle = false  // 0.5x penalty applied automatically
        )
        
        // Throttled debug logging
        val now = System.currentTimeMillis()
        val pokemonKey = "${pokemon.species.name}_${ballId}"
        if (now - lastDebugLogTime > DEBUG_LOG_COOLDOWN_MS || pokemonKey != lastDebugPokemon) {
            lastDebugLogTime = now
            lastDebugPokemon = pokemonKey
            CatchRateDisplayMod.debug(
                "World calc: ${pokemon.species.name} Lv${pokemon.level} | Ball: $ballId ${result.multiplier}x (${result.reason}) | " +
                "Base: $baseCatchRate | HP: ${currentHp.toInt()}/${maxHp.toInt()} | Status: ${statusMult}x | " +
                "Level bonus: ${levelBonus}x | Out-of-combat: 0.5x | Final: ${String.format("%.1f", catchChance)}%"
            )
        }
        
        return BallCatchRate(
            ballName = ballId,
            displayName = CatchRateFormula.formatBallName(ballId),
            catchRate = catchChance.toDouble().coerceIn(0.0, 100.0),
            multiplier = result.multiplier.toDouble(),
            conditionMet = result.conditionMet,
            reason = result.reason,
            isGuaranteed = result.isGuaranteed
        )
    }
    
    /**
     * Get a WILD Pokemon entity the player is looking at.
     * Uses both crosshair target and extended raycast for better detection.
     * Returns null if the Pokemon is owned by any player (trainer Pokemon).
     * Cached to avoid expensive entity queries every frame.
     */
    fun getLookedAtPokemon(): PokemonEntity? {
        lookupTickCounter++
        
        // Return cached result if recent enough
        if ((lookupTickCounter - lastPokemonLookupTick) < POKEMON_LOOKUP_INTERVAL_TICKS) {
            // Validate cached Pokemon is still valid
            val cached = cachedLookedAtPokemon
            if (cached != null && cached.isAlive) return cached
        }
        
        lastPokemonLookupTick = lookupTickCounter
        cachedLookedAtPokemon = findLookedAtPokemon()
        return cachedLookedAtPokemon
    }
    
    private fun findLookedAtPokemon(): PokemonEntity? {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return null
        val world = client.world ?: return null
        
        // First try the crosshair target
        val hitResult = client.crosshairTarget
        if (hitResult != null && hitResult.type == HitResult.Type.ENTITY) {
            val entityHit = hitResult as EntityHitResult
            val entity = entityHit.entity
            if (entity is PokemonEntity) {
                // Check both pokemon owner and entity owner for reliability
                val pokemonOwner = entity.pokemon.getOwnerUUID()
                val entityOwner = entity.ownerUuid
                if (pokemonOwner == null && entityOwner == null) return entity
                CatchRateDisplayMod.debug("Skipping owned Pokemon: ${entity.pokemon.species.name} (pokemonOwner: $pokemonOwner, entityOwner: $entityOwner)")
                return null
            }
        }
        
        // Fallback: Check nearby Pokemon via look direction
        val lookVec = player.rotationVector
        val eyePos = player.eyePos
        val maxDistance = 8.0
        
        // Filter for truly wild Pokemon (no owner on pokemon OR entity)
        val nearbyPokemon = world.getEntitiesByClass(
            PokemonEntity::class.java,
            player.boundingBox.expand(maxDistance)
        ) { it.pokemon.getOwnerUUID() == null && it.ownerUuid == null }
        
        var closestPokemon: PokemonEntity? = null
        var closestDistance = maxDistance
        
        for (pokemon in nearbyPokemon) {
            val toEntity = pokemon.pos.add(0.0, pokemon.height / 2.0, 0.0).subtract(eyePos)
            val distance = toEntity.length()
            if (distance > maxDistance) continue
            
            val dot = lookVec.dotProduct(toEntity.normalize())
            if (dot > 0.95 && distance < closestDistance) {
                closestPokemon = pokemon
                closestDistance = distance
            }
        }
        
        return closestPokemon
    }
}
