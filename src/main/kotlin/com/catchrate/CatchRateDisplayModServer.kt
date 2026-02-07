package com.catchrate

import com.catchrate.network.CatchRateServerNetworking
import net.fabricmc.api.ModInitializer

class CatchRateDisplayModServer : ModInitializer {
    override fun onInitialize() {
        CatchRateServerNetworking.initialize()
        CatchRateDisplayMod.LOGGER.info("Server initialized")
    }
}
