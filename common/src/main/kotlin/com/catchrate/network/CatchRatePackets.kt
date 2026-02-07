package com.catchrate.network

import com.catchrate.CatchRateMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

object CatchRatePackets {
    val REQUEST_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CatchRateMod.MOD_ID, "catch_rate_request")
    val RESPONSE_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(CatchRateMod.MOD_ID, "catch_rate_response")
}

data class CatchRateRequestPayload(
    val pokemonUuid: UUID,
    val ballItemId: String,
    val turnCount: Int
) : CustomPacketPayload {
    
    companion object {
        val TYPE: CustomPacketPayload.Type<CatchRateRequestPayload> = CustomPacketPayload.Type(CatchRatePackets.REQUEST_ID)
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CatchRateRequestPayload> = object : StreamCodec<RegistryFriendlyByteBuf, CatchRateRequestPayload> {
            override fun encode(buf: RegistryFriendlyByteBuf, payload: CatchRateRequestPayload) {
                buf.writeLong(payload.pokemonUuid.mostSignificantBits)
                buf.writeLong(payload.pokemonUuid.leastSignificantBits)
                buf.writeUtf(payload.ballItemId)
                buf.writeInt(payload.turnCount)
            }
            
            override fun decode(buf: RegistryFriendlyByteBuf): CatchRateRequestPayload {
                return CatchRateRequestPayload(
                    pokemonUuid = UUID(buf.readLong(), buf.readLong()),
                    ballItemId = buf.readUtf(),
                    turnCount = buf.readInt()
                )
            }
        }
    }
    
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
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
) : CustomPacketPayload {
    
    companion object {
        val TYPE: CustomPacketPayload.Type<CatchRateResponsePayload> = CustomPacketPayload.Type(CatchRatePackets.RESPONSE_ID)
        
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, CatchRateResponsePayload> = object : StreamCodec<RegistryFriendlyByteBuf, CatchRateResponsePayload> {
            override fun encode(buf: RegistryFriendlyByteBuf, payload: CatchRateResponsePayload) {
                buf.writeLong(payload.pokemonUuid.mostSignificantBits)
                buf.writeLong(payload.pokemonUuid.leastSignificantBits)
                buf.writeDouble(payload.catchChance)
                buf.writeUtf(payload.pokemonName)
                buf.writeInt(payload.pokemonLevel)
                buf.writeDouble(payload.hpPercent)
                buf.writeUtf(payload.statusEffect)
                buf.writeUtf(payload.ballName)
                buf.writeDouble(payload.ballMultiplier)
                buf.writeBoolean(payload.ballConditionMet)
                buf.writeUtf(payload.ballConditionDesc)
                buf.writeDouble(payload.statusMultiplier)
                buf.writeDouble(payload.lowLevelBonus)
                buf.writeBoolean(payload.isGuaranteed)
                buf.writeInt(payload.baseCatchRate)
            }
            
            override fun decode(buf: RegistryFriendlyByteBuf): CatchRateResponsePayload {
                return CatchRateResponsePayload(
                    pokemonUuid = UUID(buf.readLong(), buf.readLong()),
                    catchChance = buf.readDouble(),
                    pokemonName = buf.readUtf(),
                    pokemonLevel = buf.readInt(),
                    hpPercent = buf.readDouble(),
                    statusEffect = buf.readUtf(),
                    ballName = buf.readUtf(),
                    ballMultiplier = buf.readDouble(),
                    ballConditionMet = buf.readBoolean(),
                    ballConditionDesc = buf.readUtf(),
                    statusMultiplier = buf.readDouble(),
                    lowLevelBonus = buf.readDouble(),
                    isGuaranteed = buf.readBoolean(),
                    baseCatchRate = buf.readInt()
                )
            }
        }
    }
    
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
