package com.catchrate

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokeball.PokeBall
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.resources.ResourceLocation

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
        
        val statusPath = pokemon.status?.name?.path
        val bonusStatus = CatchRateFormula.getStatusMultiplier(statusPath)
        val bonusLevel = CatchRateFormula.getLowLevelBonus(level)
        val ballResult = getBallResult(pokeBall, ballName, pokemon, turnCount)
        val ballBonus = ballResult.multiplier
        
        // Compute modified rate once, derive percentage from it (avoids duplicate HP/modifier math)
        val modifiedCatchRate = CatchRateFormula.calculateModifiedCatchRate(
            baseCatchRate = catchRate,
            maxHp = hpInfo.maxHp,
            currentHp = hpInfo.currentHp,
            ballMultiplier = ballBonus,
            statusMultiplier = bonusStatus,
            levelBonus = bonusLevel,
            inBattle = inBattle
        )
        val isFormulaGuaranteed = CatchRateFormula.isGuaranteedByFormula(modifiedCatchRate)
        val captureChance = CatchRateFormula.modifiedRateToPercentage(modifiedCatchRate)
        
        CatchRateMod.debugCalc(
            pokemon = "${pokemon.species.name} Lv${level}",
            ball = "$ballName ${ballBonus}x${if (ballResult.conditionMet) " (met)" else ""}",
            hp = "${String.format("%.1f", hpInfo.percentage)}% (${hpInfo.currentHp.toInt()}/${hpInfo.maxHp.toInt()})",
            status = "${CatchRateFormula.getStatusDisplayName(statusPath)} ${bonusStatus}x",
            result = "${String.format("%.2f", captureChance)}% (mod=${String.format("%.1f", modifiedCatchRate)}, base=$catchRate)"
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
            isGuaranteed = isFormulaGuaranteed,
            levelBonus = bonusLevel.toDouble(),
            modifiedCatchRate = modifiedCatchRate.toDouble(),
            ballConditionMet = ballResult.conditionMet,
            ballConditionReason = ballResult.reason
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
            PokeBalls.getPokeBall(ResourceLocation.fromNamespaceAndPath("cobblemon", ballName))
        } catch (e: Exception) { 
            null 
        }
    }
    
    private fun getBallResult(
        pokeBall: PokeBall?, 
        ballName: String, 
        pokemon: ClientBattlePokemon, 
        turnCount: Int
    ): BallMultiplierCalculator.BallResult {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
            ?: return BallMultiplierCalculator.BallResult(CatchRateConstants.BALL_STANDARD_MULT, false, "")
        val level = minecraft.level
            ?: return BallMultiplierCalculator.BallResult(CatchRateConstants.BALL_STANDARD_MULT, false, "")
        
        if (pokeBall?.catchRateModifier?.isGuaranteed() == true) {
            return BallMultiplierCalculator.BallResult(
                CatchRateConstants.BALL_GUARANTEED_MULT, true,
                BallTranslations.guaranteedCatch(), isGuaranteed = true
            )
        }
        
        val battle = CobblemonClient.battle
        val ctx = BallContextFactory.fromBattlePokemon(pokemon, turnCount, player, level, battle)
        
        return BallMultiplierCalculator.calculate(ballName.lowercase(), ctx)
    }
    
    private fun getHpPercentage(pokemon: ClientBattlePokemon): Double {
        val hpInfo = CatchRateFormula.calculateHpInfo(
            hpValue = pokemon.hpValue,
            maxHpValue = pokemon.maxHp,
            isFlat = pokemon.isHpFlat
        )
        return hpInfo.percentage
    }
}
