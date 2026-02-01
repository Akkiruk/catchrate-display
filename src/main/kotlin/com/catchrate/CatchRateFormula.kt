package com.catchrate

import com.catchrate.CatchRateConstants.LOW_LEVEL_THRESHOLD
import com.catchrate.CatchRateConstants.MAX_CATCH_RATE
import com.catchrate.CatchRateConstants.OUT_OF_BATTLE_MODIFIER
import com.catchrate.CatchRateConstants.SHAKE_CHECKS
import com.catchrate.CatchRateConstants.SHAKE_EXPONENT
import com.catchrate.CatchRateConstants.SHAKE_PROBABILITY_DIVISOR
import com.catchrate.CatchRateConstants.STATUS_NONE_MULT
import com.catchrate.CatchRateConstants.STATUS_PARA_BURN_POISON_MULT
import com.catchrate.CatchRateConstants.STATUS_SLEEP_FROZEN_MULT
import kotlin.math.max
import kotlin.math.pow

/**
 * Single source of truth for all catch rate calculations.
 * Consolidates the catch formula, status multipliers, HP calculations, and related utilities.
 * 
 * This eliminates duplication across CatchRateCalculator, BallComparisonCalculator, 
 * and CatchRateServerNetworking.
 */
object CatchRateFormula {
    
    // ==================== MAIN CATCH RATE CALCULATION ====================
    
    /**
     * Calculate the final catch percentage using the Gen 6+ formula.
     * 
     * @param baseCatchRate The Pokemon's base catch rate (species-dependent)
     * @param maxHp The Pokemon's maximum HP
     * @param currentHp The Pokemon's current HP
     * @param ballMultiplier The ball's catch rate multiplier
     * @param statusMultiplier The status condition multiplier
     * @param levelBonus Low-level bonus multiplier (for Pokemon under level 13)
     * @param inBattle Whether the Pokemon is being caught in battle (false = 0.5x penalty)
     * @param levelPenalty Optional penalty for catching Pokemon higher level than your team
     * @return Catch percentage from 0.0 to 100.0
     */
    fun calculateCatchPercentage(
        baseCatchRate: Float,
        maxHp: Float,
        currentHp: Float,
        ballMultiplier: Float,
        statusMultiplier: Float = STATUS_NONE_MULT,
        levelBonus: Float = 1F,
        inBattle: Boolean = true,
        levelPenalty: Float = 1F
    ): Float {
        val battleModifier = if (inBattle) 1F else OUT_OF_BATTLE_MODIFIER
        
        // HP component: (3*maxHP - 2*currentHP) * catchRate * battleMod
        val hpComponent = (3F * maxHp - 2F * currentHp) * baseCatchRate * battleModifier
        
        // Modified catch rate with ball bonus
        var modifiedRate = (hpComponent * ballMultiplier) / (3F * maxHp)
        modifiedRate *= statusMultiplier * levelBonus * levelPenalty
        
        // Calculate shake probability
        val shakeProbability = calculateShakeProbability(modifiedRate)
        
        // Final catch chance = (shakeProbability / 65536)^4 * 100
        return (shakeProbability / SHAKE_PROBABILITY_DIVISOR).pow(SHAKE_CHECKS) * 100F
    }
    
    /**
     * Calculate shake probability from modified catch rate.
     * Used when you need the intermediate value for custom mutators.
     */
    fun calculateShakeProbability(modifiedCatchRate: Float): Float {
        return when {
            modifiedCatchRate >= MAX_CATCH_RATE -> SHAKE_PROBABILITY_DIVISOR
            modifiedCatchRate <= 0F -> 0F
            else -> SHAKE_PROBABILITY_DIVISOR / (MAX_CATCH_RATE / modifiedCatchRate).pow(SHAKE_EXPONENT)
        }
    }
    
    /**
     * Calculate modified catch rate (before shake probability).
     * Use this when you need to apply custom behavior mutators (like Cobblemon's API).
     */
    fun calculateModifiedCatchRate(
        baseCatchRate: Float,
        maxHp: Float,
        currentHp: Float,
        ballMultiplier: Float,
        statusMultiplier: Float = STATUS_NONE_MULT,
        levelBonus: Float = 1F,
        inBattle: Boolean = true,
        levelPenalty: Float = 1F
    ): Float {
        val battleModifier = if (inBattle) 1F else OUT_OF_BATTLE_MODIFIER
        val hpComponent = (3F * maxHp - 2F * currentHp) * baseCatchRate * battleModifier
        var modifiedRate = (hpComponent * ballMultiplier) / (3F * maxHp)
        modifiedRate *= statusMultiplier * levelBonus * levelPenalty
        return modifiedRate
    }
    
