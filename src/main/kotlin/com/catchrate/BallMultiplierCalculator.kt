package com.catchrate

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.pokemon.Gender
import net.minecraft.util.Identifier

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
        
        // Party info for Love Ball (null if not available)
        val partyPokemon: List<PartyMember>?,
        
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
            return BallResult(255F, true, "Guaranteed catch!", isGuaranteed = true)
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
            "ultra_ball" -> BallResult(2F, true, "2x always")
            "great_ball" -> BallResult(1.5F, true, "1.5x always")
            "poke_ball" -> BallResult(1F, true, "1x always")
            "premier_ball" -> BallResult(1F, true, "1x always")
            "safari_ball" -> calculateSafariBall(ctx)
            "sport_ball" -> BallResult(1.5F, true, "1.5x always")
            
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
                BallResult(1F, true, "Special effect on capture")
            
            else -> BallResult(1F, true, "")
        }
    }
    
    // === ANCIENT BALLS ===
    private fun calculateAncientBall(lower: String): BallResult {
        return when {
            lower.contains("jet") || lower.contains("gigaton") -> 
                BallResult(2F, true, "Ancient T3: 2x")
            lower.contains("wing") || lower.contains("leaden") -> 
                BallResult(1.5F, true, "Ancient T2: 1.5x")
            lower.contains("ultra") -> 
                BallResult(2F, true, "Ancient Ultra: 2x")
            lower.contains("great") -> 
                BallResult(1.5F, true, "Ancient Great: 1.5x")
            else -> BallResult(1F, true, "Ancient T1: 1x")
        }
    }
    
    // === BATTLE-DEPENDENT BALLS ===
    private fun calculateQuickBall(ctx: BallContext): BallResult {
        if (!ctx.inBattle) {
            return BallResult(1F, false, "Only works turn 1 in battle")
        }
        val effective = ctx.turnCount == 1
        val mult = if (effective) 5F else 1F
        return BallResult(mult, effective, if (effective) "5x on first turn!" else "Only effective turn 1")
    }
    
    private fun calculateTimerBall(ctx: BallContext): BallResult {
        if (!ctx.inBattle) {
            return BallResult(1F, false, "Only works in battle")
        }
        val mult = (ctx.turnCount * (1229F / 4096F) + 1F).coerceAtMost(4F)
        val effective = mult > 1.01f
        return BallResult(mult, effective, "Turn ${ctx.turnCount}: ${String.format("%.1f", mult)}x")
    }
    
    // === ENVIRONMENT BALLS ===
    private fun calculateDuskBall(ctx: BallContext): BallResult {
        val mult = when {
            ctx.lightLevel == 0 -> 3.5F
            ctx.lightLevel <= 7 -> 3F
            else -> 1F
        }
        val effective = ctx.lightLevel <= 7
        val reason = if (effective) "Dark area (L${ctx.lightLevel})" else "Need darkness"
        return BallResult(mult, effective, reason)
    }
    
    private fun calculateDiveBall(ctx: BallContext): BallResult {
        val mult = if (ctx.isPlayerUnderwater) 3.5F else 1F
        return BallResult(mult, ctx.isPlayerUnderwater, 
            if (ctx.isPlayerUnderwater) "Underwater!" else "Need to be underwater")
    }
    
    private fun calculateMoonBall(ctx: BallContext): BallResult {
        if (!ctx.isNight) {
            return BallResult(1F, false, "Only effective at night")
        }
        val mult = when (ctx.moonPhase) {
            0 -> 4F      // Full moon
            1, 7 -> 2.5F // Gibbous
            2, 6 -> 1.5F // Quarter
            else -> 1F   // New/crescent
        }
        val effective = mult > 1F
        return BallResult(mult, effective, "Night (phase ${ctx.moonPhase}): ${mult}x")
    }
    
    private fun calculateSafariBall(ctx: BallContext): BallResult {
        return if (ctx.inBattle) {
            BallResult(1F, true, "1x in battle")
        } else {
            BallResult(1.5F, true, "1.5x outside battle")
        }
    }
    
    // === POKEMON STAT BALLS ===
    private fun calculateNetBall(ctx: BallContext): BallResult {
        val types = listOf(ctx.primaryType, ctx.secondaryType).filterNotNull().map { it.lowercase() }
        val effective = types.any { it == "bug" || it == "water" }
        val mult = if (effective) 3F else 1F
        return BallResult(mult, effective, if (effective) "Bug/Water type!" else "Not Bug/Water")
    }
    
    private fun calculateNestBall(ctx: BallContext): BallResult {
        val effective = ctx.level < 30
        val mult = if (effective) ((41 - ctx.level) / 10F).coerceAtLeast(1F) else 1F
        return BallResult(mult, effective, 
            if (effective) "Lv${ctx.level}: ${String.format("%.1f", mult)}x" else "Only <Lv30")
    }
    
    private fun calculateFastBall(ctx: BallContext): BallResult {
        val effective = ctx.baseSpeed >= 100
        val mult = if (effective) 4F else 1F
        return BallResult(mult, effective, 
            if (effective) "Speed ${ctx.baseSpeed}!" else "Speed <100")
    }
    
    private fun calculateHeavyBall(ctx: BallContext): BallResult {
        val mult = when {
            ctx.weight >= 3000 -> 4F
            ctx.weight >= 2000 -> 2.5F
            ctx.weight >= 1000 -> 1.5F
            else -> 1F
        }
        val effective = mult > 1F
        return BallResult(mult, effective, "${ctx.weight / 10f}kg")
    }
    
    private fun calculateBeastBall(ctx: BallContext): BallResult {
        val isUB = ctx.labels.any { it.lowercase() == "ultra_beast" }
        val mult = if (isUB) 5F else 0.1F
        return BallResult(mult, isUB, if (isUB) "Ultra Beast!" else "0.1x non-UB penalty")
    }
    
    private fun calculateDreamBall(ctx: BallContext): BallResult {
        val asleep = ctx.statusPath == "sleep"
        val mult = if (asleep) 4F else 1F
        return BallResult(mult, asleep, if (asleep) "Sleeping!" else "Need sleep status")
    }
    
    // === PARTY-DEPENDENT BALLS ===
    private fun calculateLoveBall(ctx: BallContext): BallResult {
        val party = ctx.partyPokemon
        if (party == null) {
            return BallResult(1F, false, "Requires party info", requiresServer = true)
        }
        
        val wildGender = ctx.gender
        if (wildGender == null || wildGender == Gender.GENDERLESS) {
            return BallResult(1F, false, "Wild is genderless")
        }
        
        var hasOppositeGender = false
        var hasSameSpeciesOppositeGender = false
        
        for (member in party) {
            val partyGender = member.gender
            if (partyGender == null || partyGender == Gender.GENDERLESS) continue
            
            val oppositeGender = (wildGender == Gender.MALE && partyGender == Gender.FEMALE) ||
                                 (wildGender == Gender.FEMALE && partyGender == Gender.MALE)
            
            if (oppositeGender) {
                hasOppositeGender = true
                val sameSpecies = ctx.speciesId == member.speciesId
                if (sameSpecies) {
                    hasSameSpeciesOppositeGender = true
                    break // Found best case, stop searching
                }
            }
        }
        
        return when {
            hasSameSpeciesOppositeGender -> {
                val genderDesc = if (wildGender == Gender.MALE) "♂" else "♀"
                BallResult(8F, true, "Same species + opposite gender $genderDesc")
            }
            hasOppositeGender -> {
                val genderDesc = if (wildGender == Gender.MALE) "♂" else "♀"
                BallResult(2.5F, true, "Opposite gender $genderDesc")
            }
            else -> BallResult(1F, false, "No opposite gender in party")
        }
    }
    
    private fun calculateLevelBall(ctx: BallContext): BallResult {
        // Use API value if provided (server knows player's Pokemon level)
        ctx.apiMultiplier?.let { mult ->
            val effective = mult > 1.01f
            return BallResult(mult, effective, 
                if (effective) "${String.format("%.0f", mult)}x level advantage" else "Need higher level")
        }
        return BallResult(1F, false, "Requires level comparison", requiresServer = true)
    }
    
    // === SERVER-REQUIRED BALLS ===
    private fun calculateRepeatBall(ctx: BallContext): BallResult {
        ctx.apiMultiplier?.let { mult ->
            val effective = mult > 1.01f
            return BallResult(mult, effective, 
                if (effective) "3.5x - Already caught!" else "Need to catch this species first")
        }
        return BallResult(1F, false, "Requires Pokédex", requiresServer = true)
    }
    
    private fun calculateLureBall(ctx: BallContext): BallResult {
        ctx.apiMultiplier?.let { mult ->
            val effective = mult > 1.01f
            return BallResult(mult, effective, 
                if (effective) "4x fishing bonus!" else "Only for fished Pokémon")
        }
        return BallResult(1F, false, "Requires fishing context", requiresServer = true)
    }
    
    // === UTILITY ===
    fun formatBallName(name: String): String {
        return name.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
}
