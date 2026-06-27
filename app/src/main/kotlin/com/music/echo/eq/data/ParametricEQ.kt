package iad1tya.echo.music.eq.data

import kotlinx.serialization.Serializable


@Serializable
data class ParametricEQBand(
    val frequency: Double,                      
    val gain: Double,                           
    val q: Double = 1.41,                       
    val filterType: FilterType = FilterType.PK, 
    val enabled: Boolean = true                 
)


@Serializable
data class ParametricEQ(
    val preamp: Double,
    val bands: List<ParametricEQBand>,
    // Auto-EQ correction bands. These run as their OWN cascaded biquad stage BEFORE the manual [bands]
    // (auto-correction first, then the user's taste — an LTI cascade). Default empty keeps every existing
    // constructor call source-compatible.
    val autoBands: List<ParametricEQBand> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        const val MAX_BANDS = 24
    }
}