package com.catchrate

import com.catchrate.config.CatchRateConfig
import com.catchrate.platform.PlatformHelper
import net.minecraft.client.Minecraft
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * In-memory debug log collector for catch rate calculations.
 * Captures detailed calculation snapshots, environment info, and guaranteed catch failures.
 * Can produce a comprehensive report and upload it to mclo.gs.
 */
object CatchRateDebugLog {

    private const val MAX_ENTRIES = 200
    private val entries = ArrayDeque<String>(MAX_ENTRIES + 10)
    private val guaranteedFailures = mutableListOf<GuaranteedFailure>()
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    data class GuaranteedFailure(
        val timestamp: String,
        val pokemonName: String,
        val pokemonLevel: Int,
        val ballName: String,
        val ballMultiplier: Double,
        val baseCatchRate: Int,
        val modifiedCatchRate: Double,
        val hpPercentage: Double,
        val statusName: String,
        val statusMultiplier: Double,
        val turnCount: Int,
        val ballConditionMet: Boolean,
        val ballConditionReason: String,
        val isCatchRateEstimate: Boolean,
        val environmentSnapshot: String
    )

    // Track last guaranteed prediction for failure detection
    data class PendingGuaranteed(
        val result: CatchRateResult,
        val pokemonName: String,
        val pokemonLevel: Int,
        val turnCount: Int,
        val timestamp: String,
        val environmentSnapshot: String,
        val heldBallId: String,
        val heldBallCount: Int?,
        val throwAttemptArmed: Boolean = false
    )

    var pendingGuaranteed: PendingGuaranteed? = null
        private set

    private fun now(): String = LocalDateTime.now().format(timestampFormat)

    fun log(message: String) {
        val entry = "[${now()}] $message"
        synchronized(entries) {
            if (entries.size >= MAX_ENTRIES) entries.removeFirst()
            entries.addLast(entry)
        }
    }

    /** Log a full catch rate calculation snapshot. */
    fun logCalculation(
        pokemonName: String,
        pokemonLevel: Int,
        result: CatchRateResult,
        inBattle: Boolean,
        context: String = ""
    ) {
        val sb = StringBuilder()
        sb.appendLine("=== Catch Rate Calculation ===")
        sb.appendLine("  Pokemon: $pokemonName Lv$pokemonLevel")
        sb.appendLine("  Ball: ${result.ballName} (${result.ballMultiplier}x) condition_met=${result.ballConditionMet} reason=\"${result.ballConditionReason}\"")
        sb.appendLine("  Base Catch Rate: ${result.baseCatchRate}${if (result.isCatchRateEstimate) " (ESTIMATED - species not found in local data)" else ""}")
        sb.appendLine("  HP: ${String.format("%.1f", result.hpPercentage)}%")
        sb.appendLine("  Status: ${result.statusName.ifEmpty { "none" }} (${result.statusMultiplier}x)")
        sb.appendLine("  Level Bonus: ${result.levelBonus}x")
        sb.appendLine("  Modified Catch Rate: ${String.format("%.4f", result.modifiedCatchRate)}")
        sb.appendLine("  In Battle: $inBattle${if (!inBattle) " (0.5x penalty)" else ""}")
        sb.appendLine("  Guaranteed: ${result.isGuaranteed}")
        sb.appendLine("  Final Percentage: ${String.format("%.2f", result.percentage)}%")
        sb.appendLine("  Turn: ${result.turnCount}")
        if (context.isNotEmpty()) sb.appendLine("  Context: $context")
        log(sb.toString().trimEnd())
    }

