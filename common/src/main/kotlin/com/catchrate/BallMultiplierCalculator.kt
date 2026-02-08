package com.catchrate

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.pokemon.Gender
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Translation helper for ball-related strings.
 */
object BallTranslations {
    fun guaranteedCatch() = Component.translatable("catchrate.ball.guaranteed_catch").string
    fun multiplierAlways() = Component.translatable("catchrate.ball.multiplier_always").string
    fun specialEffect() = Component.translatable("catchrate.ball.special_effect").string
    fun ancient() = Component.translatable("catchrate.ball.ancient").string
    
    fun quickNotInBattle() = Component.translatable("catchrate.ball.quick.not_in_battle").string
    fun quickEffective() = Component.translatable("catchrate.ball.quick.effective").string
    fun quickIneffective() = Component.translatable("catchrate.ball.quick.ineffective").string
    
    fun timerTurnInfo(turn: Int) = Component.translatable("catchrate.ball.timer.turn_info", turn).string
    fun timerScales() = Component.translatable("catchrate.ball.timer.scales").string
    
    fun duskDarkArea(level: Int) = Component.translatable("catchrate.ball.dusk.dark_area", level).string
    fun duskNeedDarkness() = Component.translatable("catchrate.ball.dusk.need_darkness").string
    
    fun diveUnderwater() = Component.translatable("catchrate.ball.dive.underwater").string
    fun diveNeedUnderwater() = Component.translatable("catchrate.ball.dive.need_underwater").string
    
    fun moonNotNight() = Component.translatable("catchrate.ball.moon.not_night").string
    fun moonNightPhase(phase: Int) = Component.translatable("catchrate.ball.moon.night_phase", phase).string
    
    fun netEffective() = Component.translatable("catchrate.ball.net.effective").string
    fun netIneffective() = Component.translatable("catchrate.ball.net.ineffective").string
    
    fun nestEffective(level: Int) = Component.translatable("catchrate.ball.nest.effective", level).string
    fun nestIneffective() = Component.translatable("catchrate.ball.nest.ineffective").string
    
    fun fastEffective(speed: Int) = Component.translatable("catchrate.ball.fast.effective", speed).string
    fun fastIneffective() = Component.translatable("catchrate.ball.fast.ineffective").string
    
    fun heavyWeight(kg: Float) = Component.translatable("catchrate.ball.heavy.weight", kg).string
    
    fun beastUltraBeast() = Component.translatable("catchrate.ball.beast.ultra_beast").string
    fun beastPenalty() = Component.translatable("catchrate.ball.beast.penalty").string
    
    fun dreamSleeping() = Component.translatable("catchrate.ball.dream.sleeping").string
    fun dreamNeedSleep() = Component.translatable("catchrate.ball.dream.need_sleep").string
    
    fun loveNoBattler() = Component.translatable("catchrate.ball.love.no_battler").string
    fun loveNeedBattler() = Component.translatable("catchrate.ball.love.need_battler").string
    fun loveWildGenderless() = Component.translatable("catchrate.ball.love.wild_genderless").string
    fun loveBattlerGenderless() = Component.translatable("catchrate.ball.love.battler_genderless").string
    fun loveNeedOpposite() = Component.translatable("catchrate.ball.love.need_opposite").string
    fun loveSameSpecies(gender: String) = Component.translatable("catchrate.ball.love.same_species", gender).string
    fun loveOppositeGender(gender: String) = Component.translatable("catchrate.ball.love.opposite_gender", gender).string
    
    fun levelEffective() = Component.translatable("catchrate.ball.level.effective").string
    fun levelIneffective() = Component.translatable("catchrate.ball.level.ineffective").string
    fun repeatEffective() = Component.translatable("catchrate.ball.repeat.effective").string
    fun repeatIneffective() = Component.translatable("catchrate.ball.repeat.ineffective").string
    
