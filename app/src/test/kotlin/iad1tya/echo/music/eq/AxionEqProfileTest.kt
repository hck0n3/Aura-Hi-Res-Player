package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.ui.screens.equalizer.axion.buildEqBands
import org.junit.Assert.assertEquals
import org.junit.Test

class AxionEqProfileTest {

    @Test
    fun buildsOneBandPerFrequencyWithDbGainsDirect() {
        val gains = FloatArray(24) { it.toFloat() - 6f } // -6..+17 dB
        val types = IntArray(24) { 0 }
        val bands = buildEqBands(gains, types)

        assertEquals(24, bands.size)
        assertEquals(EqConstants.FREQUENCIES[0], bands[0].frequency, 0.001)
        assertEquals(EqConstants.FREQUENCIES[23], bands[23].frequency, 0.001)
        // gain stored directly in dB (NOT divided by 50)
        assertEquals(-6.0, bands[0].gain, 0.001)
        assertEquals(17.0, bands[23].gain, 0.001)
        assertEquals(EqConstants.Q, bands[0].q, 0.0001)
    }

    @Test
    fun mapsBandTypesToFilterTypes() {
        val gains = FloatArray(24) { 1f }
        val types = IntArray(24) { 0 }.also { it[0] = 1; it[1] = 2; it[2] = 0 }
        val bands = buildEqBands(gains, types)

        assertEquals(FilterType.LSC, bands[0].filterType)
        assertEquals(FilterType.HSC, bands[1].filterType)
        assertEquals(FilterType.PK, bands[2].filterType)
    }

    @Test
    fun toleratesShortArrays() {
        val bands = buildEqBands(floatArrayOf(3f, 4f), IntArray(0))
        assertEquals(24, bands.size)
        assertEquals(3.0, bands[0].gain, 0.001)
        assertEquals(0.0, bands[5].gain, 0.001) // missing → 0
        assertEquals(FilterType.PK, bands[5].filterType) // missing type → Peak
    }
}
