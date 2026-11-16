package com.sultan.cinema

/**
 * Fail-closed policy for titles displayed by the built-in free catalogue.
 * Only explicit public-domain designation or CC0 dedication is accepted.
 */
internal object ArchiveLicensePolicy {
    private const val PUBLIC_DOMAIN_MARK =
        "https://creativecommons.org/publicdomain/mark/1.0"
    private const val CC0 =
        "https://creativecommons.org/publicdomain/zero/1.0"

    fun isApproved(raw: String): Boolean {
        val normalized = raw.trim().trimEnd('/')
            .lowercase()
            .replaceFirst("http://", "https://")
        return normalized == PUBLIC_DOMAIN_MARK || normalized == CC0
    }

    // Internet Archive's own search guidance recommends the broad publicdomain wildcard.
    // It matches both Public Domain Mark and CC0 URLs; exact per-item validation remains fail-closed.
    fun searchClause(): String = "licenseurl:http*publicdomain*"

    fun isSafeIdentifier(value: String): Boolean =
        value.matches(Regex("[A-Za-z0-9._-]{1,100}"))
}