    fun lureEffective() = Component.translatable("catchrate.ball.lure.effective").string
    fun lureIneffective() = Component.translatable("catchrate.ball.lure.ineffective").string
}

/**
 * Unified ball multiplier calculation system.
 * Single source of truth for all ball bonus logic.
 */
object BallMultiplierCalculator {

    data class BallContext(
        val speciesId: String,
        val level: Int,
        val gender: Gender?,
        val primaryType: String,
        val secondaryType: String?,
        val weight: Float,
        val baseSpeed: Int,
        val labels: List<String>,
        val statusPath: String?,
        val lightLevel: Int,
        val isNight: Boolean,
        val moonPhase: Int,
        val isPlayerUnderwater: Boolean,
        val inBattle: Boolean,
        val turnCount: Int,
        val activeBattler: PartyMember?,
        val hasCaughtSpecies: Boolean? = null,
        val pokemonAspects: Set<String> = emptySet()
    )

    data class PartyMember(
        val speciesId: String,
        val gender: Gender?,
        val level: Int = 0
    )

    data class BallResult(
        val multiplier: Float,
        val conditionMet: Boolean,
        val reason: String,
        val isGuaranteed: Boolean = false
    )

    // Cache PokeBall lookups to avoid repeated ResourceLocation creation + registry lookups
    private val pokeBallCache = HashMap<String, PokeBall?>()
    
    fun calculate(ballId: String, ctx: BallContext): BallResult {
        val lower = ballId.lowercase()
        
        val pokeBall = pokeBallCache.getOrPut(lower) {
            try {
                PokeBalls.getPokeBall(ResourceLocation.fromNamespaceAndPath("cobblemon", lower))
            } catch (e: Exception) { null }
        }
        
        if (pokeBall?.catchRateModifier?.isGuaranteed() == true || lower.contains("master")) {
            CatchRateMod.debugBall(lower, "guaranteed catch", 255F, true)
            return BallResult(255F, true, BallTranslations.guaranteedCatch(), isGuaranteed = true)
        }
        
        if (pokeBall?.ancient == true) {
            CatchRateMod.debugBall(lower, "ancient ball", 1F, true)
            return calculateAncientBall(lower)
        }
        
        val result = when (lower) {
            "quick_ball" -> calculateQuickBall(ctx)
            "timer_ball" -> calculateTimerBall(ctx)
            "ultra_ball" -> BallResult(2F, true, BallTranslations.multiplierAlways())
            "great_ball" -> BallResult(1.5F, true, BallTranslations.multiplierAlways())
            "poke_ball" -> BallResult(1F, true, BallTranslations.multiplierAlways())
            "premier_ball" -> BallResult(1F, true, BallTranslations.multiplierAlways())
            "safari_ball" -> BallResult(1.5F, true, BallTranslations.multiplierAlways())
            "sport_ball" -> BallResult(1.5F, true, BallTranslations.multiplierAlways())
            "dusk_ball" -> calculateDuskBall(ctx)
            "dive_ball" -> calculateDiveBall(ctx)
            "moon_ball" -> calculateMoonBall(ctx)
            "net_ball" -> calculateNetBall(ctx)
            "nest_ball" -> calculateNestBall(ctx)
            "fast_ball" -> calculateFastBall(ctx)
            "heavy_ball" -> calculateHeavyBall(ctx)
            "beast_ball" -> calculateBeastBall(ctx)
            "dream_ball" -> calculateDreamBall(ctx)
            "love_ball" -> calculateLoveBall(ctx)
            "level_ball" -> calculateLevelBall(ctx)
            "repeat_ball" -> calculateRepeatBall(ctx)
            "lure_ball" -> calculateLureBall(ctx)
            "friend_ball", "luxury_ball", "heal_ball" -> 
                BallResult(1F, true, BallTranslations.specialEffect())
            else -> BallResult(1F, true, "")
        }
        
        CatchRateMod.debugBall(lower, result.reason, result.multiplier, result.conditionMet)
        return result
    }
    
