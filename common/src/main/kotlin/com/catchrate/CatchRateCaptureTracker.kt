package com.catchrate

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureEndPacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleCaptureStartPacket
import java.util.UUID

object CatchRateCaptureTracker {

    private data class ActiveCapture(
        val battleId: UUID,
        val targetPnx: String,
        val pokemonName: String,
        val pokemonLevel: Int,
        val result: CatchRateResult,
        val environmentSnapshot: String?,
        val isGuaranteedTracked: Boolean
    )

    private val activeCaptures = mutableMapOf<String, ActiveCapture>()
    private val consumedQuickBallTargets = mutableSetOf<String>()

    fun onBattleStarted(battleId: UUID) {
        activeCaptures.entries.removeIf { it.value.battleId != battleId }
        consumedQuickBallTargets.clear()
    }

    fun onBattleEnded() {
        activeCaptures.clear()
        consumedQuickBallTargets.clear()
    }

    fun hasConsumedQuickBallBonus(battleId: UUID?, pokemonUuid: UUID?): Boolean {
        if (battleId == null || pokemonUuid == null) return false
        return quickBallKey(battleId, pokemonUuid) in consumedQuickBallTargets
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
        val consumedQuickBallKey = if (packet.pokeBallType.path.equals("quick_ball", ignoreCase = true)) {
            quickBallKey(battleId, pokemon.uuid)
        } else {
            null
        }
        val result = try {
            CatchRateCalculator.calculateCatchRate(pokemon, packet.pokeBallType, turnCount, null, true)
        } catch (e: Throwable) {
            CatchRateDebugLog.logIncident("Failed capture calculation for $targetPnx: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            consumedQuickBallKey?.let { consumedQuickBallTargets.add(it) }
        } ?: return

        val key = key(battleId, targetPnx)
        val trackGuaranteed = result.isGuaranteed && result.isReliableGuaranteedPrediction
        activeCaptures[key] = ActiveCapture(
            battleId = battleId,
            targetPnx = targetPnx,
            pokemonName = pokemon.species.name,
            pokemonLevel = pokemon.level,
            result = result,
            environmentSnapshot = if (trackGuaranteed) CatchRateDebugLog.buildEnvironmentSnapshot() else null,
            isGuaranteedTracked = trackGuaranteed
        )
        if (trackGuaranteed) {
            CatchRateDebugLog.logIncident(
                "Armed guaranteed capture check: ${pokemon.species.name} Lv${pokemon.level} " +
                    "with ${result.ballName} turn ${result.turnCount}"
            )
        }
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

        val r = capture.result
        // Always log the outcome to the rolling file
        CatchRateDebugLog.appendCatchOutcome(
            capture.pokemonName, capture.pokemonLevel, r.ballName,
            r.baseCatchRate, r.modifiedCatchRate, r.hpPercentage,
            r.statusName, r.statusMultiplier, r.ballMultiplier,
            r.turnCount, r.isCatchRateEstimate, r.isGuaranteed, packet.succeeded
        )

        if (!capture.isGuaranteedTracked) return

        if (packet.succeeded) {
            CatchRateDebugLog.logIncident(
                "Guaranteed capture confirmed: ${capture.pokemonName} Lv${capture.pokemonLevel} with ${r.ballName}"
            )
            return
        }

        val failure = CatchRateDebugLog.GuaranteedFailure(
            timestamp = CatchRateDebugLog.currentTimestamp(),
            battleId = capture.battleId.toString(),
            targetPNX = capture.targetPnx,
            pokemonName = capture.pokemonName,
            pokemonLevel = capture.pokemonLevel,
            ballName = r.ballName,
            ballMultiplier = r.ballMultiplier,
            baseCatchRate = r.baseCatchRate,
            modifiedCatchRate = r.modifiedCatchRate,
            hpPercentage = r.hpPercentage,
            statusName = r.statusName,
            statusMultiplier = r.statusMultiplier,
            turnCount = r.turnCount,
            ballConditionMet = r.ballConditionMet,
            ballConditionReason = r.ballConditionReason,
            isCatchRateEstimate = r.isCatchRateEstimate,
            environmentSnapshot = capture.environmentSnapshot ?: ""
        )
        CatchRateDebugLog.recordGuaranteedFailure(failure)
    }

    private fun key(battleId: UUID, targetPnx: String): String = "$battleId::$targetPnx"

    private fun quickBallKey(battleId: UUID, pokemonUuid: UUID): String = "$battleId::$pokemonUuid"
}