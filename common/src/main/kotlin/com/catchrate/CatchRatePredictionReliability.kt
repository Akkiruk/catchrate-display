package com.catchrate

import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Species
import java.util.UUID

/**
 * Recovers the true species of a battle Pokemon whose client-visible species can change
 * mid-battle (Transform, and in principle Illusion) without a real species change happening.
 *
 * Cobblemon has no field that survives this with the pre-change species intact:
 * [ClientBattlePokemon.species] is a live getter off `properties`, and `BattleTransformPokemonPacket`
 * overwrites `properties`/`aspects` in place when Transform resolves. The entity-side
 * `effects.mockEffect` that Cobblemon's own code uses server-side to know a Pokemon is disguised
 * is set only by the battle interpreter (`TransformInstruction`/`SwitchInstruction`), which runs
 * exclusively on the server — it is never networked to observing clients, so reading it here
 * always sees null and can't be used as a signal.
 *
 * What *is* reliable: a wild Pokemon is always sent out, and therefore observed once in its
 * true form, before it can act (a battle can't start already mid-Transform). So the species
 * seen the very first time this client observes a given battle Pokemon's UUID is trustworthy
 * for the rest of that battle, and is kept instead of whatever `species` reports afterward.
 */
object CatchRatePredictionReliability {

    private val firstSeenBattleSpecies = mutableMapOf<UUID, Species>()

    fun clearBattleMemory() {
        firstSeenBattleSpecies.clear()
    }

    data class Reliability(
        val isReliable: Boolean,
        val reason: String = "",
        val effectiveSpecies: Species? = null
    )

    /**
     * Observes the current wild opponent every client tick, independent of whether the HUD
     * is actually rendering (which only happens while a Pokeball is held). Without this, a
     * same-turn Transform could beat the client to its only clean observation.
     */
    fun onClientTick(battle: ClientBattle?) {
        if (battle == null || !battle.isPvW) return
        val opponent = try {
            battle.side2.activeClientBattlePokemon.firstOrNull()?.battlePokemon
        } catch (_: Throwable) {
            null
        } ?: return
        observe(opponent)
    }

    private fun observe(pokemon: ClientBattlePokemon) {
        // putIfAbsent, never overwritten: any later sighting could already be post-Transform.
        firstSeenBattleSpecies.putIfAbsent(pokemon.uuid, pokemon.species)
    }

    fun analyzeBattleTarget(pokemon: ClientBattlePokemon, battle: ClientBattle?): Reliability {
        val visibleSpecies = pokemon.species
        if (battle == null || !battle.isPvW) return Reliability(true, effectiveSpecies = visibleSpecies)

        observe(pokemon)
        val trueSpecies = firstSeenBattleSpecies[pokemon.uuid] ?: visibleSpecies
        return if (trueSpecies.resourceIdentifier != visibleSpecies.resourceIdentifier) {
            Reliability(true, "Using species observed at send-out, before any mid-battle change", trueSpecies)
        } else {
            Reliability(true, effectiveSpecies = visibleSpecies)
        }
    }

    /** No battle-side transform is possible for a world (non-battle) target, so its currently
     * observed species is always the true one. */
    fun analyzeWorldTarget(entity: PokemonEntity): Reliability {
        return Reliability(true, effectiveSpecies = entity.pokemon.species)
    }
}
