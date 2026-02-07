package com.catchrate

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.pokemon.Gender
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Translation helper for ball-related strings.
 */
object BallTranslations {
    fun guaranteedCatch() = Text.translatable("catchrate.ball.guaranteed_catch").string
    fun multiplierAlways(mult: String) = Text.translatable("catchrate.ball.multiplier_always", mult).string
    fun specialEffect() = Text.translatable("catchrate.ball.special_effect").string
    fun ancient() = Text.translatable("catchrate.ball.ancient").string
    
    fun quickNotInBattle() = Text.translatable("catchrate.ball.quick.not_in_battle").string
    fun quickEffective() = Text.translatable("catchrate.ball.quick.effective").string
    fun quickIneffective() = Text.translatable("catchrate.ball.quick.ineffective").string
    
    fun timerTurnInfo(turn: Int, mult: Float) = Text.translatable("catchrate.ball.timer.turn_info", turn, mult).string
    fun timerScales() = Text.translatable("catchrate.ball.timer.scales").string
    
    fun duskDarkArea(level: Int) = Text.translatable("catchrate.ball.dusk.dark_area", level).string
    fun duskNeedDarkness() = Text.translatable("catchrate.ball.dusk.need_darkness").string
    
    fun diveUnderwater() = Text.translatable("catchrate.ball.dive.underwater").string
    fun diveNeedUnderwater() = Text.translatable("catchrate.ball.dive.need_underwater").string
    
    fun moonNotNight() = Text.translatable("catchrate.ball.moon.not_night").string
    fun moonNightPhase(phase: Int, mult: Float) = Text.translatable("catchrate.ball.moon.night_phase", phase, mult).string
    
    fun netEffective() = Text.translatable("catchrate.ball.net.effective").string
    fun netIneffective() = Text.translatable("catchrate.ball.net.ineffective").string
    
    fun nestEffective(level: Int, mult: Float) = Text.translatable("catchrate.ball.nest.effective", level, mult).string
    fun nestIneffective() = Text.translatable("catchrate.ball.nest.ineffective").string
    
    fun fastEffective(speed: Int) = Text.translatable("catchrate.ball.fast.effective", speed).string
    fun fastIneffective() = Text.translatable("catchrate.ball.fast.ineffective").string
    
    fun heavyWeight(kg: Float) = Text.translatable("catchrate.ball.heavy.weight", kg).string
    
    fun beastUltraBeast() = Text.translatable("catchrate.ball.beast.ultra_beast").string
    fun beastPenalty() = Text.translatable("catchrate.ball.beast.penalty").string
    
    fun dreamSleeping() = Text.translatable("catchrate.ball.dream.sleeping").string
    fun dreamNeedSleep() = Text.translatable("catchrate.ball.dream.need_sleep").string
    
    fun loveNoBattler() = Text.translatable("catchrate.ball.love.no_battler").string
    fun loveNeedBattler() = Text.translatable("catchrate.ball.love.need_battler").string
    fun loveWildGenderless() = Text.translatable("catchrate.ball.love.wild_genderless").string
    fun loveBattlerGenderless() = Text.translatable("catchrate.ball.love.battler_genderless").string
    fun loveNeedOpposite() = Text.translatable("catchrate.ball.love.need_opposite").string
    fun loveSameSpecies(gender: String) = Text.translatable("catchrate.ball.love.same_species", gender).string
    fun loveOppositeGender(gender: String) = Text.translatable("catchrate.ball.love.opposite_gender", gender).string
    
    fun levelEffective(mult: Float) = Text.translatable("catchrate.ball.level.effective", mult).string
    fun levelIneffective() = Text.translatable("catchrate.ball.level.ineffective").string
    fun levelRequiresServer() = Text.translatable("catchrate.ball.level.requires_server").string
    
    fun repeatEffective() = Text.translatable("catchrate.ball.repeat.effective").string
    fun repeatIneffective() = Text.translatable("catchrate.ball.repeat.ineffective").string
    fun repeatRequiresServer() = Text.translatable("catchrate.ball.repeat.requires_server").string
    
