package com.catchrate

import com.catchrate.api.CatchRateModifierRegistry
import com.catchrate.api.CatchRateModifierResult
import net.minecraft.world.entity.player.Player

/**
 * Combines the built-in CobbleCuisine compat with every modifier registered through the
 * public [CatchRateModifierRegistry] API into a single list for display and math.
 */
object ExternalCatchRateModifiers {

    fun collect(player: Player?): List<CatchRateModifierResult> {
        if (player == null) return emptyList()

        val cobbleCuisineMultiplier = CobbleCuisineCompat.getCatchRateMultiplier(player)
        val builtIn = if (cobbleCuisineMultiplier != 1F) {
            listOf(CatchRateModifierResult(cobbleCuisineMultiplier, "CobbleCuisine catch boost"))
        } else {
            emptyList()
        }

        return builtIn + CatchRateModifierRegistry.collectActive(player)
    }

    fun combinedMultiplier(modifiers: List<CatchRateModifierResult>): Float =
        modifiers.fold(1F) { acc, modifier -> acc * modifier.multiplier }
}
