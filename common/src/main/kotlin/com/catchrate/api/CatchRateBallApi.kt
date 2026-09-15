package com.catchrate.api

import com.catchrate.CatchRateMod
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-only snapshot of the target Pokémon/situation, handed to ball providers.
 * Deliberately separate from BallMultiplierCalculator's internal context so that
 * internal calculator refactors never break this public API's compatibility.
 */
data class BallApiContext(
    val speciesId: String,
    val level: Int,
    val primaryType: String,
    val secondaryType: String?,
    val weight: Float,
    val baseSpeed: Int,
    val labels: List<String>,
    val statusPath: String?,
    val inBattle: Boolean,
    val turnCount: Int,
    val pokemonAspects: Set<String> = emptySet()
)

/** Result of a custom ball's evaluation: a multiplier and the reason shown next to it. */
data class BallApiResult(
    val multiplier: Float,
    val conditionMet: Boolean,
    val reason: String,
    val isGuaranteed: Boolean = false
)

/**
 * Implemented by other mods to teach CatchRateDisplay about a custom Cobblemon ball.
 * Return null if a result can't be computed right now - the ball falls back to whatever
 * CatchRateDisplay would otherwise do for an unrecognized ball.
 */
fun interface CatchRateBallProvider {
    fun evaluate(ctx: BallApiContext): BallApiResult?
}

/**
 * Public API: other mods register a [CatchRateBallProvider] here, keyed by their ball
 * item's own ResourceLocation, to teach CatchRateDisplay about a custom Cobblemon ball -
 * no compat work or cooperation from us required. The ball automatically shows up in the
 * ball comparison panel whenever the item is installed.
 *
 * Register once during mod init, e.g.:
 * ```
 * CatchRateBallRegistry.registerBall(
 *     ResourceLocation.fromNamespaceAndPath("mymod", "quantum_ball")
 * ) { ctx ->
 *     BallApiResult(multiplier = 3.5F, conditionMet = true, reason = "Quantum resonance")
 * }
 * ```
 */
object CatchRateBallRegistry {

    private val providers = ConcurrentHashMap<ResourceLocation, CatchRateBallProvider>()
    private val failedProviders = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun registerBall(id: ResourceLocation, provider: CatchRateBallProvider) {
        providers[id] = provider
        failedProviders.remove(id)
    }

    fun unregisterBall(id: ResourceLocation) {
        providers.remove(id)
        failedProviders.remove(id)
    }

    /** Every ball ID currently registered, regardless of whether the item is installed. */
    fun registeredIds(): Set<ResourceLocation> = providers.keys

    /** Evaluates the provider for [id], isolating a throwing provider after its first failure. */
    fun evaluate(id: ResourceLocation, ctx: BallApiContext): BallApiResult? {
        val provider = providers[id] ?: return null
        return try {
            provider.evaluate(ctx)
        } catch (e: Throwable) {
            if (failedProviders.add(id)) {
                CatchRateMod.LOGGER.warn(
                    "[CatchRateDisplay] Ball provider '$id' threw ${e.javaClass.simpleName}: ${e.message}; ignoring it going forward"
                )
            }
            null
        }
    }
}