    private fun calculateAncientBall(lower: String): BallResult {
        return BallResult(1F, true, BallTranslations.ancient())
    }
    
    private fun calculateQuickBall(ctx: BallContext): BallResult {
        if (!ctx.inBattle) return BallResult(1F, false, BallTranslations.quickNotInBattle())
        val effective = ctx.turnCount == 1
        val mult = if (effective) 5F else 1F
        return BallResult(mult, effective, if (effective) BallTranslations.quickEffective() else BallTranslations.quickIneffective())
    }
    
    private fun calculateTimerBall(ctx: BallContext): BallResult {
        val mult = if (ctx.inBattle) (ctx.turnCount * (1229F / 4096F) + 1F).coerceAtMost(4F) else 1F
        val effective = mult > 1.01f
        val reason = if (ctx.inBattle) BallTranslations.timerTurnInfo(ctx.turnCount) else BallTranslations.timerScales()
        return BallResult(mult, effective, reason)
    }
    
    private fun calculateDuskBall(ctx: BallContext): BallResult {
        val mult = when {
            ctx.lightLevel == 0 -> 3.5F
            ctx.lightLevel <= 7 -> 3F
            else -> 1F
        }
        val effective = ctx.lightLevel <= 7
        return BallResult(mult, effective, if (effective) BallTranslations.duskDarkArea(ctx.lightLevel) else BallTranslations.duskNeedDarkness())
    }
    
    private fun calculateDiveBall(ctx: BallContext): BallResult {
        val mult = if (ctx.isPlayerUnderwater) 3.5F else 1F
        return BallResult(mult, ctx.isPlayerUnderwater, 
            if (ctx.isPlayerUnderwater) BallTranslations.diveUnderwater() else BallTranslations.diveNeedUnderwater())
    }
    
    private fun calculateMoonBall(ctx: BallContext): BallResult {
        if (!ctx.isNight) return BallResult(1F, false, BallTranslations.moonNotNight())
        val mult = when (ctx.moonPhase) {
            0 -> 4F
            1, 7 -> 2.5F
            2, 6 -> 1.5F
            else -> 1F
        }
        return BallResult(mult, mult > 1F, BallTranslations.moonNightPhase(ctx.moonPhase))
    }
    
    private fun calculateNetBall(ctx: BallContext): BallResult {
        val types = listOf(ctx.primaryType, ctx.secondaryType).filterNotNull().map { it.lowercase() }
        val effective = types.any { it == "bug" || it == "water" }
        return BallResult(if (effective) 3F else 1F, effective, if (effective) BallTranslations.netEffective() else BallTranslations.netIneffective())
    }
    
    private fun calculateNestBall(ctx: BallContext): BallResult {
        val effective = ctx.level < 30
        val mult = if (effective) ((41 - ctx.level) / 10F).coerceAtLeast(1F) else 1F
        return BallResult(mult, effective, if (effective) BallTranslations.nestEffective(ctx.level) else BallTranslations.nestIneffective())
    }
    
    private fun calculateFastBall(ctx: BallContext): BallResult {
        val effective = ctx.baseSpeed >= 100
        return BallResult(if (effective) 4F else 1F, effective, if (effective) BallTranslations.fastEffective(ctx.baseSpeed) else BallTranslations.fastIneffective())
    }
    
    private fun calculateHeavyBall(ctx: BallContext): BallResult {
        val mult = when {
            ctx.weight >= 3000 -> 4F
            ctx.weight >= 2000 -> 2.5F
            ctx.weight >= 1000 -> 1.5F
            else -> 1F
        }
        return BallResult(mult, mult > 1F, BallTranslations.heavyWeight(ctx.weight / 10f))
    }
    
    private fun calculateBeastBall(ctx: BallContext): BallResult {
        val isUB = ctx.labels.any { it.lowercase() == "ultra_beast" }
        return BallResult(if (isUB) 5F else 0.1F, isUB, if (isUB) BallTranslations.beastUltraBeast() else BallTranslations.beastPenalty())
    }
    
