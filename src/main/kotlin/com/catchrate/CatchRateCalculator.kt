package com.catchrate

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokeball.PokeBall
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier

/**
 * Client-side catch rate calculator.
 * 
 * Uses:
 * - CatchRateFormula for all catch rate math (single source of truth)
 * - BallMultiplierCalculator for ball-specific multipliers
 * - BallContextFactory for building context objects
 * 
 * This class is now a thin wrapper that coordinates these components.
 */
object CatchRateCalculator {
    
    /**
     * Calculate catch rate for a Pokemon in battle.
     * 
     * @param pokemon The wild Pokemon being targeted
     * @param itemStack The Poke Ball item being used
     * @param turnCount Current battle turn
     * @param playerHighestLevel Highest level Pokemon on player's team (for level penalty)
     * @param inBattle Whether in battle (false = 0.5x penalty)
     * @return CatchRateResult with all calculation details
     */
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
                ballMultiplier = CatchRateConstants.BALL_GUARANTEED_MULT.toDouble(),
                baseCatchRate = pokemon.species.catchRate,
                statusName = CatchRateFormula.getStatusDisplayName(pokemon.status?.name?.path),
                ballName = ballName,
                turnCount = turnCount,
                isGuaranteed = true
            )
        }
        
        // Calculate HP using shared formula
        val hpInfo = CatchRateFormula.calculateHpInfo(
            hpValue = pokemon.hpValue,
            maxHpValue = pokemon.maxHp,
            isFlat = pokemon.isHpFlat
        )
        
        val catchRate = pokemon.species.catchRate.toFloat()
        val level = pokemon.level
        
        // Get status multiplier from shared formula
        val statusPath = pokemon.status?.name?.path
        val bonusStatus = CatchRateFormula.getStatusMultiplier(statusPath)
        
        // Get level bonus from shared formula
        val bonusLevel = CatchRateFormula.getLowLevelBonus(level)
        
        // Get ball multiplier using unified calculator
        val ballBonus = getBallMultiplier(pokeBall, ballName, pokemon, turnCount)
        
        // NOTE: Cobblemon's findHighestThrowerLevel() always returns null for wild battles
        // due to a logic issue in their code, so level penalty is never actually applied.
        // We match Cobblemon's actual behavior by not applying level penalty.
        
        // Calculate catch percentage using shared formula
        val captureChance = CatchRateFormula.calculateCatchPercentage(
            baseCatchRate = catchRate,
            maxHp = hpInfo.maxHp,
            currentHp = hpInfo.currentHp,
            ballMultiplier = ballBonus,
            statusMultiplier = bonusStatus,
            levelBonus = bonusLevel,
            inBattle = inBattle
        )
        
        // Calculate modified catch rate for debug/display purposes
        val modifiedCatchRate = CatchRateFormula.calculateModifiedCatchRate(
            baseCatchRate = catchRate,
            maxHp = hpInfo.maxHp,
            currentHp = hpInfo.currentHp,
            ballMultiplier = ballBonus,
            statusMultiplier = bonusStatus,
            levelBonus = bonusLevel,
            inBattle = inBattle
        )
        
        return CatchRateResult(
            percentage = captureChance.toDouble().coerceIn(0.0, 100.0),
            hpPercentage = hpInfo.percentage,
            statusMultiplier = bonusStatus.toDouble(),
            ballMultiplier = ballBonus.toDouble(),
            baseCatchRate = catchRate.toInt(),
            statusName = CatchRateFormula.getStatusDisplayName(statusPath),
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
        } catch (e: Exception) { 
            null 
        }
    }
    
    /**
     * Get ball multiplier using the unified BallMultiplierCalculator.
     * This delegates to the single source of truth for ball multiplier logic.
     */
    private fun getBallMultiplier(
        pokeBall: PokeBall?, 
        ballName: String, 
        pokemon: ClientBattlePokemon, 
        turnCount: Int
    ): Float {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return CatchRateConstants.BALL_STANDARD_MULT
        val world = client.world ?: return CatchRateConstants.BALL_STANDARD_MULT
        
        // Use API for guaranteed catch check
        if (pokeBall?.catchRateModifier?.isGuaranteed() == true) {
            return CatchRateConstants.BALL_GUARANTEED_MULT
        }
        
        // Build context for unified calculator
        val battle = CobblemonClient.battle
        val ctx = BallContextFactory.fromBattlePokemon(pokemon, turnCount, player, world, battle)
        
        // Use unified calculator
        val result = BallMultiplierCalculator.calculate(ballName.lowercase(), ctx)
        return result.multiplier
    }
    
    /**
     * Get HP percentage from a ClientBattlePokemon.
     * Uses shared HP calculation formula.
     */
    private fun getHpPercentage(pokemon: ClientBattlePokemon): Double {
        val hpInfo = CatchRateFormula.calculateHpInfo(
            hpValue = pokemon.hpValue,
            maxHpValue = pokemon.maxHp,
            isFlat = pokemon.isHpFlat
        )
        return hpInfo.percentage
    }
}

/**
 * Result of a catch rate calculation.
 */
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
