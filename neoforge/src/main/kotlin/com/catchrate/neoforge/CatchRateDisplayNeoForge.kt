package com.catchrate.neoforge

import com.catchrate.CatchRateMod
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

/**
 * NeoForge main mod entrypoint.
 * This is a pure client-side mod - no server networking or packets.
 */
@Mod(CatchRateMod.NEOFORGE_MOD_ID)
class CatchRateDisplayNeoForge(modBus: IEventBus) {
    
    init {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] NeoForge init")
    }
}
