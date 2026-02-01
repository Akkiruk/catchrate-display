package com.catchrate.client

import com.catchrate.CatchRateConstants.Colors
import com.catchrate.CatchRateConstants.Dimensions
import com.catchrate.CatchRateFormula
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Formatting
import kotlin.math.roundToInt

/**
 * Centralized HUD drawing utilities.
 * Extracted from CatchRateHudRenderer to eliminate duplication and provide
 * consistent styling across all HUD modes.
 */
object HudDrawing {
    
    // ==================== PANEL DRAWING ====================
    
    /**
     * Draw a styled panel with Cobblemon-style borders.
     * 
     * @param drawContext The draw context
     * @param x Panel X position
     * @param y Panel Y position
     * @param width Panel width
     * @param height Panel height
     * @param catchChance The catch percentage (affects border color)
     * @param isWild Whether this is for a wild (out-of-combat) Pokemon
     */
    fun drawStyledPanel(
        drawContext: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        catchChance: Double,
        isWild: Boolean = false
    ) {
        // Background with gradient effect
        drawContext.fill(x + 1, y + 1, x + width - 1, y + height - 1, Colors.PANEL_BACKGROUND)
        
        // Inner header gradient for depth
        drawContext.fill(x + 2, y + 2, x + width - 2, y + 14, Colors.PANEL_HEADER_OVERLAY)
        
        // Outer border - color based on catch chance
        val borderColor = getChanceColorInt(catchChance) or 0xFF000000.toInt()
        drawContext.drawHorizontalLine(x + 2, x + width - 3, y, borderColor)
        drawContext.drawHorizontalLine(x + 2, x + width - 3, y + height - 1, borderColor)
        drawContext.drawVerticalLine(x, y + 2, y + height - 3, borderColor)
        drawContext.drawVerticalLine(x + width - 1, y + 2, y + height - 3, borderColor)
        
        // Corner pixels for rounded effect
        drawContext.fill(x + 1, y + 1, x + 2, y + 2, borderColor)
        drawContext.fill(x + width - 2, y + 1, x + width - 1, y + 2, borderColor)
        drawContext.fill(x + 1, y + height - 2, x + 2, y + height - 1, borderColor)
        drawContext.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, borderColor)
        
        // Inner highlight line at top
        drawContext.drawHorizontalLine(x + 3, x + width - 4, y + 1, Colors.PANEL_INNER_HIGHLIGHT)
        
