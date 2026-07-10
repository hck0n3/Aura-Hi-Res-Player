package iad1tya.echo.music.eq.data

/**
 * EQ editing mode.
 *
 * - [GRAPHIC]: the default 10-band (EqConstants.BAND_COUNT) ISO octave graphic equalizer.
 * - [PARAMETRIC]: 5–8 fully user-defined PEQ bands (free frequency / Q / gain / type).
 *
 * Both curves are persisted independently so switching modes never loses the other.
 */
enum class EqMode { GRAPHIC, PARAMETRIC }