    fun lureEffective() = Text.translatable("catchrate.ball.lure.effective").string
    fun lureIneffective() = Text.translatable("catchrate.ball.lure.ineffective").string
    fun lureRequiresServer() = Text.translatable("catchrate.ball.lure.requires_server").string
}

/**
 * Unified ball multiplier calculation system.
 * Single source of truth for all ball bonus logic.
 */
object BallMultiplierCalculator {

    /**
     * Context for ball calculation - abstracts away the different Pokemon types.
     */
    data class BallContext(
        // Pokemon info
        val speciesId: String,
        val level: Int,
        val gender: Gender?,
        val primaryType: String,
        val secondaryType: String?,
        val weight: Float,         // In hectograms (Cobblemon format)
        val baseSpeed: Int,
        val labels: List<String>,
        val statusPath: String?,   // e.g. "sleep", "paralysis"
        
        // Environment info
        val lightLevel: Int,
        val isNight: Boolean,
        val moonPhase: Int,
        val isPlayerUnderwater: Boolean,
        
        // Battle context
        val inBattle: Boolean,
        val turnCount: Int,
        
        // Active battler for Love Ball - the Pokemon currently in play (not the whole party)
        val activeBattler: PartyMember?,
        
        // Server-provided API values (for balls we can't calculate client-side)
        val apiMultiplier: Float? = null,
        val apiIsValid: Boolean? = null
    )

    data class PartyMember(
        val speciesId: String,
        val gender: Gender?
    )

    data class BallResult(
        val multiplier: Float,
        val conditionMet: Boolean,
        val reason: String,
        val isGuaranteed: Boolean = false,
        val requiresServer: Boolean = false
    )

    /**
     * Calculate ball multiplier and condition info.
     * This is the single unified calculation method.
     */
    fun calculate(ballId: String, ctx: BallContext): BallResult {
        val lower = ballId.lowercase()
        
        // Check for guaranteed catch balls first
        val pokeBall = try {
            PokeBalls.getPokeBall(Identifier.of("cobblemon", ballId))
        } catch (e: Exception) { null }
        
        if (pokeBall?.catchRateModifier?.isGuaranteed() == true || lower.contains("master")) {
            return BallResult(255F, true, BallTranslations.guaranteedCatch(), isGuaranteed = true)
        }
        
        // Ancient balls - wiki-documented multipliers
        if (pokeBall?.ancient == true) {
            return calculateAncientBall(lower)
        }
        
        return when (lower) {
            // === BATTLE-DEPENDENT BALLS ===
            "quick_ball" -> calculateQuickBall(ctx)
            "timer_ball" -> calculateTimerBall(ctx)
            
            // === CONSTANT MULTIPLIER BALLS ===
            "ultra_ball" -> BallResult(2F, true, BallTranslations.multiplierAlways("2"))
            "great_ball" -> BallResult(1.5F, true, BallTranslations.multiplierAlways("1.5"))
            "poke_ball" -> BallResult(1F, true, BallTranslations.multiplierAlways("1"))
            "premier_ball" -> BallResult(1F, true, BallTranslations.multiplierAlways("1"))
            "safari_ball" -> BallResult(1.5F, true, BallTranslations.multiplierAlways("1.5"))
            "sport_ball" -> BallResult(1.5F, true, BallTranslations.multiplierAlways("1.5"))
            
            // === ENVIRONMENT-BASED BALLS ===
            "dusk_ball" -> calculateDuskBall(ctx)
            "dive_ball" -> calculateDiveBall(ctx)
            "moon_ball" -> calculateMoonBall(ctx)
            
            // === POKEMON STAT-BASED BALLS ===
            "net_ball" -> calculateNetBall(ctx)
            "nest_ball" -> calculateNestBall(ctx)
            "fast_ball" -> calculateFastBall(ctx)
            "heavy_ball" -> calculateHeavyBall(ctx)
            "beast_ball" -> calculateBeastBall(ctx)
            "dream_ball" -> calculateDreamBall(ctx)
            
            // === PARTY/CONTEXT-DEPENDENT BALLS ===
            "love_ball" -> calculateLoveBall(ctx)
            "level_ball" -> calculateLevelBall(ctx)
            
            // === SERVER-REQUIRED BALLS (need Pokedex/fishing context) ===
            "repeat_ball" -> calculateRepeatBall(ctx)
            "lure_ball" -> calculateLureBall(ctx)
            
            // === NO BONUS BALLS ===
            "friend_ball", "luxury_ball", "heal_ball" -> 
                BallResult(1F, true, BallTranslations.specialEffect())
            
            else -> BallResult(1F, true, "")
        }
    }
    
