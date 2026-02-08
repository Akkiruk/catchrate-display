@file:JvmName("PlatformHelperImpl")

package com.catchrate.platform.neoforge

import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Path

/**
 * NeoForge implementation of PlatformHelper.
 */
object PlatformHelperImpl {
    
    @JvmStatic
    fun getConfigDir(): Path {
        return FMLPaths.CONFIGDIR.get()
    }
}