    private fun calculateDreamBall(ctx: BallContext): BallResult {
        val asleep = ctx.statusPath == "sleep"
        return BallResult(if (asleep) 4F else 1F, asleep, if (asleep) BallTranslations.dreamSleeping() else BallTranslations.dreamNeedSleep())
    }
    
    private fun calculateLoveBall(ctx: BallContext): BallResult {
        val activeBattler = ctx.activeBattler
            ?: return BallResult(1F, false, if (ctx.inBattle) BallTranslations.loveNoBattler() else BallTranslations.loveNeedBattler())
        
        val wildGender = ctx.gender
        if (wildGender == null || wildGender == Gender.GENDERLESS)
            return BallResult(1F, false, BallTranslations.loveWildGenderless())
        
        val battlerGender = activeBattler.gender
        if (battlerGender == null || battlerGender == Gender.GENDERLESS)
            return BallResult(1F, false, BallTranslations.loveBattlerGenderless())
        
        val oppositeGender = (wildGender == Gender.MALE && battlerGender == Gender.FEMALE) ||
                             (wildGender == Gender.FEMALE && battlerGender == Gender.MALE)
        if (!oppositeGender) return BallResult(1F, false, BallTranslations.loveNeedOpposite())
        
        val sameSpecies = ctx.speciesId == activeBattler.speciesId
        val genderDesc = if (wildGender == Gender.MALE) "♂" else "♀"
        return if (sameSpecies) BallResult(8F, true, BallTranslations.loveSameSpecies(genderDesc))
        else BallResult(2.5F, true, BallTranslations.loveOppositeGender(genderDesc))
    }
    
    private fun calculateLevelBall(ctx: BallContext): BallResult {
        // Use active battler's level (matches Cobblemon's BattleModifier which uses actor.activePokemon)
        val battlerLevel = ctx.activeBattler?.level
        if (battlerLevel != null && battlerLevel > 0) {
            val mult = when {
                battlerLevel > ctx.level * 4 -> 4F
                battlerLevel > ctx.level * 2 -> 3F
                battlerLevel > ctx.level -> 2F
                else -> 1F
            }
            val effective = mult > 1.01f
            return BallResult(mult, effective, if (effective) BallTranslations.levelEffective() else BallTranslations.levelIneffective())
        }
        // Out of battle or no battler data available
        return BallResult(1F, false, BallTranslations.levelIneffective())
    }
    
    private fun calculateRepeatBall(ctx: BallContext): BallResult {
        // Client-side: check Pokédex synced data
        ctx.hasCaughtSpecies?.let { caught ->
            val mult = if (caught) 3.5F else 1F
            return BallResult(mult, caught, if (caught) BallTranslations.repeatEffective() else BallTranslations.repeatIneffective())
        }
        return BallResult(1F, false, BallTranslations.repeatIneffective())
    }
    
    private fun calculateLureBall(ctx: BallContext): BallResult {
        CatchRateMod.debugThrottled("LureBall", "${ctx.speciesId} | aspects(${ctx.pokemonAspects.size}): ${ctx.pokemonAspects} | caught=${ctx.hasCaughtSpecies} | inBattle=${ctx.inBattle}")

        // Check "fished" aspect on the Pokemon entity
        if (ctx.pokemonAspects.isNotEmpty() || ctx.hasCaughtSpecies != null) {
            val fished = ctx.pokemonAspects.any { it.equals("fished", ignoreCase = true) }
            CatchRateMod.debugThrottled("LureBall", "  -> fished=$fished")
            val mult = if (fished) 4F else 1F
            return BallResult(mult, fished, if (fished) BallTranslations.lureEffective() else BallTranslations.lureIneffective())
        }

        CatchRateMod.debug("LureBall", "  No client data available, defaulting to 1x")
        return BallResult(1F, false, BallTranslations.lureIneffective())
    }
}
