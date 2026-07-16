package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.ui.screens.equalizer.axion.buildEqBands
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * These assert the EQ as it ACTUALLY is: [EqConstants.BAND_COUNT] bands, one per [EqConstants.FREQUENCIES]
 * entry, with SHELVES on the two edge bands and peaks in between.
 *
 * They previously asserted a 24-band graphic EQ whose filter type came from a `types` array — the design that
 * `feat(eq): 24-band graphic EQ UI` shipped. The EQ was later reworked deliberately (`feat(eq): fidelity pass
 * - Q->octave, edge shelves`) and the tests were never updated, so they failed against correct code. Nothing
 * about the EQ was changed to make them pass; the assertions were corrected to describe it.
 *
 * Everything is derived from [EqConstants] rather than hardcoded, so a future band-count change updates these
 * automatically instead of silently re-rotting them.
 */
class AxionEqProfileTest {

    @Test
    fun buildsOneBandPerFrequencyWithDbGainsDirect() {
        val last = EqConstants.BAND_COUNT - 1
        val gains = FloatArray(EqConstants.BAND_COUNT) { it.toFloat() - 6f }
        val bands = buildEqBands(gains, IntArray(EqConstants.BAND_COUNT))

        assertEquals(EqConstants.BAND_COUNT, bands.size)
        assertEquals(EqConstants.FREQUENCIES[0], bands[0].frequency, 0.001)
        assertEquals(EqConstants.FREQUENCIES[last], bands[last].frequency, 0.001)
        // gain stored directly in dB (NOT divided by 50)
        assertEquals(-6.0, bands[0].gain, 0.001)
        assertEquals(last.toDouble() - 6.0, bands[last].gain, 0.001)
        assertEquals(EqConstants.Q, bands[0].q, 0.0001)
    }

    /**
     * The filter type comes from the band's POSITION, not from the `types` argument: the lowest band is a low
     * shelf, the highest a high shelf, everything between a peak. `types` is accepted but unused — asserting
     * that it drives the type would re-introduce the stale contract.
     */
    @Test
    fun edgeBandsAreShelvesAndTheRestArePeaks() {
        val gains = FloatArray(EqConstants.BAND_COUNT) { 1f }
        // Deliberately non-zero and "wrong": the types array must NOT influence the outcome.
        val types = IntArray(EqConstants.BAND_COUNT) { 2 }
        val bands = buildEqBands(gains, types)

        assertEquals(FilterType.LSC, bands.first().filterType)
        assertEquals(FilterType.HSC, bands.last().filterType)
        bands.subList(1, bands.size - 1).forEach { assertEquals(FilterType.PK, it.filterType) }
    }

    @Test
    fun toleratesShortArrays() {
        val bands = buildEqBands(floatArrayOf(3f, 4f), IntArray(0))

        assertEquals(EqConstants.BAND_COUNT, bands.size)
        assertEquals(3.0, bands[0].gain, 0.001)
        assertEquals(0.0, bands[5].gain, 0.001) // missing → 0
        assertEquals(FilterType.PK, bands[5].filterType) // a middle band is a peak
    }
}