        // Wild Pokemon gets red accent at bottom
        if (isWild) {
            drawContext.drawHorizontalLine(x + 3, x + width - 4, y + height - 2, Colors.PANEL_WILD_ACCENT)
        }
    }
    
    // ==================== BAR DRAWING ====================
    
    /**
     * Draw a catch rate progress bar with tick marks.
     * 
     * @param drawContext The draw context
     * @param x Bar X position
     * @param y Bar Y position
     * @param width Bar width
     * @param percentage The catch percentage (0-100)
     * @param isGuaranteed Whether this is a guaranteed catch (shows green)
     */
    fun drawCatchBar(
        drawContext: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        percentage: Double,
        isGuaranteed: Boolean
    ) {
        val barHeight = Dimensions.CATCH_BAR_HEIGHT
        
        // Bar background
        drawContext.fill(x, y, x + width, y + barHeight, Colors.BAR_BACKGROUND)
        
        // Border
        drawContext.drawHorizontalLine(x, x + width - 1, y, Colors.BAR_BORDER)
        drawContext.drawHorizontalLine(x, x + width - 1, y + barHeight - 1, Colors.BAR_BORDER)
        drawContext.drawVerticalLine(x, y, y + barHeight - 1, Colors.BAR_BORDER)
        drawContext.drawVerticalLine(x + width - 1, y, y + barHeight - 1, Colors.BAR_BORDER)
        
        // Fill bar
        val fillWidth = ((width - 2) * (percentage / 100.0)).roundToInt().coerceAtLeast(0)
        if (fillWidth > 0) {
            val fillColor = if (isGuaranteed) {
                Colors.GUARANTEED or 0xFF000000.toInt()
            } else {
                getChanceColorInt(percentage) or 0xFF000000.toInt()
            }
            drawContext.fill(x + 1, y + 1, x + 1 + fillWidth, y + barHeight - 1, fillColor)
            
            // Highlight on bar top
            val highlightColor = if (isGuaranteed) {
                0x5055FF55
            } else {
                getChanceColorInt(percentage) and 0x50FFFFFF
            }
            drawContext.fill(x + 1, y + 1, x + 1 + fillWidth, y + 3, highlightColor)
        }
        
        // Tick marks at 25%, 50%, 75%
        for (tick in listOf(0.25, 0.5, 0.75)) {
            val tickX = x + ((width - 2) * tick).roundToInt()
            drawContext.drawVerticalLine(tickX, y + 1, y + barHeight - 2, Colors.BAR_TICK_MARK)
        }
    }
    
    /**
     * Draw a health bar with color gradient based on HP ratio.
     * 
     * @param drawContext The draw context
     * @param x Bar X position
     * @param y Bar Y position
     * @param width Bar width
     * @param ratio HP ratio (0.0 to 1.0)
     */
    fun drawHealthBar(
        drawContext: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        ratio: Float
    ) {
        val barHeight = Dimensions.HEALTH_BAR_HEIGHT
        
        // Bar background
        drawContext.fill(x, y, x + width, y + barHeight, Colors.BAR_BACKGROUND)
        
        // Calculate health colors (Cobblemon style - green to yellow to red)
        val (red, green) = getHealthBarColors(ratio)
        val healthColor = (255 shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or 70
        
        // Fill bar
        val fillWidth = ((width - 2) * ratio).roundToInt().coerceAtLeast(0)
        if (fillWidth > 0) {
            drawContext.fill(x + 1, y + 1, x + 1 + fillWidth, y + barHeight - 1, healthColor)
        }
        
        // Border
        drawContext.drawHorizontalLine(x, x + width - 1, y, Colors.BAR_BORDER)
        drawContext.drawHorizontalLine(x, x + width - 1, y + barHeight - 1, Colors.BAR_BORDER)
    }
    
    /**
     * Calculate health bar RGB values based on HP ratio.
     * Returns (red, green) as floats from 0-1.
     */
    private fun getHealthBarColors(ratio: Float): Pair<Float, Float> {
        val redThreshold = 0.2f
        val yellowThreshold = 0.5f
        
        val r = if (ratio > redThreshold) {
            (-2 * ratio + 2).coerceIn(0f, 1f)
        } else {
            1.0f
        }
        
        val g = when {
            ratio > yellowThreshold -> 1.0f
            ratio > redThreshold -> (ratio / yellowThreshold).coerceIn(0f, 1f)
            else -> 0.0f
        }
        
        return r to g
    }
    
    // ==================== COLOR UTILITIES ====================
    
    /**
     * Get the color for a catch percentage.
     * @param percentage Catch chance from 0-100
     * @return RGB color int (without alpha)
     */
    fun getChanceColorInt(percentage: Double): Int {
        return when {
            percentage >= 75.0 -> Colors.CHANCE_HIGH
            percentage >= 50.0 -> Colors.CHANCE_MEDIUM
            percentage >= 25.0 -> Colors.CHANCE_LOW
            else -> Colors.CHANCE_VERY_LOW
        }
    }
    
    /**
     * Get the Formatting for a catch percentage (for Text objects).
     */
    fun getChanceFormatting(percentage: Double): Formatting {
        return when {
            percentage >= 75.0 -> Formatting.GREEN
            percentage >= 50.0 -> Formatting.YELLOW
            percentage >= 25.0 -> Formatting.GOLD
            else -> Formatting.RED
        }
    }
    
    /**
     * Get the color for a ball multiplier.
     * @param multiplier Ball multiplier value
     * @return RGB color int (without alpha)
     */
    fun getBallMultiplierColor(multiplier: Number): Int {
        return when {
            multiplier.toDouble() >= 3.0 -> Colors.BALL_MULT_EXCELLENT
            multiplier.toDouble() >= 2.0 -> Colors.BALL_MULT_GREAT
            multiplier.toDouble() >= 1.5 -> Colors.BALL_MULT_GOOD
            multiplier.toDouble() < 1.0 -> Colors.BALL_MULT_POOR
            else -> Colors.BALL_MULT_NORMAL
        }
    }
    
    /**
     * Get the Formatting for a ball multiplier (for Text objects).
     */
    fun getBallMultiplierFormatting(multiplier: Double): Formatting {
        return when {
            multiplier >= 3.0 -> Formatting.GREEN
            multiplier >= 1.5 -> Formatting.AQUA
            multiplier < 1.0 -> Formatting.RED
            else -> Formatting.GRAY
        }
    }
    
    // ==================== STATUS DISPLAY ====================
    
    /**
     * Get the status icon for display.
     * Delegates to CatchRateFormula for consistency.
     */
    fun getStatusIcon(status: String): String {
        return CatchRateFormula.getStatusIcon(status)
    }
    
    // ==================== COMPARISON PANEL ====================
    
    /**
     * Draw the ball comparison panel background with gold border.
     */
    fun drawComparisonPanel(
        drawContext: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        headerHeight: Int
    ) {
        // Background
        drawContext.fill(x + 1, y + 1, x + width - 1, y + height - 1, Colors.PANEL_BACKGROUND)
        drawContext.fill(x + 2, y + 2, x + width - 2, y + headerHeight, Colors.PANEL_HEADER_OVERLAY)
        
        // Gold border with rounded corners
        val borderColor = Colors.PANEL_BORDER_DEFAULT
        drawContext.drawHorizontalLine(x + 2, x + width - 3, y, borderColor)
        drawContext.drawHorizontalLine(x + 2, x + width - 3, y + height - 1, borderColor)
        drawContext.drawVerticalLine(x, y + 2, y + height - 3, borderColor)
        drawContext.drawVerticalLine(x + width - 1, y + 2, y + height - 3, borderColor)
        
        // Corner pixels
        drawContext.fill(x + 1, y + 1, x + 2, y + 2, borderColor)
        drawContext.fill(x + width - 2, y + 1, x + width - 1, y + 2, borderColor)
        drawContext.fill(x + 1, y + height - 2, x + 2, y + height - 1, borderColor)
        drawContext.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, borderColor)
        
        // Inner highlight
        drawContext.fill(x + 3, y + 1, x + width - 3, y + 2, Colors.PANEL_INNER_HIGHLIGHT)
    }
}
