package com.catchrate.client

import com.catchrate.BallMultiplierCalculator
import com.catchrate.BallMultiplierCalculator.BallContext
import com.catchrate.BallMultiplierCalculator.PartyMember
import com.catchrate.CatchRateDisplayMod
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Gender
import net.minecraft.client.MinecraftClient
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import kotlin.math.max
import kotlin.math.pow

/**
 * Client-side calculator that computes catch rates for Pokemon using the unified BallMultiplierCalculator.
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
        
        val ctx = buildBattleContext(pokemon, turnCount, player, world, battle)
        
        val (currentHp, maxHp) = if (pokemon.isHpFlat) {
            pokemon.hpValue to (if (pokemon.maxHp > 0) pokemon.maxHp else 1F)
        } else {
            val mHp = if (pokemon.maxHp > 0) pokemon.maxHp else 100F
            (pokemon.hpValue * mHp) to mHp
        }
        
        val baseCatchRate = pokemon.species.catchRate.toFloat()
        val statusMult = getStatusMultiplier(pokemon.status?.name?.path)
        val levelBonus = if (pokemon.level < 13) max((36 - (2 * pokemon.level)) / 10F, 1F) else 1F
        
        return comparableBalls.map { ballId ->
            val result = BallMultiplierCalculator.calculate(ballId, ctx)
            val catchChance = calculateCatchChance(
                baseCatchRate, maxHp, currentHp, result.multiplier, statusMult, levelBonus
            )
            
            BallCatchRate(
                ballName = ballId,
                displayName = BallMultiplierCalculator.formatBallName(ballId),
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
        val ctx = buildWorldContext(pokemon, player, world)
        
        val result = BallMultiplierCalculator.calculate(ballId, ctx)
        
        val baseCatchRate = pokemon.species.catchRate.toFloat()
        val maxHp = pokemon.maxHealth.toFloat()
        val currentHp = pokemon.currentHealth.toFloat()
        val statusMult = getStatusMultiplier(pokemon.status?.status?.name?.path)
        val levelBonus = if (pokemon.level < 13) max((36 - (2 * pokemon.level)) / 10F, 1F) else 1F
        
        // Out of battle = 0.5x modifier
        val catchChance = calculateCatchChance(
            baseCatchRate, maxHp, currentHp, result.multiplier, statusMult, levelBonus, 
            outOfBattleModifier = 0.5F
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
            displayName = BallMultiplierCalculator.formatBallName(ballId),
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
     */
    fun getLookedAtPokemon(): PokemonEntity? {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return null
        val world = client.world ?: return null
        
        // First try the crosshair target
        val hitResult = client.crosshairTarget
        if (hitResult != null && hitResult.type == HitResult.Type.ENTITY) {
            val entityHit = hitResult as EntityHitResult
            val entity = entityHit.entity
            if (entity is PokemonEntity) {
                val ownerUuid = entity.pokemon.getOwnerUUID()
                if (ownerUuid == null) return entity
                CatchRateDisplayMod.debug("Skipping owned Pokemon: ${entity.pokemon.species.name} (owner: $ownerUuid)")
                return null
            }
        }
        
        // Fallback: Check nearby Pokemon via look direction
        val lookVec = player.rotationVector
        val eyePos = player.eyePos
        val maxDistance = 8.0
        
        val nearbyPokemon = world.getEntitiesByClass(
            PokemonEntity::class.java,
            player.boundingBox.expand(maxDistance)
        ) { it.pokemon.getOwnerUUID() == null }
        
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
    
    // === CONTEXT BUILDERS ===
    
    /**
     * Build a BallContext from a ClientBattlePokemon (in-battle scenario).
     * Gets the player's active battler for Love Ball checks.
     */
    private fun buildBattleContext(
        pokemon: ClientBattlePokemon,
        turnCount: Int,
        player: net.minecraft.entity.player.PlayerEntity,
        world: net.minecraft.world.World,
        battle: ClientBattle?
    ): BallContext {
        val species = pokemon.species
        val timeOfDay = world.timeOfDay % 24000
        
        // Get the player's ACTIVE battler (the one currently in play)
        val activeBattler = getActiveBattler(battle)
        
        return BallContext(
            speciesId = species.resourceIdentifier.toString(),
            level = pokemon.level,
            gender = try { Gender.valueOf(pokemon.gender.name) } catch (e: Exception) { null },
            primaryType = species.primaryType.name,
            secondaryType = species.secondaryType?.name,
            weight = species.weight,
            baseSpeed = species.baseStats.entries.find { it.key.showdownId.equals("spe", true) }?.value ?: 0,
            labels = try { species.labels.toList() } catch (e: Exception) { emptyList() },
            statusPath = pokemon.status?.name?.path,
            lightLevel = world.getLightLevel(player.blockPos),
            isNight = timeOfDay in 12000..24000,
            moonPhase = world.moonPhase,
            isPlayerUnderwater = player.isSubmergedInWater,
            inBattle = true,
            turnCount = turnCount,
            activeBattler = activeBattler
        )
    }
    
    /**
     * Build a BallContext from a Pokemon entity (out-of-combat scenario).
     * Love Ball won't work out of battle (no active battler).
     */
    private fun buildWorldContext(
        pokemon: com.cobblemon.mod.common.pokemon.Pokemon,
        player: net.minecraft.entity.player.PlayerEntity,
        world: net.minecraft.world.World
    ): BallContext {
        val species = pokemon.species
        val timeOfDay = world.timeOfDay % 24000
        
        // Out of battle - no active battler for Love Ball
        return BallContext(
            speciesId = species.resourceIdentifier.toString(),
            level = pokemon.level,
            gender = pokemon.gender,
            primaryType = pokemon.primaryType.name,
            secondaryType = pokemon.secondaryType?.name,
            weight = species.weight,
            baseSpeed = species.baseStats.entries.find { it.key.showdownId.equals("spe", true) }?.value ?: 0,
            labels = try { species.labels.toList() } catch (e: Exception) { emptyList() },
            statusPath = pokemon.status?.status?.name?.path,
            lightLevel = world.getLightLevel(player.blockPos),
            isNight = timeOfDay in 12000..24000,
            moonPhase = world.moonPhase,
            isPlayerUnderwater = player.isSubmergedInWater,
            inBattle = false,
            turnCount = 0,
            activeBattler = null  // No active battler out of combat
        )
    }
    
    /**
     * Get the player's active battler from the battle (side1).
     * Returns the species and gender of the Pokemon currently in play.
     */
    private fun getActiveBattler(battle: ClientBattle?): PartyMember? {
        if (battle == null) return null
        return try {
            // side1 is the player, get their active Pokemon
            val activePokemon = battle.side1.activeClientBattlePokemon.firstOrNull()?.battlePokemon
            if (activePokemon != null) {
                PartyMember(
                    speciesId = activePokemon.species.resourceIdentifier.toString(),
                    gender = try { Gender.valueOf(activePokemon.gender.name) } catch (e: Exception) { null }
                )
            } else null
        } catch (e: Exception) {
            CatchRateDisplayMod.debug("Could not access active battler: ${e.message}")
            null
        }
    }
    
    // === UTILITY ===
    
    private fun getStatusMultiplier(statusPath: String?): Float {
        return when (statusPath) {
            "sleep", "frozen" -> 2.5F
            "paralysis", "burn", "poison", "poisonbadly" -> 1.5F
            else -> 1F
        }
    }
    
    private fun calculateCatchChance(
        baseCatchRate: Float,
        maxHp: Float,
        currentHp: Float,
        ballMultiplier: Float,
        statusMult: Float,
        levelBonus: Float,
        outOfBattleModifier: Float = 1F
    ): Float {
        val hpComponent = (3F * maxHp - 2F * currentHp) * baseCatchRate * outOfBattleModifier
        var modifiedRate = (hpComponent * ballMultiplier) / (3F * maxHp)
        modifiedRate *= statusMult * levelBonus
        
        val shakeProbability = when {
            modifiedRate >= 255F -> 65536F
            modifiedRate <= 0F -> 0F
            else -> 65536F / (255F / modifiedRate).pow(0.1875F)
        }
        
        return (shakeProbability / 65536F).pow(4F) * 100F
    }
}