    /** Record that a guaranteed catch was predicted - will check for failure on next turn. */
    fun recordGuaranteedPrediction(
        result: CatchRateResult,
        pokemonName: String,
        pokemonLevel: Int,
        turnCount: Int,
        heldBallId: String,
        heldBallCount: Int?
    ) {
        val existing = pendingGuaranteed
        if (existing != null &&
            existing.turnCount == turnCount &&
            existing.pokemonName == pokemonName &&
            existing.result.ballName == result.ballName &&
            existing.heldBallId == heldBallId &&
            existing.heldBallCount == heldBallCount &&
            kotlin.math.abs(existing.result.modifiedCatchRate - result.modifiedCatchRate) < 0.0001 &&
            kotlin.math.abs(existing.result.hpPercentage - result.hpPercentage) < 0.01
        ) {
            return
        }

        pendingGuaranteed = PendingGuaranteed(
            result = result,
            pokemonName = pokemonName,
            pokemonLevel = pokemonLevel,
            turnCount = turnCount,
            timestamp = now(),
            environmentSnapshot = buildEnvironmentSnapshot(),
            heldBallId = heldBallId,
            heldBallCount = heldBallCount
        )
        log("!!! GUARANTEED CATCH PREDICTED: $pokemonName Lv$pokemonLevel with ${result.ballName} (${result.ballMultiplier}x, mod=${String.format("%.4f", result.modifiedCatchRate)})")
    }

    /** Arm the failure check only after the player has actually submitted an action while the same guaranteed ball is still selected. */
    fun armPendingGuaranteedThrow(turnCount: Int, heldBallId: String?, heldBallCount: Int?) {
        val pending = pendingGuaranteed ?: return
        if (pending.turnCount != turnCount) return
        if (heldBallId != pending.heldBallId) return
        if (pending.heldBallCount != null && heldBallCount != pending.heldBallCount) return
        if (pending.throwAttemptArmed) return

        pendingGuaranteed = pending.copy(throwAttemptArmed = true)
        log("Armed guaranteed catch failure check for ${pending.pokemonName} Lv${pending.pokemonLevel} with ${pending.result.ballName}")
    }

    /** Called when a new turn starts - if we had a confirmed throw attempt and the ball stack changed, the catch failed. */
    fun onNewTurn(newTurnCount: Int, heldBallId: String?, heldBallCount: Int?): GuaranteedFailure? {
        val pending = pendingGuaranteed ?: return null
        if (newTurnCount <= pending.turnCount) return null
        if (!pending.throwAttemptArmed) return null

        val ballWasConsumed = when {
            pending.heldBallCount == null -> false
            heldBallId == pending.heldBallId && heldBallCount != null -> heldBallCount < pending.heldBallCount
            (heldBallId == null || heldBallId.isBlank()) && pending.heldBallCount <= 1 -> true
            else -> false
        }

        pendingGuaranteed = null
        if (!ballWasConsumed) {
            log("Cleared guaranteed catch prediction without confirmed ball consumption")
            return null
        }

        val failure = GuaranteedFailure(
            timestamp = pending.timestamp,
            pokemonName = pending.pokemonName,
            pokemonLevel = pending.pokemonLevel,
            ballName = pending.result.ballName,
            ballMultiplier = pending.result.ballMultiplier,
            baseCatchRate = pending.result.baseCatchRate,
            modifiedCatchRate = pending.result.modifiedCatchRate,
            hpPercentage = pending.result.hpPercentage,
            statusName = pending.result.statusName,
            statusMultiplier = pending.result.statusMultiplier,
            turnCount = pending.turnCount,
            ballConditionMet = pending.result.ballConditionMet,
            ballConditionReason = pending.result.ballConditionReason,
            isCatchRateEstimate = pending.result.isCatchRateEstimate,
            environmentSnapshot = pending.environmentSnapshot
        )
        synchronized(guaranteedFailures) {
            guaranteedFailures.add(failure)
        }
        log("!!! GUARANTEED CATCH FAILED: ${failure.pokemonName} Lv${failure.pokemonLevel} with ${failure.ballName} on turn ${failure.turnCount}!")
        return failure
    }

    /** Called when the battle ends - clear pending guaranteed if the catch succeeded (battle ended). */
    fun onBattleEnd() {
        pendingGuaranteed = null
    }

    /** Build a comprehensive debug report for upload. */
    fun buildFullReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=" .repeat(60))
        sb.appendLine("  CATCHRATE DISPLAY - DEBUG REPORT")
        sb.appendLine("  Generated: ${now()}")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        // Environment info
        sb.appendLine("--- ENVIRONMENT ---")
        sb.appendLine(buildEnvironmentSnapshot())
        sb.appendLine()

