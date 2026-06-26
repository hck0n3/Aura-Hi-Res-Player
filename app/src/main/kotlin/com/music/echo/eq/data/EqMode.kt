package iad1tya.echo.music.eq.data

/**
 * EQ editing mode.
 *
 * - [GRAPHIC]: the default 24-band ISO 1/3-octave graphic equalizer.
 * - [PARAMETRIC]: 5–8 fully user-defined PEQ bands (free frequency / Q / gain / type).
 *
 * Both curves are persisted independently so switching modes never loses the other.
 */
enum class EqMode { GRAPHIC, PARAMETRIC }
