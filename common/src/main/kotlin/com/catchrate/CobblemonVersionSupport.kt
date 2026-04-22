package com.catchrate

/**
 * Runtime Cobblemon version helpers for behavior that changed between releases.
 */
object CobblemonVersionSupport {

    enum class AncientBallProfile {
        LEGACY_PRE_173_FIX,
        RESPECTIVE_MODIFIERS_POST_173
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
    private val ancientBallFixBoundary = ParsedVersion(1, 7, 3)

    fun cobblemonVersion(): String = rawVersion

    fun ancientBallProfile(): AncientBallProfile {
        val parsed = parsedVersion ?: return AncientBallProfile.LEGACY_PRE_173_FIX
        return if (parsed > ancientBallFixBoundary) {
            AncientBallProfile.RESPECTIVE_MODIFIERS_POST_173
        } else {
            AncientBallProfile.LEGACY_PRE_173_FIX
        }
    }

    fun ancientBallProfileLabel(): String {
        return when (ancientBallProfile()) {
            AncientBallProfile.LEGACY_PRE_173_FIX -> "1.7.3 and earlier (Great/Ultra/Origin only)"
            AncientBallProfile.RESPECTIVE_MODIFIERS_POST_173 -> "post-1.7.3 (Great/Ultra/Leaden/Wing/Gigaton/Jet/Origin)"
        }
    }

    private fun resolveCobblemonVersion(): String {
        return try {
            com.cobblemon.mod.common.Cobblemon.VERSION
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