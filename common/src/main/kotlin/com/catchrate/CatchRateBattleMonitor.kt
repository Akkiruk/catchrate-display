package com.catchrate

import com.cobblemon.mod.common.client.CobblemonClient
import java.util.UUID

object CatchRateBattleMonitor {

    private var currentBattleId: UUID? = null
    private var turnCount = 1
    private var lastMustChoose = false
    private var mustChooseTransitions = 0

    fun onClientTick() {
        val battle = CobblemonClient.battle
        if (battle == null) {
            if (currentBattleId != null) {
                CatchRateDebugLog.onBattleEnd()
                CatchRateCaptureTracker.onBattleEnded()
                reset()
            }
            return
        }

        if (battle.battleId != currentBattleId) {
            currentBattleId = battle.battleId
            turnCount = 1
            lastMustChoose = false
            mustChooseTransitions = 0
            CatchRateCaptureTracker.onBattleStarted(battle.battleId)
            CatchRateDebugLog.onBattleObserved(battle.battleId.toString(), battle.isPvW)
        }

        val nowMustChoose = battle.mustChoose
        if (nowMustChoose && !lastMustChoose) {
            mustChooseTransitions++
            if (mustChooseTransitions > 1) {
                turnCount++
                CatchRateDebugLog.logIncident("Battle turn advanced to $turnCount for ${battle.battleId}")
            }
        }

        lastMustChoose = nowMustChoose

        // Runs every tick regardless of held item so a disguising Pokemon (e.g. Ditto's
        // Transform) is captured in its true form even if the player hasn't drawn a
        // Pokeball yet. See CatchRatePredictionReliability.onClientTick for why this can't
        // just live in the HUD render path.
        CatchRatePredictionReliability.onClientTick(battle)
    }

    fun getTurnCount(expectedBattleId: UUID? = currentBattleId): Int {
        return if (expectedBattleId == null || expectedBattleId == currentBattleId) turnCount else 1
    }

    private fun reset() {
        currentBattleId = null
        turnCount = 1
        lastMustChoose = false
        mustChooseTransitions = 0
    }
}