package com.catchrate.client

import com.catchrate.CatchRateDisplayMod
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.util.Identifier
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import kotlin.math.max
import kotlin.math.pow

object BallComparisonCalculator {
    
    private val comparableBalls = listOf(
        "quick_ball", "ultra_ball", "timer_ball", "dusk_ball", "net_ball", "dive_ball",
        "nest_ball", "repeat_ball", "great_ball", "level_ball", "love_ball", "heavy_ball",
        "fast_ball", "moon_ball", "dream_ball", "lure_ball", "friend_ball", "luxury_ball",
        "heal_ball", "premier_ball", "poke_ball", "safari_ball", "sport_ball", "beast_ball"
    )
    
    data class BallCatchRate(
        val ballName: String,
        val displayName: String,
        val catchRate: Double,
        val multiplier: Double,
        val conditionMet: Boolean,
        val reason: String,
        val isGuaranteed: Boolean = false
    )
    
    fun calculateAllBalls(pokemon: ClientBattlePokemon, turnCount: Int): List<BallCatchRate> {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return emptyList()
        val world = client.world ?: return emptyList()
        
        val (currentHp, maxHp) = if (pokemon.isHpFlat) {
            pokemon.hpValue to (if (pokemon.maxHp > 0) pokemon.maxHp else 1F)
        } else {
            val mHp = if (pokemon.maxHp > 0) pokemon.maxHp else 100F
            (pokemon.hpValue * mHp) to mHp
        }
        
        val baseCatchRate = pokemon.species.catchRate.toFloat()
        val level = pokemon.level
        
        val statusMult = when (pokemon.status?.name?.path) {
            "sleep", "frozen" -> 2.5F
            "paralysis", "burn", "poison", "poisonbadly" -> 1.5F
            else -> 1F
        }
        
        val levelBonus = if (level < 13) max((36 - (2 * level)) / 10F, 1F) else 1F
        
        val results = mutableListOf<BallCatchRate>()
        
        for (ballId in comparableBalls) {
            val (multiplier, conditionMet, reason) = getBallInfo(
                ballId, pokemon, turnCount, player, world
            )
            
            // Calculate catch rate
            val hpComponent = (3F * maxHp - 2F * currentHp) * baseCatchRate
            var modifiedRate = (hpComponent * multiplier) / (3F * maxHp)
            modifiedRate *= statusMult * levelBonus
            
            val shakeProbability = when {
                modifiedRate >= 255F -> 65536F
                modifiedRate <= 0F -> 0F
                else -> 65536F / (255F / modifiedRate).pow(0.1875F)
            }
            
            val catchChance = (shakeProbability / 65536F).pow(4F) * 100F
            
            results.add(BallCatchRate(
                ballName = ballId,
                displayName = formatBallName(ballId),
                catchRate = catchChance.toDouble().coerceIn(0.0, 100.0),
                multiplier = multiplier.toDouble(),
                conditionMet = conditionMet,
                reason = reason
            ))
        }
        
        // Sort by catch rate descending
        return results.sortedByDescending { it.catchRate }
    }
    
    /**
     * Calculate catch rate for a world Pokemon (out of combat).
     */
    fun calculateForWorldPokemon(
        entity: PokemonEntity,
        ballId: String
    ): BallCatchRate? {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return null
        val world = client.world ?: return null
        
        val pokemon = entity.pokemon
        val baseCatchRate = pokemon.species.catchRate.toFloat()
        val maxHp = pokemon.maxHealth.toFloat()
        val currentHp = pokemon.currentHealth.toFloat() // Usually full for wild Pokemon
        
        val (multiplier, conditionMet, reason) = getBallInfoForWorld(
            ballId, pokemon, player, world
        )
        
        // Status (usually none for wild Pokemon)
        val statusMult = when (pokemon.status?.status?.name?.path) {
            "sleep", "frozen" -> 2.5F
            "paralysis", "burn", "poison", "poisonbadly" -> 1.5F
            else -> 1F
        }
        
        val levelBonus = if (pokemon.level < 13) max((36 - (2 * pokemon.level)) / 10F, 1F) else 1F
        
        // Out of battle = 0.5x modifier
        val inBattleModifier = 0.5F
        
        val hpComponent = (3F * maxHp - 2F * currentHp) * baseCatchRate * inBattleModifier
        var modifiedRate = (hpComponent * multiplier) / (3F * maxHp)
        modifiedRate *= statusMult * levelBonus
        
        val shakeProbability = when {
            modifiedRate >= 255F -> 65536F
            modifiedRate <= 0F -> 0F
            else -> 65536F / (255F / modifiedRate).pow(0.1875F)
        }
        
        val catchChance = (shakeProbability / 65536F).pow(4F) * 100F
        
        return BallCatchRate(
            ballName = ballId,
            displayName = formatBallName(ballId),
            catchRate = catchChance.toDouble().coerceIn(0.0, 100.0),
            multiplier = multiplier.toDouble(),
            conditionMet = conditionMet,
            reason = reason
        )
    }
    
