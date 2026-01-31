package com.catchrate

import com.catchrate.network.CatchRateServerNetworking
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

class CatchRateDisplayModServer : ModInitializer {
    companion object {
        const val MOD_ID = "catchrate-display"
        val LOGGER = LoggerFactory.getLogger(MOD_ID)
    }
    
    override fun onInitialize() {
        CatchRateServerNetworking.initialize()
        LOGGER.info("Server initialized")
    }
}
