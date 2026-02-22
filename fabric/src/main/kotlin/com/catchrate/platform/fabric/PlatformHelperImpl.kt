@file:JvmName("PlatformHelperImpl")

package com.catchrate.platform.fabric

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

/**
 * Fabric implementation of PlatformHelper.
 */
object PlatformHelperImpl {
    
    @JvmStatic
    fun getConfigDir(): Path {
        return FabricLoader.getInstance().configDir
    }

    @JvmStatic
    fun getGameDir(): Path {
        return FabricLoader.getInstance().gameDir
    }
}