    /**
     * Get the Pokemon entity the player is looking at.
     * Uses both crosshair target and extended raycast for better detection.
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
                return entity
            }
        }
        
        // Fallback: Check nearby Pokemon entities that the player is looking at
        val lookVec = player.rotationVector
        val eyePos = player.eyePos
        val maxDistance = 8.0
        
        // Get all Pokemon entities within range
        val nearbyPokemon = world.getEntitiesByClass(
            PokemonEntity::class.java,
            player.boundingBox.expand(maxDistance)
        ) { true }
        
        var closestPokemon: PokemonEntity? = null
        var closestDistance = maxDistance
        
        for (pokemon in nearbyPokemon) {
            // Check if player is looking at this Pokemon
            val toEntity = pokemon.pos.add(0.0, pokemon.height / 2.0, 0.0).subtract(eyePos)
            val distance = toEntity.length()
            
            if (distance > maxDistance) continue
            
            val normalizedToEntity = toEntity.normalize()
            val dot = lookVec.dotProduct(normalizedToEntity)
            
            // Check if looking roughly at the entity (dot product > 0.95 means within ~18 degrees)
            if (dot > 0.95 && distance < closestDistance) {
                closestPokemon = pokemon
                closestDistance = distance
            }
        }
        
        return closestPokemon
    }
    
    /**
     * Get ball multiplier info using Cobblemon API.
     * Ancient balls that are just throwPower variants (Feather/Wing/Jet/Heavy/Leaden/Gigaton) return 1x.
     * Only ancient_great_ball (1.5x), ancient_ultra_ball (2x), and ancient_origin_ball (guaranteed) have modifiers.
     */
    private fun getBallInfo(
        ballId: String,
        pokemon: ClientBattlePokemon,
        turnCount: Int,
        player: net.minecraft.entity.player.PlayerEntity,
        world: net.minecraft.world.World
    ): Triple<Float, Boolean, String> {
        val lower = ballId.lowercase()
        
        // Try to get ball from Cobblemon API
        val pokeBall = try {
            PokeBalls.getPokeBall(Identifier.of("cobblemon", ballId))
        } catch (e: Exception) { null }
        
        // Guaranteed catch balls
        if (pokeBall?.catchRateModifier?.isGuaranteed() == true || lower.contains("master")) {
            return Triple(255F, true, "Guaranteed catch!")
        }
        
        // Ancient balls - wiki-documented multipliers:
        // Feather/Heavy = 1x, Wing/Leaden = 1.5x, Jet/Gigaton = 2x
        if (pokeBall?.ancient == true) {
            return when {
                lower.contains("jet") || lower.contains("gigaton") -> Triple(2F, true, "Ancient T3: 2x")
                lower.contains("wing") || lower.contains("leaden") -> Triple(1.5F, true, "Ancient T2: 1.5x")
                lower.contains("ultra") -> Triple(2F, true, "Ancient Ultra: 2x")
                lower.contains("great") -> Triple(1.5F, true, "Ancient Great: 1.5x")
                else -> Triple(1F, true, "Ancient T1: 1x")
            }
        }
        
        return when {
            lower == "quick_ball" -> {
                val mult = if (turnCount == 1) 5F else 1F
                Triple(mult, turnCount == 1, if (turnCount == 1) "First turn!" else "Only effective turn 1")
            }
            lower == "timer_ball" -> {
                val mult = (turnCount * (1229F / 4096F) + 1F).coerceAtMost(4F)
                Triple(mult, mult > 1.01f, "Turn $turnCount")
            }
            lower == "ultra_ball" -> Triple(2F, true, "2x always")
            lower == "great_ball" -> Triple(1.5F, true, "1.5x always")
            lower == "poke_ball" -> Triple(1F, true, "1x always")
            lower == "dusk_ball" -> {
                val light = world.getLightLevel(player.blockPos)
                val mult = if (light <= 7) 3F else 1F
                Triple(mult, light <= 7, if (light <= 7) "Dark area" else "Need darkness")
            }
            lower == "net_ball" -> {
                val types = listOf(pokemon.species.primaryType.name, pokemon.species.secondaryType?.name)
                    .filterNotNull().map { it.lowercase() }
                val isBugWater = types.any { it == "bug" || it == "water" }
                Triple(if (isBugWater) 3F else 1F, isBugWater, if (isBugWater) "Bug/Water type!" else "Not Bug/Water")
            }
            lower == "dive_ball" -> {
                val inWater = player.isSubmergedInWater
                Triple(if (inWater) 3.5F else 1F, inWater, if (inWater) "Underwater!" else "Need to be underwater")
            }
            lower == "nest_ball" -> {
                val effective = pokemon.level < 30
                val mult = if (effective) ((41 - pokemon.level) / 10F).coerceAtLeast(1F) else 1F
                Triple(mult, effective, if (effective) "Low level (${pokemon.level})" else "Only <Lv30")
            }
            lower == "dream_ball" -> {
                val asleep = pokemon.status?.name?.path == "sleep"
                Triple(if (asleep) 4F else 1F, asleep, if (asleep) "Sleeping!" else "Need sleep status")
            }
            lower == "moon_ball" -> {
                val timeOfDay = world.timeOfDay % 24000
                val isNight = timeOfDay in 12000..24000
                val mult = if (isNight) {
                    when (world.moonPhase) {
                        0 -> 4F; 1, 7 -> 2.5F; 2, 6 -> 1.5F; else -> 1F
                    }
                } else 1F
                Triple(mult, isNight && mult > 1, if (isNight) "Nighttime" else "Only at night")
            }
            lower == "fast_ball" -> {
                val speed = pokemon.species.baseStats.entries
                    .find { it.key.showdownId.equals("spe", true) }?.value ?: 0
                val fast = speed >= 100
                Triple(if (fast) 4F else 1F, fast, if (fast) "Speed ≥100" else "Speed <100")
            }
            lower == "heavy_ball" -> {
                val weight = pokemon.species.weight
                val mult = when {
                    weight >= 3000 -> 4F
                    weight >= 2000 -> 2.5F
                    weight >= 1000 -> 1.5F
                    else -> 1F
                }
                Triple(mult, mult > 1, "Weight: ${weight/10f}kg")
            }
            lower == "beast_ball" -> {
                val labels = try { pokemon.species.labels.map { it.lowercase() } } catch (e: Exception) { emptyList() }
                val isUB = labels.contains("ultra_beast")
                Triple(if (isUB) 5F else 0.1F, isUB, if (isUB) "Ultra Beast!" else "Not Ultra Beast")
            }
            // Server-required balls - show estimate
            lower == "love_ball" -> Triple(1F, false, "Needs same species")
            lower == "level_ball" -> Triple(1F, false, "Needs level check")
            lower == "repeat_ball" -> Triple(1F, false, "Needs Pokédex")
            lower == "lure_ball" -> Triple(1F, false, "Needs fishing")
            // No bonus balls
            else -> Triple(1F, true, "No bonus")
        }
    }
    
