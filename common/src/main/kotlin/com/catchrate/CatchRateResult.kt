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
    val modifiedCatchRate: Double = 0.0
)
