package com.catchrate

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureEndPacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureStartPacket
import java.util.UUID

object CatchRateCaptureTracker {

    private data class ActiveGuaranteedCapture(
        val battleId: UUID,
        val targetPnx: String,
        val pokemonName: String,
        val pokemonLevel: Int,
        val result: CatchRateResult,
        val environmentSnapshot: String
    )

    private val activeCaptures = mutableMapOf<String, ActiveGuaranteedCapture>()

    fun onBattleStarted(battleId: UUID) {
        activeCaptures.entries.removeIf { it.value.battleId != battleId }
    }

    fun onBattleEnded() {
        activeCaptures.clear()
    }

    fun onCaptureStart(packet: BattleCaptureStartPacket) {
        val battle = CobblemonClient.battle ?: return
        val battleId = battle.battleId
        val targetPnx = packet.targetPNX
        val activePokemon = try {
            battle.getPokemonFromPNX(targetPnx).second
        } catch (_: Throwable) {
            null
        } ?: return

        val pokemon = activePokemon.battlePokemon ?: return
        val turnCount = CatchRateBattleMonitor.getTurnCount(battleId)
        val result = try {
            CatchRateCalculator.calculateCatchRate(pokemon, packet.pokeBallType, turnCount, null, true)
        } catch (e: Throwable) {
            CatchRateDebugLog.logIncident("Failed authoritative capture calculation for $targetPnx: ${e.javaClass.simpleName}: ${e.message}")
            null
        } ?: return

        val key = key(battleId, targetPnx)
        if (!result.isGuaranteed) {
            activeCaptures.remove(key)
            CatchRateDebugLog.logIncident("Capture start for ${pokemon.species.name} Lv${pokemon.level} with ${result.ballName} was not guaranteed")
            return
        }
        if (!result.isReliableGuaranteedPrediction) {
            activeCaptures.remove(key)
            CatchRateDebugLog.logIncident("Skipped guaranteed failure tracking for ${pokemon.species.name} Lv${pokemon.level} with ${result.ballName}: prediction was not reliable")
            return
        }

        val capture = ActiveGuaranteedCapture(
            battleId = battleId,
            targetPnx = targetPnx,
            pokemonName = pokemon.species.name,
            pokemonLevel = pokemon.level,
            result = result,
            environmentSnapshot = CatchRateDebugLog.buildEnvironmentSnapshot()
        )
        activeCaptures[key] = capture
        CatchRateDebugLog.logIncident(
            "Armed authoritative guaranteed capture check for ${capture.pokemonName} Lv${capture.pokemonLevel} " +
                "with ${capture.result.ballName} on turn ${capture.result.turnCount} (battle=$battleId target=$targetPnx)"
        )
    }

    fun onCaptureEnd(packet: BattleCaptureEndPacket) {
        val battleId = CobblemonClient.battle?.battleId
        val targetPnx = packet.targetPNX
        val key = if (battleId != null) key(battleId, targetPnx) else null
        val capture = when {
            key != null -> activeCaptures.remove(key)
            else -> {
                val match = activeCaptures.entries.firstOrNull { it.value.targetPnx == targetPnx }
                match?.also { activeCaptures.remove(it.key) }?.value
            }
        } ?: return

        if (packet.succeeded) {
            CatchRateDebugLog.logIncident(
                "Confirmed guaranteed capture success for ${capture.pokemonName} Lv${capture.pokemonLevel} with ${capture.result.ballName}"
            )
            return
        }

        val failure = CatchRateDebugLog.GuaranteedFailure(
            timestamp = CatchRateDebugLog.currentTimestamp(),
            battleId = capture.battleId.toString(),
            targetPNX = capture.targetPnx,
            pokemonName = capture.pokemonName,
            pokemonLevel = capture.pokemonLevel,
            ballName = capture.result.ballName,
            ballMultiplier = capture.result.ballMultiplier,
            baseCatchRate = capture.result.baseCatchRate,
            modifiedCatchRate = capture.result.modifiedCatchRate,
            hpPercentage = capture.result.hpPercentage,
            statusName = capture.result.statusName,
            statusMultiplier = capture.result.statusMultiplier,
            turnCount = capture.result.turnCount,
            ballConditionMet = capture.result.ballConditionMet,
            ballConditionReason = capture.result.ballConditionReason,
            isCatchRateEstimate = capture.result.isCatchRateEstimate,
            environmentSnapshot = capture.environmentSnapshot
        )
        CatchRateDebugLog.recordGuaranteedFailure(failure)
    }

    private fun key(battleId: UUID, targetPnx: String): String = "$battleId::$targetPnx"
}