    /**
     * Get ball multiplier info for world Pokemon using Cobblemon API.
     * Ancient balls that are just throwPower variants return 1x.
     */
    private fun getBallInfoForWorld(
        ballId: String,
        pokemon: com.cobblemon.mod.common.pokemon.Pokemon,
        player: net.minecraft.entity.player.PlayerEntity,
        world: net.minecraft.world.World
    ): Triple<Float, Boolean, String> {
        val lower = ballId.lowercase()
        
        // Try to get ball from Cobblemon API
        val pokeBall = try {
            PokeBalls.getPokeBall(Identifier.of("cobblemon", ballId))
        } catch (e: Exception) { null }
        
        // Guaranteed catch balls (Master Ball, Ancient Origin Ball)
        if (pokeBall?.catchRateModifier?.isGuaranteed() == true || lower.contains("master")) {
            return Triple(255F, true, "Guaranteed catch!")
        }
        
        // Ancient balls - wiki-documented multipliers:
        // Feather/Heavy = 1x, Wing/Leaden = 1.5x, Jet/Gigaton = 2x
        if (pokeBall?.ancient == true) {
            return when {
                lower.contains("jet") || lower.contains("gigaton") -> Triple(2F, true, "Ancient T3: 2x")
                lower.contains("wing") || lower.contains("leaden") -> Triple(1.5F, true, "Ancient T2: 1.5x")
                lower.contains("ultra") -> Triple(2F, true, "Ancient Ultra: 2x")
                lower.contains("great") -> Triple(1.5F, true, "Ancient Great: 1.5x")
                else -> Triple(1F, true, "Ancient T1: 1x")
            }
        }
        
        return when {
            // Always 5x for first turn (out of combat counts as turn 1)
            lower == "quick_ball" -> Triple(5F, true, "First turn!")
            lower == "timer_ball" -> Triple(1F, false, "Increases each turn")
            lower == "ultra_ball" -> Triple(2F, true, "2x always")
            lower == "great_ball" -> Triple(1.5F, true, "1.5x always")
            lower == "sport_ball" -> Triple(1.5F, true, "1.5x always")
            lower == "safari_ball" -> Triple(1.5F, true, "Out of battle: 1.5x")
            lower == "poke_ball" -> Triple(1F, true, "1x always")
            
            lower == "dusk_ball" -> {
                val light = world.getLightLevel(player.blockPos)
                val mult = when (light) {
                    0 -> 3.5F
                    in 1..7 -> 3F
                    else -> 1F
                }
                Triple(mult, light <= 7, if (light <= 7) "Dark area (L$light)" else "Need darkness")
            }
            
            lower == "dive_ball" -> {
                val inWater = player.isSubmergedInWater
                Triple(if (inWater) 3.5F else 1F, inWater, if (inWater) "Underwater!" else "Need underwater")
            }
            
            lower == "net_ball" -> {
                val types = listOf(pokemon.primaryType.name, pokemon.secondaryType?.name)
                    .filterNotNull().map { it.lowercase() }
                val isBugWater = types.any { it == "bug" || it == "water" }
                Triple(if (isBugWater) 3F else 1F, isBugWater, if (isBugWater) "Bug/Water!" else "Not Bug/Water")
            }
            
            lower == "nest_ball" -> {
                val effective = pokemon.level < 30
                val mult = if (effective) ((41 - pokemon.level) / 10F).coerceAtLeast(1F) else 1F
                Triple(mult, effective, if (effective) "Lv${pokemon.level} bonus" else "Only <Lv30")
            }
            
            lower == "moon_ball" -> {
                val timeOfDay = world.timeOfDay % 24000
                val isNight = timeOfDay in 12000..24000
                val mult = if (isNight) {
                    when (world.moonPhase) {
                        0 -> 4F; 1, 7 -> 2.5F; 2, 6 -> 1.5F; else -> 1F
                    }
                } else 1F
                Triple(mult, isNight && mult > 1, if (isNight) "Night (phase ${world.moonPhase})" else "Only at night")
            }
            
            lower == "dream_ball" -> {
                val asleep = pokemon.status?.status?.name?.path == "sleep"
                Triple(if (asleep) 4F else 1F, asleep, if (asleep) "Sleeping!" else "Need sleep")
            }
            
            lower == "fast_ball" -> {
                val speed = pokemon.species.baseStats.entries
                    .find { it.key.showdownId.equals("spe", true) }?.value ?: 0
                val fast = speed >= 100
                Triple(if (fast) 4F else 1F, fast, if (fast) "Speed $speed" else "Speed <100")
            }
            
            lower == "heavy_ball" -> {
                val weight = pokemon.species.weight
                val mult = when {
                    weight >= 3000 -> 4F
                    weight >= 2000 -> 2.5F
                    weight >= 1000 -> 1.5F
                    else -> 1F
                }
                Triple(mult, mult > 1, "${weight/10f}kg")
            }
            
            lower == "beast_ball" -> {
                val labels = try { pokemon.species.labels.map { it.lowercase() } } catch (e: Exception) { emptyList() }
                val isUB = labels.contains("ultra_beast")
                Triple(if (isUB) 5F else 0.1F, isUB, if (isUB) "Ultra Beast!" else "0.1x non-UB")
            }
            
            else -> Triple(1F, true, "")
        }
    }
    
    private fun formatBallName(name: String): String {
        return name.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
}
