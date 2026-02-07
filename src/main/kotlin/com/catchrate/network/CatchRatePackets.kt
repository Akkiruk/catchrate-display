package com.catchrate.network

import com.catchrate.CatchRateDisplayMod
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

object CatchRatePackets {
    val REQUEST_ID: Identifier = Identifier.of(CatchRateDisplayMod.MOD_ID, "catch_rate_request")
    val RESPONSE_ID: Identifier = Identifier.of(CatchRateDisplayMod.MOD_ID, "catch_rate_response")
    val WORLD_REQUEST_ID: Identifier = Identifier.of(CatchRateDisplayMod.MOD_ID, "world_catch_rate_request")
}

data class CatchRateRequestPayload(
    val pokemonUuid: UUID,
    val ballItemId: String,
    val turnCount: Int
) : CustomPayload {
    
    companion object {
        val ID: CustomPayload.Id<CatchRateRequestPayload> = CustomPayload.Id(CatchRatePackets.REQUEST_ID)
        
        val CODEC: PacketCodec<RegistryByteBuf, CatchRateRequestPayload> = object : PacketCodec<RegistryByteBuf, CatchRateRequestPayload> {
            override fun encode(buf: RegistryByteBuf, payload: CatchRateRequestPayload) {
                buf.writeLong(payload.pokemonUuid.mostSignificantBits)
                buf.writeLong(payload.pokemonUuid.leastSignificantBits)
                buf.writeString(payload.ballItemId)
                buf.writeInt(payload.turnCount)
            }
            
            override fun decode(buf: RegistryByteBuf): CatchRateRequestPayload {
                return CatchRateRequestPayload(
                    pokemonUuid = UUID(buf.readLong(), buf.readLong()),
                    ballItemId = buf.readString(),
                    turnCount = buf.readInt()
                )
            }
        }
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}

data class CatchRateResponsePayload(
    val pokemonUuid: UUID,
    val catchChance: Double,
    val pokemonName: String,
    val pokemonLevel: Int,
    val hpPercent: Double,
    val statusEffect: String,
    val ballName: String,
    val ballMultiplier: Double,
    val ballConditionMet: Boolean,
    val ballConditionDesc: String,
    val statusMultiplier: Double,
    val lowLevelBonus: Double,
    val isGuaranteed: Boolean,
    val baseCatchRate: Int
) : CustomPayload {
    
    companion object {
        val ID: CustomPayload.Id<CatchRateResponsePayload> = CustomPayload.Id(CatchRatePackets.RESPONSE_ID)
        
        val CODEC: PacketCodec<RegistryByteBuf, CatchRateResponsePayload> = object : PacketCodec<RegistryByteBuf, CatchRateResponsePayload> {
            override fun encode(buf: RegistryByteBuf, payload: CatchRateResponsePayload) {
                buf.writeLong(payload.pokemonUuid.mostSignificantBits)
                buf.writeLong(payload.pokemonUuid.leastSignificantBits)
                buf.writeDouble(payload.catchChance)
                buf.writeString(payload.pokemonName)
                buf.writeInt(payload.pokemonLevel)
                buf.writeDouble(payload.hpPercent)
                buf.writeString(payload.statusEffect)
                buf.writeString(payload.ballName)
                buf.writeDouble(payload.ballMultiplier)
                buf.writeBoolean(payload.ballConditionMet)
                buf.writeString(payload.ballConditionDesc)
                buf.writeDouble(payload.statusMultiplier)
                buf.writeDouble(payload.lowLevelBonus)
                buf.writeBoolean(payload.isGuaranteed)
                buf.writeInt(payload.baseCatchRate)
            }
            
            override fun decode(buf: RegistryByteBuf): CatchRateResponsePayload {
                return CatchRateResponsePayload(
                    pokemonUuid = UUID(buf.readLong(), buf.readLong()),
                    catchChance = buf.readDouble(),
                    pokemonName = buf.readString(),
                    pokemonLevel = buf.readInt(),
                    hpPercent = buf.readDouble(),
                    statusEffect = buf.readString(),
                    ballName = buf.readString(),
                    ballMultiplier = buf.readDouble(),
                    ballConditionMet = buf.readBoolean(),
                    ballConditionDesc = buf.readString(),
                    statusMultiplier = buf.readDouble(),
                    lowLevelBonus = buf.readDouble(),
                    isGuaranteed = buf.readBoolean(),
                    baseCatchRate = buf.readInt()
                )
            }
        }
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}

/**
 * Request payload for out-of-combat world Pokemon catch rate calculation.
 * Uses the entity's network ID (always available on client) to identify the Pokemon.
 */
data class WorldCatchRateRequestPayload(
    val entityId: Int,
    val ballItemId: String
) : CustomPayload {
    
    companion object {
        val ID: CustomPayload.Id<WorldCatchRateRequestPayload> = CustomPayload.Id(CatchRatePackets.WORLD_REQUEST_ID)
        
        val CODEC: PacketCodec<RegistryByteBuf, WorldCatchRateRequestPayload> = object : PacketCodec<RegistryByteBuf, WorldCatchRateRequestPayload> {
            override fun encode(buf: RegistryByteBuf, payload: WorldCatchRateRequestPayload) {
                buf.writeInt(payload.entityId)
                buf.writeString(payload.ballItemId)
            }
            
            override fun decode(buf: RegistryByteBuf): WorldCatchRateRequestPayload {
                return WorldCatchRateRequestPayload(
                    entityId = buf.readInt(),
                    ballItemId = buf.readString()
                )
            }
        }
    }
    
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID
}
