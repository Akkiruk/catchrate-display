package com.catchrate

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokeball.PokeBall
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import kotlin.math.max
import kotlin.math.pow

/**
 * Calculates catch rates using Cobblemon's native PokeBall API.
 * Uses pokeBall.catchRateModifier and pokeBall.ancient for accurate multipliers.
 */
object CatchRateCalculator {
    
    fun calculateCatchRate(
        pokemon: ClientBattlePokemon,
        itemStack: ItemStack,
        turnCount: Int = 1,
        playerHighestLevel: Int? = null,
        inBattle: Boolean = true
    ): CatchRateResult {
        val pokeBall = getPokeBallFromItem(itemStack)
        val ballName = pokeBall?.name?.path ?: itemStack.item.toString().substringAfter(":").substringBefore("}")
        
        // Check for guaranteed catch via API
        if (pokeBall?.catchRateModifier?.isGuaranteed() == true) {
            return CatchRateResult(
                percentage = 100.0,
                hpPercentage = getHpPercentage(pokemon),
                statusMultiplier = 1.0,
                ballMultiplier = 255.0,
                baseCatchRate = pokemon.species.catchRate,
                statusName = getStatusName(pokemon),
                ballName = ballName,
                turnCount = turnCount,
                isGuaranteed = true
            )
        }
        
        val (currentHp, maxHp, hpPercentage) = calculateHp(pokemon)
        val catchRate = pokemon.species.catchRate.toFloat()
        val level = pokemon.level
        
        val bonusStatus = getStatusMultiplier(pokemon)
        val bonusLevel = if (level < 13) max((36 - (2 * level)) / 10F, 1F) else 1F
        val ballBonus = getBallMultiplier(pokeBall, ballName, pokemon, turnCount)
        val inBattleModifier = if (inBattle) 1F else 0.5F
        
        val hpComponent = (3F * maxHp - 2F * currentHp) * catchRate * inBattleModifier
        var modifiedCatchRate = (hpComponent * ballBonus) / (3F * maxHp)
        modifiedCatchRate *= bonusStatus * bonusLevel
        
        if (playerHighestLevel != null && playerHighestLevel < level) {
            modifiedCatchRate *= max(0.1F, 1F - ((level - playerHighestLevel) / 50F).coerceAtMost(0.9F))
        }
        
        val shakeProbability = when {
            modifiedCatchRate >= 255F -> 65536F
            modifiedCatchRate <= 0F -> 0F
            else -> 65536F / (255F / modifiedCatchRate).pow(0.1875F)
        }
        
        val captureChance = (shakeProbability / 65536F).pow(4F) * 100F
        
        return CatchRateResult(
            percentage = captureChance.toDouble().coerceIn(0.0, 100.0),
            hpPercentage = hpPercentage,
            statusMultiplier = bonusStatus.toDouble(),
            ballMultiplier = ballBonus.toDouble(),
            baseCatchRate = catchRate.toInt(),
            statusName = getStatusName(pokemon),
            ballName = ballName,
            turnCount = turnCount,
            isGuaranteed = false,
            levelBonus = bonusLevel.toDouble(),
            modifiedCatchRate = modifiedCatchRate.toDouble()
        )
    }
    
    /**
     * Get the PokeBall object from an ItemStack using Cobblemon's API.
     */
    fun getPokeBallFromItem(itemStack: ItemStack): PokeBall? {
        val item = itemStack.item
        if (item is PokeBallItem) return item.pokeBall
        
        val ballName = itemStack.item.toString().substringAfter(":").substringBefore("}").trim()
        return try {
            PokeBalls.getPokeBall(Identifier.of("cobblemon", ballName))
        } catch (e: Exception) { null }
    }
    
