package com.catchrate.platform

import dev.architectury.injectables.annotations.ExpectPlatform
import java.nio.file.Path

/**
 * Platform abstraction via @ExpectPlatform.
 * Each platform provides implementations in {package}.fabric.platform.PlatformHelperImpl
 * or {package}.neoforge.platform.PlatformHelperImpl respectively.
 */
object PlatformHelper {

    @JvmStatic
    @ExpectPlatform
    fun getConfigDir(): Path = throw AssertionError("@ExpectPlatform not replaced")

    @JvmStatic
    @ExpectPlatform
    fun getGameDir(): Path = throw AssertionError("@ExpectPlatform not replaced")
}
