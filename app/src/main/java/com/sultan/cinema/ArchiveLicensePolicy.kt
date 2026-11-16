package com.sultan.cinema

/**
 * Accepts only known Creative Commons licenses and public-domain tools.
 * The app streams original items from their source; it does not edit or rehost them.
 */
internal object ArchiveLicensePolicy {
    private val ccLicense = Regex(
        "^https?://creativecommons\\.org/licenses/(by|by-sa|by-nd|by-nc|by-nc-sa|by-nc-nd)/(1\\.0|2\\.0|2\\.5|3\\.0|4\\.0)$",
        RegexOption.IGNORE_CASE
    )
    private val publicDomain = Regex(
        "^https?://creativecommons\\.org/publicdomain/(mark|zero)/1\\.0$",
        RegexOption.IGNORE_CASE
    )

    fun normalized(raw: String): String =
        raw.trim().trimEnd('/').replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")

    fun isApproved(raw: String): Boolean {
        val value = normalized(raw)
        return ccLicense.matches(value) || publicDomain.matches(value)
    }

    fun displayName(raw: String): String {
        val value = normalized(raw).lowercase()
        if (value.endsWith("/publicdomain/zero/1.0")) return "CC0 1.0"
        if (value.endsWith("/publicdomain/mark/1.0")) return "Public Domain Mark 1.0"
        val match = Regex("/licenses/(by(?:-sa|-nd|-nc|-nc-sa|-nc-nd)?)/(1\\.0|2\\.0|2\\.5|3\\.0|4\\.0)$").find(value)
            ?: return "Лицензия Creative Commons"
        return "CC " + match.groupValues[1].uppercase() + " " + match.groupValues[2]
    }

    // Search for all Creative Commons license URLs; each returned item is then checked against the allowlist above.
    fun searchClause(): String = "licenseurl:http*creativecommons.org*"

    fun isSafeIdentifier(value: String): Boolean =
        value.matches(Regex("[A-Za-z0-9._-]{1,100}"))
}
