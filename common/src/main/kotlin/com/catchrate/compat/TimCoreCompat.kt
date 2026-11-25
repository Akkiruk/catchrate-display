package com.catchrate.compat

import com.catchrate.CatchRateMod
import com.catchrate.platform.PlatformHelper
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity.CaptureState
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// TIM Core 1.6.0+ has "preventQuickBallSpam" (on by default): the first Quick Ball that hits a
// Pokemon tags it, and every later Quick Ball on it gets no bonus, in any battle, turn 1 or not.
object TimCoreCompat {

    private const val HIT_FLAG = "tim_core:already_hit_with_quick_ball"

    // Pokemon we saw take a Quick Ball this session. On a server it's all we have to go on.
    private val hitThisSession = ConcurrentHashMap.newKeySet<UUID>()
    private val ballTargets = HashMap<Int, UUID>()

    @Volatile var revision = 0
        private set

    private val hasRule by lazy {
        PlatformHelper.isModLoaded("tim_core") && runCatching {
            Class.forName("us.timinc.mc.cobblemon.timcore.handler.PreventQuickBallSpam")
        }.isSuccess
    }

    private val configGetter: (() -> Boolean)? by lazy {
        runCatching {
            val timCore = Class.forName("us.timinc.mc.cobblemon.timcore.TimCore")
            val instance = timCore.getField("INSTANCE").get(null)
            val getConfig = timCore.getMethod("getConfig")
            val flag = getConfig.invoke(instance).javaClass.getMethod("getPreventQuickBallSpam")
            val getter: () -> Boolean = { flag.invoke(getConfig.invoke(instance)) as Boolean }
            getter
        }.getOrNull()
    }

    @Volatile private var fileStamp = Long.MIN_VALUE
    @Volatile private var fileValue = true

    fun isRuleActive(): Boolean {
        if (!hasRule) return false
        // Same object TIM reads, so in singleplayer this is exactly the server's setting. On a
        // server it's the client's copy of tim_core.json, which packs ship identical.
        configGetter?.let { get -> runCatching(get).getOrNull()?.let { return it } }
        return readConfigFile()
    }

    fun isQuickBallSpent(pokemonUuid: UUID?, entity: PokemonEntity?): Boolean {
        if (pokemonUuid == null || !isRuleActive()) return false
        return serverFlag(entity) ?: (pokemonUuid in hitThisSession)
    }

    fun markHit(pokemonUuid: UUID) {
        if (isRuleActive() && hitThisSession.add(pokemonUuid)) revision++
    }

    fun onClientTick() {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player == null || mc.level == null) {
            if (hitThisSession.isNotEmpty() || ballTargets.isNotEmpty()) {
                hitThisSession.clear()
                ballTargets.clear()
                revision++
            }
            return
        }
        if (!isRuleActive()) return

        // Covers throws outside battle and by other players, which never send us a capture packet.
        // The Pokemon points at the ball while it's being beamed in, and TIM tags it once the
        // ball starts shaking, since that's when the capture roll happens.
        val area = player.boundingBox.inflate(96.0)
        val level = mc.level!!
        val balls = level.getEntitiesOfClass(EmptyPokeBallEntity::class.java, area) {
            it.pokeBall.name.path == "quick_ball"
        }
        ballTargets.keys.retainAll(balls.map { it.id }.toSet())
        for (ball in balls) {
            when (ball.captureState) {
                CaptureState.HIT -> if (ball.id !in ballTargets) {
                    level.getEntitiesOfClass(PokemonEntity::class.java, area) { it.phasingTargetId == ball.id }
                        .firstOrNull()?.let { ballTargets[ball.id] = it.pokemon.uuid }
                }
                CaptureState.SHAKE, CaptureState.CRITICAL, CaptureState.CAPTURED, CaptureState.BROKEN_FREE ->
                    ballTargets.remove(ball.id)?.let { markHit(it) }
                else -> {}
            }
        }
    }

    // Singleplayer only: read TIM's tag straight off the server's copy of the Pokemon.
    private fun serverFlag(entity: PokemonEntity?): Boolean? {
        entity ?: return null
        val server = Minecraft.getInstance().singleplayerServer ?: return null
        // reading another thread's entity, so a bad moment can throw; the session memory is the fallback
        return runCatching {
            val serverEntity = server.getLevel(entity.level().dimension())?.getEntity(entity.id) as? PokemonEntity
            serverEntity?.pokemon?.persistentData?.contains(HIT_FLAG)
        }.getOrNull()
    }

    private fun readConfigFile(): Boolean {
        // TIM falls back to its defaults (rule on) for a missing file or key
        val path = PlatformHelper.getConfigDir().resolve("tim_core.json")
        val stamp = if (Files.exists(path)) Files.getLastModifiedTime(path).toMillis() else -1L
        if (stamp == fileStamp) return fileValue
        fileValue = if (stamp < 0) true else try {
            Files.newBufferedReader(path).use { reader ->
                JsonParser.parseReader(reader).asJsonObject.get("preventQuickBallSpam")?.asBoolean ?: true
            }
        } catch (e: Exception) {
            CatchRateMod.debugOnChange("TimCoreErr", "config", "Couldn't read tim_core.json: ${e.message}")
            true
        }
        fileStamp = stamp
        return fileValue
    }
}