    /**
     * Convert modified catch rate to final percentage.
     * Use after applying Cobblemon's behavior mutator.
     */
    fun modifiedRateToPercentage(modifiedCatchRate: Float): Float {
        val shakeProbability = calculateShakeProbability(modifiedCatchRate)
        return (shakeProbability / SHAKE_PROBABILITY_DIVISOR).pow(SHAKE_CHECKS) * 100F
    }
    
    // ==================== STATUS MULTIPLIERS ====================
    
    /**
     * Get the catch rate multiplier for a status condition.
     * @param statusPath The status identifier path (e.g., "sleep", "paralysis")
     */
    fun getStatusMultiplier(statusPath: String?): Float {
        return when (statusPath?.lowercase()) {
            "sleep", "frozen" -> STATUS_SLEEP_FROZEN_MULT
            "paralysis", "burn", "poison", "poisonbadly" -> STATUS_PARA_BURN_POISON_MULT
            else -> STATUS_NONE_MULT
        }
    }
    
    /**
     * Get the display name for a status condition.
     * @param statusPath The status identifier path
     */
    fun getStatusDisplayName(statusPath: String?): String {
        return when (statusPath?.lowercase()) {
            "sleep" -> "Asleep"
            "frozen" -> "Frozen"
            "paralysis" -> "Paralyzed"
            "poison" -> "Poisoned"
            "poisonbadly" -> "Badly Poisoned"
            "burn" -> "Burned"
            else -> "None"
        }
    }
    
    /**
     * Get the status icon for UI display.
     */
    fun getStatusIcon(statusPath: String?): String {
        return when (statusPath?.lowercase()) {
            "sleep", "asleep" -> "💤"
            "frozen" -> "❄"
            "paralysis", "paralyzed" -> "⚡"
            "burn", "burned" -> "🔥"
            "poison", "poisoned", "poisonbadly", "badly poisoned" -> "☠"
            else -> "●"
        }
    }
    
    // ==================== LEVEL BONUS ====================
    
    /**
     * Calculate the low-level catch bonus for Pokemon under level 13.
     * Cobblemon uses integer division: (36 - 2*level) / 10
     */
    fun getLowLevelBonus(level: Int): Float {
        return if (level < LOW_LEVEL_THRESHOLD) {
            max((36 - (2 * level)) / 10, 1).toFloat()
        } else {
            1F
        }
    }
    
    /**
     * Calculate the level penalty when catching Pokemon higher level than your team.
     * @param pokemonLevel The wild Pokemon's level
     * @param playerHighestLevel The highest level Pokemon on the player's team
     * @return Multiplier from 0.1 to 1.0
     */
    fun getLevelPenalty(pokemonLevel: Int, playerHighestLevel: Int?): Float {
        if (playerHighestLevel == null || playerHighestLevel >= pokemonLevel) {
            return 1F
        }
        val penalty = (pokemonLevel - playerHighestLevel) / 50F
        return max(0.1F, 1F - penalty.coerceAtMost(0.9F))
    }
    
    // ==================== HP UTILITIES ====================
    
    /**
     * HP calculation result containing current, max, and percentage.
     */
    data class HpInfo(
        val currentHp: Float,
        val maxHp: Float,
        val percentage: Double
    )
    
    /**
     * Calculate HP info from raw values, handling flat vs percentage HP formats.
     * 
     * @param hpValue The HP value (either flat HP or percentage as decimal)
     * @param maxHpValue The max HP value
     * @param isFlat Whether hpValue is a flat value (true) or percentage (false)
     */
    fun calculateHpInfo(hpValue: Float, maxHpValue: Float, isFlat: Boolean): HpInfo {
        return if (isFlat) {
            val maxHp = if (maxHpValue > 0) maxHpValue else 1F
            val percentage = (hpValue / maxHp * 100.0).coerceIn(0.0, 100.0)
            HpInfo(hpValue, maxHp, percentage)
        } else {
            val maxHp = if (maxHpValue > 0) maxHpValue else 100F
            val currentHp = hpValue * maxHp
            val percentage = (hpValue * 100.0).coerceIn(0.0, 100.0)
            HpInfo(currentHp, maxHp, percentage)
        }
    }
    
    // ==================== FORMATTING UTILITIES ====================
    
    /**
     * Format a ball name for display (e.g., "ultra_ball" -> "Ultra Ball").
     */
    fun formatBallName(name: String): String {
        return name.replace("cobblemon:", "")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
    
    /**
     * Format a ball name without "Ball" suffix for compact display.
     */
    fun formatBallNameCompact(name: String): String {
        return formatBallName(name).replace(" Ball", "")
    }
}
