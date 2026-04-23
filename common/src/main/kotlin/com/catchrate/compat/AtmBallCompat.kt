package com.catchrate.compat

import com.catchrate.BallMultiplierCalculator.BallContext
import com.catchrate.BallMultiplierCalculator.BallResult
import com.catchrate.BallTranslations
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import java.util.LinkedHashSet
import java.util.Locale

object AtmBallCompat {
    private val customBallIds = listOf(
        "allthemodium_ball",
        "ancient_allthemodium_ball",
        "vibranium_ball",
        "ancient_vibranium_ball",
        "unobtainium_ball",
        "ancient_unobtainium_ball",
        "soul_lava_ball",
        "ancient_soul_lava_ball"
    )

    private val customBallIdSet = customBallIds.toSet()

    @Volatile
    private var cachedInstalledBallIds: List<String>? = null

    fun getCustomResult(ballId: String?, ctx: BallContext?): BallResult? {
        if (ballId == null || ctx == null) {
            return null
        }

        return when (normalizeBallId(ballId) ?: return null) {
            "allthemodium_ball", "ancient_allthemodium_ball" -> fixed(4F)
            "vibranium_ball", "ancient_vibranium_ball" -> fixed(6F)
            "unobtainium_ball", "ancient_unobtainium_ball" -> {
                val boosted = hasLegendaryOrPiglich(ctx)
                captureEffect(if (boosted) 30F else 10F, boosted)
            }
            "soul_lava_ball", "ancient_soul_lava_ball" -> soulLava(ctx)
            else -> null
        }
    }

    fun extendComparableBalls(base: List<String>): List<String> {
        val installedBallIds = installedBallIds()
        if (installedBallIds.isEmpty()) {
            return base
        }

        val values = LinkedHashSet<String>()
        values.addAll(base)
        values.addAll(installedBallIds)
        return values.toList()
    }

    fun isSupportedBall(ballId: String?): Boolean {
        val normalized = normalizeBallId(ballId) ?: return false
        return normalized in customBallIdSet
    }

    private fun installedBallIds(): List<String> {
        cachedInstalledBallIds?.let { return it }

        val registeredPaths = BuiltInRegistries.ITEM.keySet()
            .mapTo(mutableSetOf()) { it.path.lowercase(Locale.ROOT) }
        val installed = customBallIds.filter { it in registeredPaths }
        cachedInstalledBallIds = installed
        return installed
    }

    private fun fixed(multiplier: Float): BallResult {
        return BallResult(multiplier, true, BallTranslations.multiplierAlways())
    }

    private fun captureEffect(multiplier: Float, conditionMet: Boolean): BallResult {
        return BallResult(multiplier, conditionMet, BallTranslations.specialEffect())
    }

    private fun conditional(multiplier: Float, conditionMet: Boolean): BallResult {
        return BallResult(multiplier, conditionMet, BallTranslations.multiplierAlways())
    }

    private fun soulLava(ctx: BallContext): BallResult {
        val fire = hasType(ctx, "fire")
        val water = hasType(ctx, "water")

        return when {
            fire -> conditional(50F, true)
            water -> conditional(0F, true)
            else -> conditional(2F, false)
        }
    }

    private fun hasLegendaryOrPiglich(ctx: BallContext): Boolean {
        if (matchesId(ctx.speciesId, "piglich")) {
            return true
        }

        return ctx.labels.any { it.equals("legendary", ignoreCase = true) }
    }

    private fun hasType(ctx: BallContext, type: String): Boolean {
        return matchesId(ctx.primaryType, type) || matchesId(ctx.secondaryType, type)
    }

    private fun matchesId(value: String?, expectedPath: String): Boolean {
        return normalizeBallId(value) == expectedPath
    }

    private fun normalizeBallId(value: String?): String? {
        val raw = value
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return ResourceLocation.tryParse(raw)?.path?.lowercase(Locale.ROOT) ?: raw.substringAfterLast(':')
    }
}