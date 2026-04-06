package com.catchrate

import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import java.util.UUID
import net.minecraft.client.Minecraft

object CatchRatePredictionReliability {

    data class Reliability(
        val isReliable: Boolean,
        val reason: String = ""
    )

    fun analyzeBattleTarget(pokemon: ClientBattlePokemon, battle: ClientBattle?): Reliability {
        if (battle == null || !battle.isPvW) return Reliability(true)

        val wildEntities = getActiveWildBattleEntities(battle.battleId)
        if (wildEntities.isEmpty()) return Reliability(true)

        val matchedEntity = wildEntities.firstOrNull { it.pokemon.uuid == pokemon.uuid }
        if (matchedEntity != null) {
            return if (isDisguised(matchedEntity)) {
                Reliability(false, "Active wild entity is disguised")
            } else {
                Reliability(true)
            }
        }

        val disguisedEntity = wildEntities.firstOrNull(::isDisguised)
        return if (disguisedEntity != null) {
            Reliability(false, "Active wild entity is disguised and public battle data does not match it")
        } else {
            Reliability(false, "Public battle data does not match any active wild entity")
        }
    }

    fun analyzeWorldTarget(entity: PokemonEntity): Reliability {
        return if (isDisguised(entity)) {
            Reliability(false, "World target is disguised")
        } else {
            Reliability(true)
        }
    }

    private fun getActiveWildBattleEntities(battleId: UUID): List<PokemonEntity> {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return emptyList()
        val level = minecraft.level ?: return emptyList()

        return level.getEntitiesOfClass(
            PokemonEntity::class.java,
            player.boundingBox.inflate(128.0)
        ) {
            it.isAlive &&
                it.battleId == battleId &&
                it.ownerUUID == null &&
                it.pokemon.getOwnerUUID() == null
        }
    }

    private fun isDisguised(entity: PokemonEntity): Boolean {
        return try {
            entity.effects.mockEffect != null
        } catch (_: Throwable) {
            false
        }
    }
}