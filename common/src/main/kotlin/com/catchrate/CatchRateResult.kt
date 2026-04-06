package com.catchrate

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
    val modifiedCatchRate: Double = 0.0,
    val ballConditionMet: Boolean = true,
    val ballConditionReason: String = "",
    /** True when the base catch rate is a fallback estimate (species not found in local data). */
    val isCatchRateEstimate: Boolean = false,
    /** Additional catch-rate multiplier from detected client-side compat effects. */
    val externalCatchRateMultiplier: Double = 1.0,
    val externalCatchRateReason: String = "",
    /** True when the displayed catch prediction is based on trustworthy client target data. */
    val isReliableGuaranteedPrediction: Boolean = false
)
