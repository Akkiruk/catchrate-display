package com.catchrate

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokeball.PokeBall
import net.minecraft.client.Minecraft
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
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
        return try {
            calculateCatchRateInternal(pokemon, itemStack, turnCount, playerHighestLevel, inBattle)
        } catch (e: Throwable) {
            CatchRateMod.debugOnChange("CalcErr", "fallback", "calculateCatchRate failed: ${e.javaClass.simpleName}: ${e.message}")
            val baseCatchRate = try { SpeciesCatchRateCache.getCatchRate(pokemon.species) } catch (_: Throwable) { 45 }
            val ballName = try { getPokeBallFromItem(itemStack)?.name?.path } catch (_: Throwable) { null }
                ?: itemStack.item.toString().substringAfter(":").substringBefore("}")
            CatchRateResult(
                percentage = CatchRateFormula.modifiedRateToPercentage(baseCatchRate.toFloat()).toDouble().coerceIn(0.0, 100.0),
                hpPercentage = 100.0,
                statusMultiplier = 1.0,
                ballMultiplier = 1.0,
                baseCatchRate = baseCatchRate,
                statusName = "",
                ballName = ballName,
                turnCount = turnCount
            )
        }
    }

    private fun calculateCatchRateInternal(
        pokemon: ClientBattlePokemon,
        itemStack: ItemStack,
        turnCount: Int = 1,
        playerHighestLevel: Int? = null,
        inBattle: Boolean = true
    ): CatchRateResult {
        val pokeBall = getPokeBallFromItem(itemStack)
        val ballName = pokeBall?.name?.path ?: itemStack.item.toString().substringAfter(":").substringBefore("}")
        
        val baseCatchRate = SpeciesCatchRateCache.getCatchRate(pokemon.species)
        
        // Calculate HP using shared formula
        val hpInfo = CatchRateFormula.calculateHpInfo(
            hpValue = pokemon.hpValue,
            maxHpValue = pokemon.maxHp,
            isFlat = pokemon.isHpFlat
        )
        
        val catchRate = baseCatchRate.toFloat()
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
        val isFormulaGuaranteed = ballResult.isGuaranteed || CatchRateFormula.isGuaranteedByFormula(modifiedCatchRate)
        val captureChance = if (isFormulaGuaranteed) 100F else CatchRateFormula.modifiedRateToPercentage(modifiedCatchRate)
        
        CatchRateMod.debugOnChange("Calc",
            "${pokemon.species.name}_${ballName}_${hpInfo.currentHp.toInt()}_${statusPath}_${bonusStatus}",
            "${pokemon.species.name} Lv${level} | Ball=$ballName ${ballBonus}x${if (ballResult.conditionMet) " (met)" else ""} | " +
            "HP=${String.format("%.1f", hpInfo.percentage)}% (${hpInfo.currentHp.toInt()}/${hpInfo.maxHp.toInt()}) | " +
            "Status=${CatchRateFormula.getStatusDisplayName(statusPath)} ${bonusStatus}x | " +
            "Result=${String.format("%.2f", captureChance)}% (mod=${String.format("%.1f", modifiedCatchRate)}, base=$catchRate)"
        )
        
        return CatchRateResult(
            percentage = captureChance.toDouble().coerceIn(0.0, 100.0),
            hpPercentage = hpInfo.percentage,
            statusMultiplier = bonusStatus.toDouble(),
            ballMultiplier = ballBonus.toDouble(),
            baseCatchRate = catchRate.toInt(),
            statusName = statusPath ?: "",
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
     * Also checks inside container items (e.g., Berry Pouch Pokéball Launcher)
     * for a selected pokéball using vanilla DataComponents.
     */
    fun getPokeBallFromItem(itemStack: ItemStack): PokeBall? {
        val item = itemStack.item
        if (item is PokeBallItem) return item.pokeBall
        
        // Check for a selected pokéball in the item's container (Pokéball Launcher, etc.)
        getSelectedBallFromContainer(itemStack)?.let { return it }
        
        val ballName = itemStack.item.toString().substringAfter(":").substringBefore("}").trim()
        return try {
            PokeBalls.getPokeBall(ResourceLocation.fromNamespaceAndPath("cobblemon", ballName))
        } catch (e: Throwable) { 
            null 
        }
    }
    
    /**
     * Try to extract the selected PokeBall from a container item's data components.
     * Works with any mod that stores pokéballs in CONTAINER and tracks selection via CUSTOM_DATA.
     */
    private fun getSelectedBallFromContainer(itemStack: ItemStack): PokeBall? {
        val container = itemStack.get(DataComponents.CONTAINER) ?: return null
        
        val items = NonNullList.withSize(9, ItemStack.EMPTY)
        container.copyInto(items)
        
        // Get selected index from custom data, defaulting to first ball if not set
        val selectedIndex = itemStack.get(DataComponents.CUSTOM_DATA)
            ?.copyTag()
            ?.takeIf { it.contains("SelectedIndex") }
            ?.getInt("SelectedIndex")
            ?: items.indexOfFirst { (it.item as? PokeBallItem) != null }.takeIf { it >= 0 }
            ?: return null
        
        if (selectedIndex < 0 || selectedIndex >= items.size) return null
        val selected = items[selectedIndex]
        if (selected.isEmpty) return null
        
        return (selected.item as? PokeBallItem)?.pokeBall
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
        
        // All guaranteed checks happen inside BallMultiplierCalculator.calculate()
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
