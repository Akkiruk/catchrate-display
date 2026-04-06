package com.catchrate

import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Species
import java.util.UUID
import net.minecraft.client.Minecraft

object CatchRatePredictionReliability {

    private val rememberedBattleSpecies = mutableMapOf<UUID, Species>()

    fun clearBattleMemory() {
        rememberedBattleSpecies.clear()
    }

    data class Reliability(
        val isReliable: Boolean,
        val reason: String = "",
        val effectiveSpecies: Species? = null
    )

    fun analyzeBattleTarget(pokemon: ClientBattlePokemon, battle: ClientBattle?): Reliability {
        val visibleSpecies = pokemon.species
        if (battle == null || !battle.isPvW) return Reliability(true, effectiveSpecies = visibleSpecies)

        val wildEntities = getActiveWildBattleEntities(battle.battleId)
        if (wildEntities.isEmpty()) return Reliability(true, effectiveSpecies = visibleSpecies)

        val matchedEntity = wildEntities.firstOrNull { it.pokemon.uuid == pokemon.uuid }
        if (matchedEntity != null) {
            return if (isDisguised(matchedEntity)) {
                val rememberedSpecies = getRememberedSpecies(pokemon.uuid, battle.wildActor?.uuid)
                if (rememberedSpecies != null) {
                    Reliability(true, "Using remembered pre-disguise species", rememberedSpecies)
                } else {
                    Reliability(false, "Active wild entity is disguised", visibleSpecies)
                }
            } else {
                rememberSpecies(visibleSpecies, pokemon.uuid, battle.wildActor?.uuid)
                Reliability(true, effectiveSpecies = visibleSpecies)
            }
        }

        val actualWildUuid = battle.wildActor?.uuid
        val actualWildEntity = actualWildUuid?.let { wildUuid ->
            wildEntities.firstOrNull { it.pokemon.uuid == wildUuid }
        }
        if (actualWildEntity != null) {
            if (isDisguised(actualWildEntity)) {
                val rememberedSpecies = getRememberedSpecies(actualWildUuid)
                if (rememberedSpecies != null) {
                    return Reliability(true, "Using remembered pre-disguise species for hidden target", rememberedSpecies)
                }
                return Reliability(
                    false,
                    "Active wild entity is disguised and the client was never shown its true species",
                    visibleSpecies
                )
            }

            rememberSpecies(visibleSpecies, actualWildUuid)
            return Reliability(true, effectiveSpecies = visibleSpecies)
        }

        val disguisedEntity = wildEntities.firstOrNull(::isDisguised)
        return if (disguisedEntity != null) {
            Reliability(false, "Active wild entity is disguised and public battle data does not match it", visibleSpecies)
        } else {
            Reliability(false, "Public battle data does not match any active wild entity", visibleSpecies)
        }
    }

    fun analyzeWorldTarget(entity: PokemonEntity): Reliability {
        return if (isDisguised(entity)) {
            Reliability(false, "World target is disguised", entity.pokemon.species)
        } else {
            Reliability(true, effectiveSpecies = entity.pokemon.species)
        }
    }

    private fun rememberSpecies(species: Species, vararg keys: UUID?) {
        keys.forEach { key ->
            if (key != null) {
                rememberedBattleSpecies[key] = species
            }
        }
    }

    private fun getRememberedSpecies(vararg keys: UUID?): Species? {
        return keys.firstNotNullOfOrNull { key -> key?.let { rememberedBattleSpecies[it] } }
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