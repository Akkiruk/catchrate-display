@file:JvmName("PlatformHelperImpl")

package com.catchrate.platform.neoforge

import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.ModList
import java.nio.file.Path

/**
 * NeoForge implementation of PlatformHelper.
 */
object PlatformHelperImpl {
    
    @JvmStatic
    fun getConfigDir(): Path {
        return FMLPaths.CONFIGDIR.get()
    }

    @JvmStatic
    fun getGameDir(): Path {
        return FMLPaths.GAMEDIR.get()
    }

    @JvmStatic
    fun isModLoaded(modId: String): Boolean {
        return ModList.get().isLoaded(modId)
    }
}
