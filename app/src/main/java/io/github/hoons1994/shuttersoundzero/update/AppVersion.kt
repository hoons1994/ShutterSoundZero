package io.github.hoons1994.shuttersoundzero.update

/** Release tags use vMAJOR.MINOR.PATCH; debug builds compare using their base version. */
internal data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    companion object {
        fun parse(value: String, allowDebugSuffix: Boolean = false): AppVersion? {
            val normalized = if (allowDebugSuffix) value.removeSuffix("-debug") else value
            val match = Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matchEntire(normalized)
                ?: return null
            val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return AppVersion(parts[0], parts[1], parts[2])
        }
    }
}