        // Mod list
        sb.appendLine("--- INSTALLED MODS ---")
        sb.appendLine(getInstalledMods())
        sb.appendLine()

        // Config
        sb.appendLine("--- CONFIG ---")
        sb.appendLine(getConfigDump())
        sb.appendLine()

        // Guaranteed catch failures
        sb.appendLine("--- GUARANTEED CATCH FAILURES (${guaranteedFailures.size}) ---")
        if (guaranteedFailures.isEmpty()) {
            sb.appendLine("  (none recorded)")
        } else {
            synchronized(guaranteedFailures) {
                guaranteedFailures.forEachIndexed { i, f ->
                    sb.appendLine("  Failure #${i + 1}:")
                    sb.appendLine("    Time: ${f.timestamp}")
                    sb.appendLine("    Pokemon: ${f.pokemonName} Lv${f.pokemonLevel}")
                    sb.appendLine("    Ball: ${f.ballName} (${f.ballMultiplier}x) condition_met=${f.ballConditionMet} reason=\"${f.ballConditionReason}\"")
                    sb.appendLine("    Base Catch Rate: ${f.baseCatchRate}${if (f.isCatchRateEstimate) " (ESTIMATED)" else ""}")
                    sb.appendLine("    Modified Catch Rate: ${String.format("%.4f", f.modifiedCatchRate)}")
                    sb.appendLine("    HP: ${String.format("%.1f", f.hpPercentage)}% | Status: ${f.statusName.ifEmpty { "none" }} (${f.statusMultiplier}x)")
                    sb.appendLine("    Turn: ${f.turnCount}")
                    sb.appendLine("    --- Environment at time of failure ---")
                    f.environmentSnapshot.lines().forEach { line ->
                        sb.appendLine("    $line")
                    }
                    sb.appendLine()
                }
            }
        }

        // Recent debug log entries
        sb.appendLine("--- DEBUG LOG (last $MAX_ENTRIES entries) ---")
        synchronized(entries) {
            if (entries.isEmpty()) {
                sb.appendLine("  (no entries - enable debug with /catchrate debug)")
            } else {
                entries.forEach { sb.appendLine("  $it") }
            }
        }
        sb.appendLine()

        // Catch rate cache info
        sb.appendLine("--- CATCH RATE CACHE ---")
        sb.appendLine(getCacheInfo())
        sb.appendLine()

        sb.appendLine("=".repeat(60))
        sb.appendLine("  END OF REPORT")
        sb.appendLine("=".repeat(60))

