package com.sultan.cinema

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveLicensePolicyTest {
    @Test fun acceptsPublicDomainMarkHttpsAndHttp() {
        assertTrue(ArchiveLicensePolicy.isApproved("https://creativecommons.org/publicdomain/mark/1.0/"))
        assertTrue(ArchiveLicensePolicy.isApproved("http://creativecommons.org/publicdomain/mark/1.0"))
    }

    @Test fun acceptsCcZeroOnly() {
        assertTrue(ArchiveLicensePolicy.isApproved("https://creativecommons.org/publicdomain/zero/1.0/"))
    }

    @Test fun usesArchivePublicDomainWildcardForDiscovery() {
        assertTrue(ArchiveLicensePolicy.searchClause().contains("licenseurl:http*publicdomain*"))
    }

    @Test fun rejectsAttributionAndUnknownLicenses() {
        assertFalse(ArchiveLicensePolicy.isApproved("https://creativecommons.org/licenses/by/4.0/"))
        assertFalse(ArchiveLicensePolicy.isApproved(""))
        assertFalse(ArchiveLicensePolicy.isApproved("unknown"))
    }

    @Test fun rejectsLookalikeLicenseUrls() {
        assertFalse(ArchiveLicensePolicy.isApproved("https://example.com/creativecommons.org/publicdomain/mark/1.0"))
        assertFalse(ArchiveLicensePolicy.isApproved("https://creativecommons.org/publicdomain/mark/1.0.evil"))
    }

    @Test fun onlyAllowsSafeArchiveIdentifiers() {
        assertTrue(ArchiveLicensePolicy.isSafeIdentifier("classic_film-1931"))
        assertFalse(ArchiveLicensePolicy.isSafeIdentifier("../metadata"))
        assertFalse(ArchiveLicensePolicy.isSafeIdentifier(""))
    }
}