    // === ANCIENT BALLS ===
    // Note: In Cobblemon, ancient balls use DUMMY catchRateModifier (always 1x)
    // Their throwPower only affects ball travel distance, not catch rate
    private fun calculateAncientBall(lower: String): BallResult {
        return BallResult(1F, true, BallTranslations.ancient())
    }
    
    // === BATTLE-DEPENDENT BALLS ===
    private fun calculateQuickBall(ctx: BallContext): BallResult {
        if (!ctx.inBattle) {
            return BallResult(1F, false, BallTranslations.quickNotInBattle())
        }
        val effective = ctx.turnCount == 1
        val mult = if (effective) 5F else 1F
        return BallResult(mult, effective, if (effective) BallTranslations.quickEffective() else BallTranslations.quickIneffective())
    }
    
    private fun calculateTimerBall(ctx: BallContext): BallResult {
        // Timer Ball scales with turns - out of combat defaults to turn 0 = 1x
        val mult = if (ctx.inBattle) (ctx.turnCount * (1229F / 4096F) + 1F).coerceAtMost(4F) else 1F
        val effective = mult > 1.01f
        val reason = if (ctx.inBattle) BallTranslations.timerTurnInfo(ctx.turnCount, mult) else BallTranslations.timerScales()
        return BallResult(mult, effective, reason)
    }
    
    // === ENVIRONMENT BALLS ===
    private fun calculateDuskBall(ctx: BallContext): BallResult {
        val mult = when {
            ctx.lightLevel == 0 -> 3.5F
            ctx.lightLevel <= 7 -> 3F
            else -> 1F
        }
        val effective = ctx.lightLevel <= 7
        val reason = if (effective) BallTranslations.duskDarkArea(ctx.lightLevel) else BallTranslations.duskNeedDarkness()
        return BallResult(mult, effective, reason)
    }
    
    private fun calculateDiveBall(ctx: BallContext): BallResult {
        val mult = if (ctx.isPlayerUnderwater) 3.5F else 1F
        return BallResult(mult, ctx.isPlayerUnderwater, 
            if (ctx.isPlayerUnderwater) BallTranslations.diveUnderwater() else BallTranslations.diveNeedUnderwater())
    }
    
    private fun calculateMoonBall(ctx: BallContext): BallResult {
        if (!ctx.isNight) {
            return BallResult(1F, false, BallTranslations.moonNotNight())
        }
        val mult = when (ctx.moonPhase) {
            0 -> 4F      // Full moon
            1, 7 -> 2.5F // Gibbous
            2, 6 -> 1.5F // Quarter
            else -> 1F   // New/crescent
        }
        val effective = mult > 1F
        return BallResult(mult, effective, BallTranslations.moonNightPhase(ctx.moonPhase, mult))
    }
    
    // === POKEMON STAT BALLS ===
    private fun calculateNetBall(ctx: BallContext): BallResult {
        val types = listOf(ctx.primaryType, ctx.secondaryType).filterNotNull().map { it.lowercase() }
        val effective = types.any { it == "bug" || it == "water" }
        val mult = if (effective) 3F else 1F
        return BallResult(mult, effective, if (effective) BallTranslations.netEffective() else BallTranslations.netIneffective())
    }
    
    private fun calculateNestBall(ctx: BallContext): BallResult {
        val effective = ctx.level < 30
        val mult = if (effective) ((41 - ctx.level) / 10F).coerceAtLeast(1F) else 1F
        return BallResult(mult, effective, 
            if (effective) BallTranslations.nestEffective(ctx.level, mult) else BallTranslations.nestIneffective())
    }
    
    private fun calculateFastBall(ctx: BallContext): BallResult {
        val effective = ctx.baseSpeed >= 100
        val mult = if (effective) 4F else 1F
        return BallResult(mult, effective, 
            if (effective) BallTranslations.fastEffective(ctx.baseSpeed) else BallTranslations.fastIneffective())
    }
    
