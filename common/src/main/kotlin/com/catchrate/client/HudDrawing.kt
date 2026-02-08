package com.catchrate.client

import com.catchrate.CatchRateConstants.Colors
import com.catchrate.CatchRateConstants.Dimensions
import com.catchrate.CatchRateFormula
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.ChatFormatting
import kotlin.math.roundToInt

/**
 * Centralized HUD drawing utilities.
 * Extracted from CatchRateHudRenderer to eliminate duplication and provide
 * consistent styling across all HUD modes.
 */
object HudDrawing {
    
    // ==================== PANEL DRAWING ====================
    
    fun drawStyledPanel(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        catchChance: Double,
        isWild: Boolean = false
    ) {
        // Background with gradient effect
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, Colors.PANEL_BACKGROUND)
        
        // Inner header gradient for depth
        guiGraphics.fill(x + 2, y + 2, x + width - 2, y + 14, Colors.PANEL_HEADER_OVERLAY)
        
        // Outer border - color based on catch chance
        val borderColor = getChanceColorInt(catchChance) or 0xFF000000.toInt()
        guiGraphics.hLine(x + 2, x + width - 3, y, borderColor)
        guiGraphics.hLine(x + 2, x + width - 3, y + height - 1, borderColor)
        guiGraphics.vLine(x, y + 2, y + height - 3, borderColor)
        guiGraphics.vLine(x + width - 1, y + 2, y + height - 3, borderColor)
        
        // Corner pixels for rounded effect
        guiGraphics.fill(x + 1, y + 1, x + 2, y + 2, borderColor)
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, borderColor)
        guiGraphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, borderColor)
        guiGraphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, borderColor)
        
        // Inner highlight line at top
        guiGraphics.hLine(x + 3, x + width - 4, y + 1, Colors.PANEL_INNER_HIGHLIGHT)
        
        // Wild Pokemon gets red accent at bottom
        if (isWild) {
            guiGraphics.hLine(x + 3, x + width - 4, y + height - 2, Colors.PANEL_WILD_ACCENT)
        }
    }
    
    // ==================== BAR DRAWING ====================
    
    fun drawCatchBar(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        percentage: Double,
        isGuaranteed: Boolean
    ) {
        val barHeight = Dimensions.CATCH_BAR_HEIGHT
        
        // Bar background
        guiGraphics.fill(x, y, x + width, y + barHeight, Colors.BAR_BACKGROUND)
        
        // Border
        guiGraphics.hLine(x, x + width - 1, y, Colors.BAR_BORDER)
        guiGraphics.hLine(x, x + width - 1, y + barHeight - 1, Colors.BAR_BORDER)
        guiGraphics.vLine(x, y, y + barHeight - 1, Colors.BAR_BORDER)
        guiGraphics.vLine(x + width - 1, y, y + barHeight - 1, Colors.BAR_BORDER)
        
        // Fill bar
        val fillWidth = ((width - 2) * (percentage / 100.0)).roundToInt().coerceAtLeast(0)
        if (fillWidth > 0) {
            val fillColor = if (isGuaranteed) {
                Colors.GUARANTEED or 0xFF000000.toInt()
            } else {
                getChanceColorInt(percentage) or 0xFF000000.toInt()
            }
            guiGraphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + barHeight - 1, fillColor)
            
            // Highlight on bar top
            val highlightColor = if (isGuaranteed) {
                0x5055FF55
            } else {
                getChanceColorInt(percentage) and 0x50FFFFFF
            }
            guiGraphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + 3, highlightColor)
        }
        
        // Tick marks at 25%, 50%, 75%
        for (tick in listOf(0.25, 0.5, 0.75)) {
            val tickX = x + ((width - 2) * tick).roundToInt()
            guiGraphics.vLine(tickX, y + 1, y + barHeight - 2, Colors.BAR_TICK_MARK)
        }
    }
    
    fun getHpMultiplierColor(multiplier: Double): Int {
        return when {
            multiplier >= 0.9 -> Colors.CHANCE_HIGH      // very low HP
            multiplier >= 0.67 -> Colors.CHANCE_MEDIUM    // ~half HP
            multiplier >= 0.5 -> Colors.CHANCE_LOW        // moderate HP
            else -> Colors.CHANCE_VERY_LOW                // near-full HP
        }
    }
    
    // ==================== COLOR UTILITIES ====================
    
    fun getChanceColorInt(percentage: Double): Int {
        return when {
            percentage >= 75.0 -> Colors.CHANCE_HIGH
            percentage >= 50.0 -> Colors.CHANCE_MEDIUM
            percentage >= 25.0 -> Colors.CHANCE_LOW
            else -> Colors.CHANCE_VERY_LOW
        }
    }
    
    fun getChanceFormatting(percentage: Double): ChatFormatting {
        return when {
            percentage >= 75.0 -> ChatFormatting.GREEN
            percentage >= 50.0 -> ChatFormatting.YELLOW
            percentage >= 25.0 -> ChatFormatting.GOLD
            else -> ChatFormatting.RED
        }
    }
    
    fun getBallMultiplierColor(multiplier: Number): Int {
        return when {
            multiplier.toDouble() >= 3.0 -> Colors.BALL_MULT_EXCELLENT
            multiplier.toDouble() >= 2.0 -> Colors.BALL_MULT_GREAT
            multiplier.toDouble() >= 1.5 -> Colors.BALL_MULT_GOOD
            multiplier.toDouble() < 1.0 -> Colors.BALL_MULT_POOR
            else -> Colors.BALL_MULT_NORMAL
        }
    }
    
    fun getBallMultiplierFormatting(multiplier: Double): ChatFormatting {
        return when {
            multiplier >= 3.0 -> ChatFormatting.GREEN
            multiplier >= 2.0 -> ChatFormatting.AQUA
            multiplier >= 1.5 -> ChatFormatting.YELLOW
            multiplier < 1.0 -> ChatFormatting.RED
            else -> ChatFormatting.GRAY
        }
    }
    
    // ==================== STATUS DISPLAY ====================
    
    fun getStatusIcon(status: String): String {
        return CatchRateFormula.getStatusIcon(status)
    }
    
    // ==================== COMPARISON PANEL ====================
    
    fun drawComparisonPanel(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        headerHeight: Int
    ) {
        // Background
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, Colors.PANEL_BACKGROUND)
        guiGraphics.fill(x + 2, y + 2, x + width - 2, y + headerHeight, Colors.PANEL_HEADER_OVERLAY)
        
        // Gold border with rounded corners
        val borderColor = Colors.PANEL_BORDER_DEFAULT
        guiGraphics.hLine(x + 2, x + width - 3, y, borderColor)
        guiGraphics.hLine(x + 2, x + width - 3, y + height - 1, borderColor)
        guiGraphics.vLine(x, y + 2, y + height - 3, borderColor)
        guiGraphics.vLine(x + width - 1, y + 2, y + height - 3, borderColor)
        
        // Corner pixels
        guiGraphics.fill(x + 1, y + 1, x + 2, y + 2, borderColor)
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, borderColor)
        guiGraphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, borderColor)
        guiGraphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, borderColor)
        
        // Inner highlight
        guiGraphics.fill(x + 3, y + 1, x + width - 3, y + 2, Colors.PANEL_INNER_HIGHLIGHT)
    }
}
