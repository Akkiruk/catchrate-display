package com.catchrate

import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.battle.ClientBattleActor
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.battle.ClientBattleSide
import net.minecraft.client.Minecraft

/**
 * Works out "which side am I on, and is the other side catchable" without relying on
 * ClientBattle.isPvW. isPvW rejects any battle where the player's side holds a non-player
 * actor, which is exactly what Asymmetric Battles API does for multi battles (Cobblemon SOS
 * Encounters turns every wild singles fight into one): the empty ally slot is filled with a
 * dummy actor typed WILD. That made the HUD disappear from turn one in those battles.
 */
object BattleSides {

    private fun localUuid() = Minecraft.getInstance().player?.uuid

    private fun ownActor(battle: ClientBattle): ClientBattleActor? {
        val uuid = localUuid() ?: return null
        return battle.sides.asSequence().flatMap { it.actors }.firstOrNull { it.uuid == uuid }
    }

    fun playerSide(battle: ClientBattle): ClientBattleSide? = try {
        ownActor(battle)?.side
            ?: battle.sides.firstOrNull { side -> side.actors.any { it.type == ActorType.PLAYER } }
    } catch (_: Throwable) { null }

    fun opponentSide(battle: ClientBattle): ClientBattleSide? = try {
        val mine = playerSide(battle)
        if (mine == null) null else battle.sides.firstOrNull { it !== mine }
    } catch (_: Throwable) { null }

    /**
     * True when the opposing side is made up only of wild Pokemon (placeholder actors with no
     * Pokemon are ignored), i.e. something a ball can actually be thrown at.
     */
    fun isWildBattle(battle: ClientBattle): Boolean {
        if (battle.isPvW) return true
        return try {
            val opponents = opponentSide(battle)?.actors?.filter { it.pokemon.isNotEmpty() || it.activePokemon.any { a -> a.battlePokemon != null } }
            !opponents.isNullOrEmpty() && opponents.all { it.type == ActorType.WILD }
        } catch (_: Throwable) { false }
    }

    /**
     * Every wild Pokemon on the field that can still be caught, in slot order. Fainted ones are
     * dropped unless nothing else is left (so the HUD doesn't blink out the frame one faints).
     */
    fun opponentPokemonList(battle: ClientBattle): List<ClientBattlePokemon> = try {
        val active = opponentSide(battle)?.activeClientBattlePokemon?.mapNotNull { it.battlePokemon }.orEmpty()
            .distinctBy { it.uuid }
        active.filter { it.hpValue > 0f }.ifEmpty { active.take(1) }
    } catch (_: Throwable) { emptyList() }

    /** The first catchable wild Pokemon, for code that only cares about one target. */
    fun opponentPokemon(battle: ClientBattle): ClientBattlePokemon? = opponentPokemonList(battle).firstOrNull()

    /** The local player's own active Pokemon (Love Ball / Level Ball read it). */
    fun ownPokemon(battle: ClientBattle): ClientBattlePokemon? = try {
        ownActor(battle)?.activePokemon?.firstNotNullOfOrNull { it.battlePokemon }
            ?: playerSide(battle)?.activeClientBattlePokemon?.firstNotNullOfOrNull { it.battlePokemon }
    } catch (_: Throwable) { null }
}
