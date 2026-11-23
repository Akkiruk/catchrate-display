package com.catchrate

import com.catchrate.CatchRateConstants.LOW_LEVEL_THRESHOLD
import com.catchrate.CatchRateConstants.MAX_CATCH_RATE
import com.catchrate.CatchRateConstants.OUT_OF_BATTLE_MODIFIER
import com.catchrate.CatchRateConstants.SHAKE_CHECKS
import com.catchrate.CatchRateConstants.SHAKE_EXPONENT
import com.catchrate.CatchRateConstants.SHAKE_PROBABILITY_DIVISOR
import com.catchrate.CatchRateConstants.SHAKE_RANDOM_BOUND
import com.catchrate.CatchRateConstants.STATUS_NONE_MULT
import com.catchrate.CatchRateConstants.STATUS_PARA_BURN_POISON_MULT
import com.catchrate.CatchRateConstants.STATUS_SLEEP_FROZEN_MULT
import com.cobblemon.mod.common.api.pokedex.CaughtCount
import com.cobblemon.mod.common.client.CobblemonClient
import net.minecraft.network.chat.Component
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

object CatchRateFormula {
    private val ballNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    fun calculateCatchPercentage(
        baseCatchRate: Float,
        maxHp: Float,
        currentHp: Float,
        ballMultiplier: Float,
        statusMultiplier: Float = STATUS_NONE_MULT,
        levelBonus: Float = 1F,
        inBattle: Boolean = true,
        levelPenalty: Float = 1F,
        externalCatchRateMultiplier: Float = 1F
    ): Float = modifiedRateToPercentage(
        calculateModifiedCatchRate(
            baseCatchRate, maxHp, currentHp, ballMultiplier, statusMultiplier,
            levelBonus, inBattle, levelPenalty, externalCatchRateMultiplier
        )
    )
    
    // Cobblemon rounds this before comparing it to Random.nextInt(65537)
    fun calculateShakeProbability(modifiedCatchRate: Float): Float {
        return when {
            modifiedCatchRate <= 0F -> 0F
            else -> SHAKE_PROBABILITY_DIVISOR / (MAX_CATCH_RATE / modifiedCatchRate).pow(SHAKE_EXPONENT)
        }
    }
    
    // past a modified rate of 255 every shake passes, e.g. Quick Ball on something easy
    fun isGuaranteedByFormula(modifiedCatchRate: Float): Boolean {
        val shakeProbability = calculateShakeProbability(modifiedCatchRate).roundToInt()
        return shakeProbability >= SHAKE_RANDOM_BOUND.toInt()
    }
    
    fun calculateModifiedCatchRate(
        baseCatchRate: Float,
        maxHp: Float,
        currentHp: Float,
        ballMultiplier: Float,
        statusMultiplier: Float = STATUS_NONE_MULT,
        levelBonus: Float = 1F,
        inBattle: Boolean = true,
        levelPenalty: Float = 1F,
        externalCatchRateMultiplier: Float = 1F
    ): Float {
        val battleModifier = if (inBattle) 1F else OUT_OF_BATTLE_MODIFIER
        val hpComponent = (3F * maxHp - 2F * currentHp) * baseCatchRate * battleModifier
        var modifiedRate = (hpComponent * ballMultiplier) / (3F * maxHp)
        modifiedRate *= statusMultiplier * levelBonus * levelPenalty * externalCatchRateMultiplier
        return modifiedRate
    }
    
    fun modifiedRateToPercentage(modifiedCatchRate: Float): Float {
        val shakeProbability = calculateShakeProbability(modifiedCatchRate).roundToInt()
        if (shakeProbability >= SHAKE_RANDOM_BOUND.toInt()) return 100F
        val perShakeProb = shakeProbability.toFloat() / SHAKE_RANDOM_BOUND
        return perShakeProb.pow(SHAKE_CHECKS) * 100F
    }

    // Chance the throw turns into a critical capture, same roll as Cobblemon's
    // CriticalCaptureProvider. Kept out of the main percentage on purpose, the HUD shows it
    // as its own line. Scales with species owned in your Pokedex, zero at 30 or fewer.
    fun critChance(modifiedCatchRate: Float, caught: Int = caughtCount()): Float {
        val mult = when {
            caught <= 30 -> return 0F
            caught <= 150 -> 0.5F
            caught <= 300 -> 1F
            caught <= 450 -> 1.5F
            caught <= 600 -> 2F
            else -> 2.5F
        }
        val c = (modifiedCatchRate * mult / 6F).roundToInt()
        return (c / 256F).coerceIn(0F, 1F)
    }

    // the server syncs the dex to the client, so this is the same count the server rolls with
    private fun caughtCount(): Int = CobblemonClient.clientPokedexData.getGlobalCalculatedValue(CaughtCount)
    
    /**
     * Format catch percentage for display.
     * Shows 100% when the formula guarantees a catch (shakeProbability >= 65537),
     * not just for Master Ball. This matches Cobblemon's actual behavior where
     * high ball multipliers on high-catch-rate Pokemon are genuinely guaranteed.
     */
    fun formatCatchPercentage(percentage: Double, isGuaranteed: Boolean): String {
        return if (isGuaranteed || percentage >= 100.0) {
            "100.0"
        } else {
            // Cap at 99.9% only for catches that are NOT mathematically guaranteed
            String.format(Locale.US, "%.1f", percentage.coerceIn(0.0, 99.9))
        }
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
     * Get the translation key for a status condition.
     * @param statusPath The status identifier path
     * @return Translation key for use with Text.translatable()
     */
    fun getStatusTranslationKey(statusPath: String?): String {
        return when (statusPath?.lowercase()) {
            "sleep" -> "catchrate.status.asleep"
            "frozen" -> "catchrate.status.frozen"
            "paralysis" -> "catchrate.status.paralyzed"
            "poison" -> "catchrate.status.poisoned"
            "poisonbadly" -> "catchrate.status.badly_poisoned"
            "burn" -> "catchrate.status.burned"
            else -> "catchrate.status.none"
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
     * Format a ball name using Cobblemon's item translations for proper localization.
     */
    fun formatBallName(name: String): String {
        return ballNameCache.getOrPut(name) {
            val parts = name.split(":", limit = 2)
            val namespace = if (parts.size == 2) parts[0] else "cobblemon"
            val cleanName = if (parts.size == 2) parts[1] else parts[0]
            val translationKey = "item.$namespace.$cleanName"
            val translated = Component.translatable(translationKey).string
            if (translated != translationKey) {
                translated
            } else {
                cleanName.replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            }
        }
    }
    
}
