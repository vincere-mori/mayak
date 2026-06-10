package app.mayak.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountryDetectorTest {

    @Test
    fun detectsFromFlagEmoji() {
        assertEquals("NL", CountryDetector.detect("🇳🇱 Amsterdam"))
    }

    @Test
    fun detectsFromEnglishCountryName() {
        assertEquals("DE", CountryDetector.detect("Germany-Frankfurt-01"))
    }

    @Test
    fun detectsFromRussianCountryName() {
        assertEquals("JP", CountryDetector.detect("Япония Токио 2"))
    }

    @Test
    fun detectsFromCity() {
        assertEquals("FI", CountryDetector.detect("Helsinki Premium"))
    }

    @Test
    fun detectsFromIsoCodeToken() {
        assertEquals("US", CountryDetector.detect("US West premium"))
    }

    @Test
    fun returnsNullWhenUnknown() {
        assertNull(CountryDetector.detect("fast-server-01"))
    }
}
