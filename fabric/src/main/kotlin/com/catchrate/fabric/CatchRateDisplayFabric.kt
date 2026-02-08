package com.catchrate.fabric

import com.catchrate.CatchRateMod
import net.fabricmc.api.ModInitializer

/**
 * Fabric common entrypoint.
 * This is a pure client-side mod - no server networking or packets.
 */
class CatchRateDisplayFabric : ModInitializer {
    override fun onInitialize() {
        CatchRateMod.LOGGER.info("[CatchRateDisplay] Fabric common init")
    }
}