    private fun calculateHeavyBall(ctx: BallContext): BallResult {
        val mult = when {
            ctx.weight >= 3000 -> 4F
            ctx.weight >= 2000 -> 2.5F
            ctx.weight >= 1000 -> 1.5F
            else -> 1F
        }
        val effective = mult > 1F
        return BallResult(mult, effective, BallTranslations.heavyWeight(ctx.weight / 10f))
    }
    
    private fun calculateBeastBall(ctx: BallContext): BallResult {
        val isUB = ctx.labels.any { it.lowercase() == "ultra_beast" }
        val mult = if (isUB) 5F else 0.1F
        return BallResult(mult, isUB, if (isUB) BallTranslations.beastUltraBeast() else BallTranslations.beastPenalty())
    }
    
    private fun calculateDreamBall(ctx: BallContext): BallResult {
        val asleep = ctx.statusPath == "sleep"
        val mult = if (asleep) 4F else 1F
        return BallResult(mult, asleep, if (asleep) BallTranslations.dreamSleeping() else BallTranslations.dreamNeedSleep())
    }
    
    // === PARTY-DEPENDENT BALLS ===
    private fun calculateLoveBall(ctx: BallContext): BallResult {
        // Love Ball checks the ACTIVE battler - out of combat = no battler = 1x
        val activeBattler = ctx.activeBattler
        if (activeBattler == null) {
            return BallResult(1F, false, if (ctx.inBattle) BallTranslations.loveNoBattler() else BallTranslations.loveNeedBattler())
        }
        
        val wildGender = ctx.gender
        if (wildGender == null || wildGender == Gender.GENDERLESS) {
            return BallResult(1F, false, BallTranslations.loveWildGenderless())
        }
        
        val battlerGender = activeBattler.gender
        if (battlerGender == null || battlerGender == Gender.GENDERLESS) {
            return BallResult(1F, false, BallTranslations.loveBattlerGenderless())
        }
        
        val oppositeGender = (wildGender == Gender.MALE && battlerGender == Gender.FEMALE) ||
                             (wildGender == Gender.FEMALE && battlerGender == Gender.MALE)
        
        if (!oppositeGender) {
            return BallResult(1F, false, BallTranslations.loveNeedOpposite())
        }
        
        // Check if same species AND opposite gender
        val sameSpecies = ctx.speciesId == activeBattler.speciesId
        val genderDesc = if (wildGender == Gender.MALE) "♂" else "♀"
        
        return if (sameSpecies) {
            BallResult(8F, true, BallTranslations.loveSameSpecies(genderDesc))
        } else {
            BallResult(2.5F, true, BallTranslations.loveOppositeGender(genderDesc))
        }
    }
    
    private fun calculateLevelBall(ctx: BallContext): BallResult {
        // Use API value if provided (server knows player's Pokemon level)
        ctx.apiMultiplier?.let { mult ->
            val effective = mult > 1.01f
            return BallResult(mult, effective, 
                if (effective) BallTranslations.levelEffective(mult) else BallTranslations.levelIneffective())
        }
        return BallResult(1F, false, BallTranslations.levelRequiresServer(), requiresServer = true)
    }
    
    // === SERVER-REQUIRED BALLS ===
    private fun calculateRepeatBall(ctx: BallContext): BallResult {
        ctx.apiMultiplier?.let { mult ->
            val effective = mult > 1.01f
            return BallResult(mult, effective, 
                if (effective) BallTranslations.repeatEffective() else BallTranslations.repeatIneffective())
        }
        return BallResult(1F, false, BallTranslations.repeatRequiresServer(), requiresServer = true)
    }
    
    private fun calculateLureBall(ctx: BallContext): BallResult {
        ctx.apiMultiplier?.let { mult ->
            val effective = mult > 1.01f
            return BallResult(mult, effective, 
                if (effective) BallTranslations.lureEffective() else BallTranslations.lureIneffective())
        }
        return BallResult(1F, false, BallTranslations.lureRequiresServer(), requiresServer = true)
    }
    
}
