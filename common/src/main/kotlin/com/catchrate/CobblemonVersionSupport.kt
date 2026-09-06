package com.catchrate

/**
 * Runtime Cobblemon version helpers for behavior that changed between releases.
 */
object CobblemonVersionSupport {

    enum class AncientBallProfile {
        LEGACY_PRE_180,
        RESPECTIVE_MODIFIERS_180
    }

    private data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int
    ) : Comparable<ParsedVersion> {
        override fun compareTo(other: ParsedVersion): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }
    }

    private val rawVersion by lazy { resolveCobblemonVersion() }
    private val parsedVersion by lazy { parse(rawVersion) }
    // The ancient-ball buffs (leaden/gigaton/wing/jet) were introduced exactly in 1.8.0, so the
    // gate gate is >= 1.8.0 rather than "> 1.7.3" — if Cobblemon ever ships a 1.7.x patch without
    // the fix, that boundary would have wrongly reported it as fixed.
    private val ancientBallFixBoundary = ParsedVersion(1, 8, 0)

    fun cobblemonVersion(): String = rawVersion

    fun ancientBallProfile(): AncientBallProfile {
        val parsed = parsedVersion ?: return AncientBallProfile.LEGACY_PRE_180
        return if (parsed >= ancientBallFixBoundary) {
            AncientBallProfile.RESPECTIVE_MODIFIERS_180
        } else {
            AncientBallProfile.LEGACY_PRE_180
        }
    }

    fun ancientBallProfileLabel(): String {
        return when (ancientBallProfile()) {
            AncientBallProfile.LEGACY_PRE_180 -> "1.7.x and earlier (Great/Ultra/Origin only)"
            AncientBallProfile.RESPECTIVE_MODIFIERS_180 -> "1.8.0+ (Great/Ultra/Leaden/Wing/Gigaton/Jet/Origin)"
        }
    }

    private fun resolveCobblemonVersion(): String {
        // Cobblemon.VERSION is a Kotlin `const val`, which the compiler inlines as a literal at
        // compile time — a direct reference would always report whatever version this mod was
        // built against, not whatever Cobblemon jar is actually loaded. Read it via reflection so
        // this reflects the real runtime version.
        return try {
            val cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon")
            cobblemonClass.getField("VERSION").get(null) as? String ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun parse(version: String): ParsedVersion? {
        val match = VERSION_REGEX.find(version) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: 0
        return ParsedVersion(major, minor, patch)
    }

    private val VERSION_REGEX = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")
}