    /**
     * Get ball multiplier - uses pokeBall.ancient to handle ancient variants elegantly.
     * Ancient balls that are just throwPower variants (not catch rate variants) return 1x.
     */
    private fun getBallMultiplier(pokeBall: PokeBall?, ballName: String, pokemon: ClientBattlePokemon, turnCount: Int): Float {
        val client = MinecraftClient.getInstance()
        val player = client.player
        val world = client.world
        val name = ballName.lowercase()
        
        // Use API if available
        if (pokeBall != null) {
            // Guaranteed catch (Master Ball, Ancient Origin Ball)
            if (pokeBall.catchRateModifier.isGuaranteed()) return 255F
            
            // Ancient balls - wiki-documented multipliers:
            // Feather/Heavy = 1x, Wing/Leaden = 1.5x, Jet/Gigaton = 2x, Great = 1.5x, Ultra = 2x
            if (pokeBall.ancient) {
                return when {
                    name.contains("jet") || name.contains("gigaton") -> 2F
                    name.contains("wing") || name.contains("leaden") -> 1.5F
                    name.contains("ultra") -> 2F
                    name.contains("great") -> 1.5F
                    else -> 1F // Feather, Heavy, basic ancient balls
                }
            }
        }
        
        // Guaranteed catch balls (fallback if API didn't catch it)
        if (name.contains("master")) return 255F
        
        // Standard ball logic
        return when (name) {
            "great_ball" -> 1.5F
            "ultra_ball" -> 2F
            "sport_ball" -> 1.5F
            
            "timer_ball" -> (turnCount * (1229F / 4096F)).coerceAtMost(4F)
            "quick_ball" -> if (turnCount == 1) 5F else 1F
            
            "net_ball" -> {
                val types = listOf(pokemon.species.primaryType.name, pokemon.species.secondaryType?.name)
                    .filterNotNull().map { it.lowercase() }
                if (types.any { it == "bug" || it == "water" }) 3F else 1F
            }
            
            "nest_ball" -> if (pokemon.level < 30) ((41 - pokemon.level) / 10F).coerceAtLeast(1F) else 1F
            
            "dusk_ball" -> {
                if (player != null && world != null) {
                    when (world.getLightLevel(player.blockPos)) {
                        0 -> 3.5F
                        in 1..7 -> 3F
                        else -> 1F
                    }
                } else 1F
            }
            
            "dive_ball" -> if (player?.isSubmergedInWater == true) 3.5F else 1F
            
            "moon_ball" -> world?.let {
                val isNight = (it.timeOfDay % 24000) in 12000..24000
                if (isNight) when (it.moonPhase) { 0 -> 4F; 1, 7 -> 2.5F; 2, 6 -> 1.5F; else -> 1F } else 1F
            } ?: 1F
            
            "dream_ball" -> if (pokemon.status?.name?.path == "sleep") 4F else 1F
            
            "fast_ball" -> {
                val speed = pokemon.species.baseStats.entries.find { it.key.showdownId.equals("spe", true) }?.value ?: 0
                if (speed >= 100) 4F else 1F
            }
            
            "heavy_ball" -> when {
                pokemon.species.weight >= 3000 -> 4F
                pokemon.species.weight >= 2000 -> 2.5F
                pokemon.species.weight >= 1000 -> 1.5F
                else -> 1F
            }
            
            "beast_ball" -> {
                val labels = try { pokemon.species.labels.map { it.lowercase() } } catch (e: Exception) { emptyList() }
                if (labels.contains("ultra_beast")) 5F else 0.1F
            }
            
            else -> 1F
        }
    }
    
    private fun calculateHp(pokemon: ClientBattlePokemon): Triple<Float, Float, Double> {
        return if (pokemon.isHpFlat) {
            val currentHp = pokemon.hpValue
            val maxHp = if (pokemon.maxHp > 0) pokemon.maxHp else 1F
            Triple(currentHp, maxHp, (currentHp / maxHp * 100.0))
        } else {
            val maxHp = if (pokemon.maxHp > 0) pokemon.maxHp else 100F
            val currentHp = pokemon.hpValue * maxHp
            Triple(currentHp, maxHp, (pokemon.hpValue * 100.0))
        }
    }
    
    private fun getStatusMultiplier(pokemon: ClientBattlePokemon) = when (pokemon.status?.name?.path) {
        "sleep", "frozen" -> 2.5F
        "paralysis", "burn", "poison", "poisonbadly" -> 1.5F
        else -> 1F
    }
    
    private fun getHpPercentage(pokemon: ClientBattlePokemon) = if (pokemon.isHpFlat && pokemon.maxHp > 0) {
        (pokemon.hpValue / pokemon.maxHp * 100.0)
    } else {
        pokemon.hpValue * 100.0
    }.coerceIn(0.0, 100.0)
    
    private fun getStatusName(pokemon: ClientBattlePokemon) = when (pokemon.status?.name?.path) {
        "sleep" -> "Asleep"
        "frozen" -> "Frozen"
        "paralysis" -> "Paralyzed"
        "poison" -> "Poisoned"
        "poisonbadly" -> "Badly Poisoned"
        "burn" -> "Burned"
        else -> "None"
    }
}

data class CatchRateResult(
    val percentage: Double,
    val hpPercentage: Double,
    val statusMultiplier: Double,
    val ballMultiplier: Double,
    val baseCatchRate: Int,
    val statusName: String,
    val ballName: String = "poke_ball",
    val turnCount: Int = 1,
    val isGuaranteed: Boolean = false,
    val levelBonus: Double = 1.0,
    val modifiedCatchRate: Double = 0.0
)
