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
 * Can produce a comprehensive local report and incident files.
 */
object CatchRateDebugLog {

    private const val MAX_ENTRIES = 200
    private val entries = ArrayDeque<String>(MAX_ENTRIES + 10)
    private val guaranteedFailures = mutableListOf<GuaranteedFailure>()
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    data class GuaranteedFailure(
        val timestamp: String,
        val battleId: String,
        val targetPNX: String,
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

    private fun now(): String = LocalDateTime.now().format(timestampFormat)

    fun currentTimestamp(): String = now()

    private fun appendEntry(message: String): String {
        val entry = "[${now()}] $message"
        synchronized(entries) {
            if (entries.size >= MAX_ENTRIES) entries.removeFirst()
            entries.addLast(entry)
        }
        return entry
    }

    fun log(message: String) {
        appendEntry(message)
    }

    fun logIncident(message: String) {
        appendEntry(message)
        CatchRateMod.LOGGER.info("[CatchRate] {}", message)
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

    fun recordGuaranteedFailure(failure: GuaranteedFailure): String? {
        synchronized(guaranteedFailures) {
            guaranteedFailures.add(failure)
        }
        logIncident("!!! GUARANTEED CATCH FAILED: ${failure.pokemonName} Lv${failure.pokemonLevel} with ${failure.ballName} on turn ${failure.turnCount}! battle=${failure.battleId} target=${failure.targetPNX}")

        if (!CatchRateMod.isDebugActive) {
            CatchRateMod.sessionDebugOverride = true
            logIncident("Debug logging auto-enabled after confirmed guaranteed catch failure")
        }

        val incidentPath = saveIncidentToFile(failure)
        CatchRateMod.LOGGER.error("[CatchRate] CONFIRMED guaranteed catch failure detected via client capture packets")
        CatchRateMod.LOGGER.error(
            "[CatchRate] battle={} targetPNX={} pokemon={} lv={} ball={} mult={} turn={} baseCatchRate={} modifiedCatchRate={} hp={} status={} statusMult={} conditionMet={} conditionReason=\"{}\" estimated={} incidentLog={}",
            failure.battleId,
            failure.targetPNX,
            failure.pokemonName,
            failure.pokemonLevel,
            failure.ballName,
            failure.ballMultiplier,
            failure.turnCount,
            failure.baseCatchRate,
            String.format("%.4f", failure.modifiedCatchRate),
            String.format("%.1f", failure.hpPercentage),
            failure.statusName.ifEmpty { "none" },
            failure.statusMultiplier,
            failure.ballConditionMet,
            failure.ballConditionReason,
            failure.isCatchRateEstimate,
            incidentPath ?: "(failed to write incident file)"
        )
        return incidentPath
    }

    fun onBattleObserved(battleId: String, isPvW: Boolean) {
        logIncident("Battle started: id=$battleId isPvW=$isPvW")
    }

    fun onBattleEnd() {
        logIncident("Battle ended; cleared active guaranteed capture tracking")
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
                    sb.appendLine("    Battle: ${f.battleId} | TargetPNX: ${f.targetPNX}")
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

    private fun saveIncidentToFile(failure: GuaranteedFailure): String? {
        return try {
            val gameDir = PlatformHelper.getGameDir().toFile()
            val logsDir = File(gameDir, "catchrate-logs/incidents")
            logsDir.mkdirs()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS"))
            val file = File(logsDir, "guaranteed-failure-$timestamp.txt")
            file.writeText(buildIncidentReport(failure))
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildIncidentReport(failure: GuaranteedFailure): String {
        val sb = StringBuilder()
        sb.appendLine("=".repeat(60))
        sb.appendLine("  CATCHRATE DISPLAY - GUARANTEED FAILURE INCIDENT")
        sb.appendLine("  Generated: ${now()}")
        sb.appendLine("=".repeat(60))
        sb.appendLine()
        sb.appendLine("--- FAILURE ---")
        sb.appendLine("Time: ${failure.timestamp}")
        sb.appendLine("Battle: ${failure.battleId}")
        sb.appendLine("TargetPNX: ${failure.targetPNX}")
        sb.appendLine("Pokemon: ${failure.pokemonName} Lv${failure.pokemonLevel}")
        sb.appendLine("Ball: ${failure.ballName} (${failure.ballMultiplier}x)")
        sb.appendLine("Turn: ${failure.turnCount}")
        sb.appendLine("Base Catch Rate: ${failure.baseCatchRate}${if (failure.isCatchRateEstimate) " (ESTIMATED)" else ""}")
        sb.appendLine("Modified Catch Rate: ${String.format("%.4f", failure.modifiedCatchRate)}")
        sb.appendLine("HP: ${String.format("%.1f", failure.hpPercentage)}%")
        sb.appendLine("Status: ${failure.statusName.ifEmpty { "none" }} (${failure.statusMultiplier}x)")
        sb.appendLine("Ball Condition: met=${failure.ballConditionMet} reason=\"${failure.ballConditionReason}\"")
        sb.appendLine()
        sb.appendLine("--- ENVIRONMENT ---")
        sb.appendLine(failure.environmentSnapshot)
        sb.appendLine()
        sb.appendLine("--- RECENT DEBUG LOG ---")
        synchronized(entries) {
            if (entries.isEmpty()) {
                sb.appendLine("  (no entries)")
            } else {
                entries.takeLast(80).forEach { sb.appendLine("  $it") }
            }
        }
        sb.appendLine()
        sb.appendLine("=".repeat(60))
        return sb.toString()
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
    comparisonHeld: ${CatchRateKeybinds.isComparisonHeld}
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
