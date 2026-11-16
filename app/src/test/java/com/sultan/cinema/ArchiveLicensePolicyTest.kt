package com.sultan.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveLicensePolicyTest {
    @Test fun acceptsPublicDomainMarkAndCc0() {
        assertTrue(ArchiveLicensePolicy.isApproved("https://creativecommons.org/publicdomain/mark/1.0/"))
        assertTrue(ArchiveLicensePolicy.isApproved("http://creativecommons.org/publicdomain/zero/1.0/"))
    }

    @Test fun acceptsRecognizedCreativeCommonsLicenses() {
        assertTrue(ArchiveLicensePolicy.isApproved("https://creativecommons.org/licenses/by/4.0/"))
        assertTrue(ArchiveLicensePolicy.isApproved("http://creativecommons.org/licenses/by-sa/3.0/"))
        assertTrue(ArchiveLicensePolicy.isApproved("https://creativecommons.org/licenses/by-nc-nd/2.5/"))
        assertTrue(ArchiveLicensePolicy.isApproved("https://creativecommons.org/licenses/by-nd/4.0"))
    }

    @Test fun rejectsNonCcAndRetiredUsSpecificPublicDomainDedication() {
        assertFalse(ArchiveLicensePolicy.isApproved("https://example.com/film"))
        assertFalse(ArchiveLicensePolicy.isApproved("https://creativecommons.org/licenses/publicdomain/"))
        assertFalse(ArchiveLicensePolicy.isApproved("https://creativecommons.org/licenses/by/4.0.evil"))
        assertFalse(ArchiveLicensePolicy.isApproved(""))
    }

    @Test fun labelsLicensesAccurately() {
        assertEquals("CC BY 4.0", ArchiveLicensePolicy.displayName("https://creativecommons.org/licenses/by/4.0/"))
        assertEquals("CC BY-NC-ND 3.0", ArchiveLicensePolicy.displayName("http://creativecommons.org/licenses/by-nc-nd/3.0/"))
        assertEquals("CC0 1.0", ArchiveLicensePolicy.displayName("https://creativecommons.org/publicdomain/zero/1.0/"))
    }

    @Test fun usesBroadCreativeCommonsDiscoveryQuery() {
        assertEquals("(licenseurl:http*by* OR licenseurl:http*publicdomain/mark* OR licenseurl:http*publicdomain/zero*)", ArchiveLicensePolicy.searchClause())
    }

    @Test fun onlyAllowsSafeArchiveIdentifiers() {
        assertTrue(ArchiveLicensePolicy.isSafeIdentifier("classic_film-1931"))
        assertFalse(ArchiveLicensePolicy.isSafeIdentifier("../metadata"))
        assertFalse(ArchiveLicensePolicy.isSafeIdentifier(""))
    }
}
