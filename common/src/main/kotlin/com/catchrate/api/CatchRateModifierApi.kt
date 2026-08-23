package com.catchrate.api

import com.catchrate.CatchRateMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * A single active catch-rate modifier: a multiplier and the short label shown next to it
 * (e.g. "Carrot Boost" for a 2x multiplier from eating a carrot).
 */
data class CatchRateModifierResult(val multiplier: Float, val description: String)

/**
 * Implemented by other mods to add their own catch-rate compat.
 * Return null when the modifier isn't currently active (e.g. the buff has worn off) -
 * no row is shown and no multiplier is applied.
 */
fun interface CatchRateModifierProvider {
    fun evaluate(player: Player): CatchRateModifierResult?
}

/**
 * Public API: other mods register a [CatchRateModifierProvider] here to add a row to the
 * catch rate HUD and have their multiplier folded into the displayed percentage, without
 * requiring any compat work from CatchRateDisplay itself.
 *
 * Register once during mod init, e.g.:
 * ```
 * CatchRateModifierRegistry.registerModifier(
 *     ResourceLocation.fromNamespaceAndPath("mymod", "carrot_boost")
 * ) { player ->
 *     if (player.hasEffect(MY_CARROT_EFFECT)) CatchRateModifierResult(2f, "Carrot Boost") else null
 * }
 * ```
 */
object CatchRateModifierRegistry {

    private val providers = ConcurrentHashMap<ResourceLocation, CatchRateModifierProvider>()
    private val failedProviders = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun registerModifier(id: ResourceLocation, provider: CatchRateModifierProvider) {
        providers[id] = provider
        failedProviders.remove(id)
    }

    fun unregisterModifier(id: ResourceLocation) {
        providers.remove(id)
        failedProviders.remove(id)
    }

    /** Evaluates every registered provider for [player], skipping any that throw or are inactive. */
    fun collectActive(player: Player): List<CatchRateModifierResult> {
        if (providers.isEmpty()) return emptyList()
        val results = mutableListOf<CatchRateModifierResult>()
        for ((id, provider) in providers) {
            try {
                provider.evaluate(player)?.let(results::add)
            } catch (e: Throwable) {
                if (failedProviders.add(id)) {
                    CatchRateMod.LOGGER.warn(
                        "[CatchRateDisplay] Modifier provider '$id' threw ${e.javaClass.simpleName}: ${e.message}; ignoring it going forward"
                    )
                }
            }
        }
        return results
    }
}