        return sb.toString()
    }

    fun buildEnvironmentSnapshot(): String {
        val sb = StringBuilder()
        sb.appendLine("Mod Version: ${CatchRateMod.VERSION}")
        sb.appendLine("Minecraft: ${getMinecraftVersion()}")
        sb.appendLine("Loader: ${getLoaderInfo()}")
        sb.appendLine("Loader Version: ${getLoaderVersion()}")
        sb.appendLine("Cobblemon: ${getCobblemonVersion()}")
        sb.appendLine("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        sb.appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        sb.appendLine("Debug Enabled: ${CatchRateMod.isDebugActive} (config=${CatchRateMod.DEBUG_ENABLED}, session=${CatchRateMod.sessionDebugOverride})")

        val minecraft = Minecraft.getInstance()
        val connection = minecraft.connection
        if (connection != null) {
            val serverData = minecraft.currentServer
            if (serverData != null) {
                sb.appendLine("Server: Multiplayer - ${serverData.ip}")
                sb.appendLine("Server Name: ${serverData.name}")
            } else if (minecraft.isLocalServer) {
                sb.appendLine("Server: Singleplayer (integrated)")
            } else {
                sb.appendLine("Server: Unknown (connected)")
            }
        } else {
            sb.appendLine("Server: Not connected")
        }

        // Battle state
        val battle = try { com.cobblemon.mod.common.client.CobblemonClient.battle } catch (_: Throwable) { null }
        if (battle != null) {
            sb.appendLine("Battle Active: yes (id=${battle.battleId}, isPvW=${battle.isPvW}, mustChoose=${battle.mustChoose})")
            val opponent = battle.side2?.activeClientBattlePokemon?.firstOrNull()?.battlePokemon
            if (opponent != null) {
                sb.appendLine("  Opponent: ${opponent.species.name} Lv${opponent.level}")
            }
        } else {
            sb.appendLine("Battle Active: no")
        }

        return sb.toString().trimEnd()
    }

    /** Save the report to a local file. Returns the file path. */
    fun saveToFile(): String {
        val report = buildFullReport()
        val gameDir = PlatformHelper.getGameDir().toFile()
        val logsDir = File(gameDir, "catchrate-logs")
        logsDir.mkdirs()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val file = File(logsDir, "catchrate-debug-$timestamp.txt")
        file.writeText(report)
        return file.absolutePath
    }

    // ==================== Data Collectors ====================

    private fun getMinecraftVersion(): String {
        return try { net.minecraft.SharedConstants.getCurrentVersion().name } catch (_: Throwable) { "unknown" }
    }

    private fun getLoaderInfo(): String {
        return try {
            if (Class.forName("net.fabricmc.loader.api.FabricLoader") != null) "Fabric" else "Unknown"
        } catch (_: Throwable) {
            try {
                if (Class.forName("net.neoforged.fml.loading.FMLLoader") != null) "NeoForge" else "Unknown"
            } catch (_: Throwable) { "Unknown" }
        }
    }

    private fun getLoaderVersion(): String {
        return try {
            val loader = net.fabricmc.loader.api.FabricLoader.getInstance()
            loader.getModContainer("fabricloader")
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")
        } catch (_: Throwable) {
            try {
                // NeoForge
                val clazz = Class.forName("net.neoforged.fml.loading.FMLLoader")
                val versionField = clazz.getDeclaredMethod("versionInfo")
                val versionInfo = versionField.invoke(null)
                versionInfo?.toString() ?: "unknown"
            } catch (_: Throwable) { "unknown" }
        }
    }

    private fun getCobblemonVersion(): String {
        return try { com.cobblemon.mod.common.Cobblemon.VERSION } catch (_: Throwable) { "unknown" }
    }

    private fun getInstalledMods(): String {
        return try {
            val loader = net.fabricmc.loader.api.FabricLoader.getInstance()
            loader.allMods.sortedBy { it.metadata.id }.joinToString("\n") { mod ->
                "  ${mod.metadata.id} v${mod.metadata.version.friendlyString} - ${mod.metadata.name}"
            }
        } catch (_: Throwable) {
            try {
                // NeoForge
                val modList = Class.forName("net.neoforged.fml.ModList")
                    .getDeclaredMethod("get")
                    .invoke(null)
                val mods = modList.javaClass.getDeclaredMethod("getMods").invoke(modList) as List<*>
                mods.joinToString("\n") { modInfo ->
                    val id = modInfo?.javaClass?.getDeclaredMethod("getModId")?.invoke(modInfo) ?: "?"
                    val ver = modInfo?.javaClass?.getDeclaredMethod("getVersion")?.invoke(modInfo) ?: "?"
                    val name = modInfo?.javaClass?.getDeclaredMethod("getDisplayName")?.invoke(modInfo) ?: "?"
                    "  $id v$ver - $name"
                }
            } catch (_: Throwable) { "  (unable to enumerate mods)" }
        }
    }

    private fun getConfigDump(): String {
        val config = CatchRateConfig.get()
        return """  hudEnabled: ${config.hudEnabled}
  hudAnchor: ${config.hudAnchor}
  hudOffsetX: ${config.hudOffsetX}
  hudOffsetY: ${config.hudOffsetY}
  showOutOfCombat: ${config.showOutOfCombat}
  compactMode: ${config.compactMode}
  showBallComparison: ${config.showBallComparison}
  hideUnencounteredInfo: ${config.hideUnencounteredInfo}
  debugLogging: ${config.debugLogging}"""
    }

    private fun getCacheInfo(): String {
        return try {
            val cacheSize = SpeciesCatchRateCache.cacheSize()
            "  Cache size: $cacheSize species cached"
        } catch (_: Throwable) {
            "  (unable to read cache info)"
        }
    }
}
