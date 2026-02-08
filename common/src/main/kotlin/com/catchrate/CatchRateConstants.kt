package com.catchrate

/**
 * Central location for catch rate calculation constants.
 * Only contains constants that are used in multiple places.
 * Ball-specific multipliers are in BallMultiplierCalculator (single source of truth).
 */
object CatchRateConstants {
    
    // ==================== CATCH FORMULA CONSTANTS ====================
    
    /** Maximum possible catch rate value (guaranteed catch) */
    const val MAX_CATCH_RATE = 255F
    
    /** Shake probability divisor used in Gen 6+ formula */
    const val SHAKE_PROBABILITY_DIVISOR = 65536F
    
    /** Number of possible outcomes for Random.nextInt(65537) in Cobblemon's shake check */
    const val SHAKE_RANDOM_BOUND = 65537F
    
    /** Exponent used in shake probability calculation: (255/a)^0.1875 */
    const val SHAKE_EXPONENT = 0.1875F
    
    /** Number of shake checks required for successful capture */
    const val SHAKE_CHECKS = 4
    
    /** Multiplier applied when throwing Poke Ball outside of battle */
    const val OUT_OF_BATTLE_MODIFIER = 0.5F
    
    // ==================== STATUS MULTIPLIERS ====================
    
    /** Multiplier for sleep and frozen status effects */
    const val STATUS_SLEEP_FROZEN_MULT = 2.5F
    
    /** Multiplier for paralysis, burn, and poison status effects */
    const val STATUS_PARA_BURN_POISON_MULT = 1.5F
    
    /** Default multiplier when no status effect */
    const val STATUS_NONE_MULT = 1F
    
    // ==================== BALL CONSTANTS (only commonly used ones) ====================
    
    /** Guaranteed catch multiplier (Master Ball, etc.) */
    const val BALL_GUARANTEED_MULT = 255F
    
    /** Standard Poke Ball multiplier (fallback) */
    const val BALL_STANDARD_MULT = 1F
    
    // ==================== LEVEL BONUS CONSTANTS ====================
    
    /** Level threshold for low-level catch bonus */
    const val LOW_LEVEL_THRESHOLD = 13
    
    // ==================== ENVIRONMENT CONSTANTS ====================
    
    /** Time of day range for night (in ticks) */
    const val NIGHT_START_TICK = 12000L
    const val NIGHT_END_TICK = 24000L
    
    // ==================== UI COLORS ====================
    
    object Colors {
        // Panel colors
        const val PANEL_BACKGROUND = 0xE5101820.toInt()
        const val PANEL_HEADER_OVERLAY = 0x40000000
        const val PANEL_BORDER_DEFAULT = 0xFFFFAA00.toInt()
        const val PANEL_WILD_ACCENT = 0xFFE43838.toInt()
        const val PANEL_INNER_HIGHLIGHT = 0x30FFFFFF
        
        // Bar colors
        const val BAR_BACKGROUND = 0xFF1A1A2E.toInt()
        const val BAR_BORDER = 0xFF333344.toInt()
        const val BAR_TICK_MARK = 0x40FFFFFF
        
        // Text colors
        const val TEXT_WHITE = 0xFFFFFF
        const val TEXT_GRAY = 0xAAAAAA
        const val TEXT_DARK_GRAY = 0x888888
        const val TEXT_DARK_GREEN = 0x55AA55
        const val TEXT_PURPLE = 0xAA55FF
        const val TEXT_RED = 0xFF5555
        const val TEXT_YELLOW = 0xFFFF55
        const val TEXT_ORANGE = 0xFFAA00
        const val TEXT_GREEN = 0x55FF55
        const val TEXT_CYAN = 0x55FFFF
        const val TEXT_WILD_RED = 0xE43838
        
        // Chance-based colors
        const val CHANCE_HIGH = 0x55FF55     // >= 75%
        const val CHANCE_MEDIUM = 0xFFFF55   // >= 50%
        const val CHANCE_LOW = 0xFFAA00      // >= 25%
        const val CHANCE_VERY_LOW = 0xFF5555 // < 25%
        
        // Ball multiplier colors
        const val BALL_MULT_EXCELLENT = 0x55FF55  // >= 3x
        const val BALL_MULT_GREAT = 0x55FFFF      // >= 2x
        const val BALL_MULT_GOOD = 0xFFFF55       // >= 1.5x
        const val BALL_MULT_POOR = 0xFF5555       // < 1x
        const val BALL_MULT_NORMAL = 0xAAAAAA     // 1x
        
        // Guaranteed catch
        const val GUARANTEED = 0x55FF55
    }
    
    // ==================== UI DIMENSIONS ====================
    
    object Dimensions {
        const val HUD_PADDING = 6
        const val LINE_HEIGHT = 10
        const val CATCH_BAR_HEIGHT = 8
    }
}
