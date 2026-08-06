package iad1tya.echo.music.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.StatPeriod
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * # The range selector of Estadísticas, as ONE implementation
 *
 * The classic [StatsScreen] used to derive its chip labels inline: three `generateSequence` blocks
 * (weeks / months / years) plus a hard-coded list for the continuous ranges, and then a *second*
 * copy of the same `when` to name the currently selected range for the Resumen sheet.
 *
 * "Interfaz nueva" redraws that selector, and a redrawn selector that carries its own copy of these
 * sequences is exactly how the two skins end up disagreeing about which window the numbers below
 * them describe. So the derivation moved here, unchanged, and BOTH screens call it.
 *
 * What is deliberately NOT here: the range → timestamp conversion. That is
 * [iad1tya.echo.music.constants.statToPeriod], it already had one home, and the chip's `first` is
 * precisely the index that function takes — which is why [statsPeriodLabel] can look a range up by
 * that index alone, for all four modes.
 */

/**
 * The chips offered for [option], newest first.
 *
 * `first` is the index handed to `StatsViewModel.indexChips` (and from there to `statToPeriod`);
 * `second` is the label. For [OptionStats.CONTINUOUS] the index is a [StatPeriod] ordinal, for the
 * other three it is the position in the sequence — in both cases `first` IS the stored index, so no
 * caller needs to know which mode it is looking at.
 *
 * Returns empty when there is no listening history yet ([firstEventTimestamp] `null`): with no first
 * event there is no range to walk back to, exactly as before.
 */
@Composable
fun statsPeriodChips(
    option: OptionStats,
    currentDate: LocalDateTime,
    firstEventTimestamp: LocalDateTime?,
): List<Pair<Int, String>> = when (option) {
    // `stringResource` / `pluralStringResource` are @ReadOnlyComposable — they emit no group, so
    // calling them inside a branch does not make the composable call order depend on `option`.
    OptionStats.CONTINUOUS -> listOf(
        StatPeriod.WEEK_1.ordinal to pluralStringResource(R.plurals.n_week, 1, 1),
        StatPeriod.MONTH_1.ordinal to pluralStringResource(R.plurals.n_month, 1, 1),
        StatPeriod.MONTH_3.ordinal to pluralStringResource(R.plurals.n_month, 3, 3),
        StatPeriod.MONTH_6.ordinal to pluralStringResource(R.plurals.n_month, 6, 6),
        StatPeriod.YEAR_1.ordinal to pluralStringResource(R.plurals.n_year, 1, 1),
        StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
    )

    OptionStats.WEEKS -> weeklyStatsChips(currentDate, firstEventTimestamp)
    OptionStats.MONTHS -> monthlyStatsChips(currentDate, firstEventTimestamp)
    OptionStats.YEARS -> yearlyStatsChips(currentDate, firstEventTimestamp)
}

/**
 * The label of the range currently selected, for the Resumen de actividad sheet.
 *
 * Empty when the index names no chip (a mode with no history yet), which is what the classic sheet
 * showed too.
 */
fun statsPeriodLabel(chips: List<Pair<Int, String>>, index: Int): String =
    chips.firstOrNull { it.first == index }?.second.orEmpty()

// ── The three sequences, moved verbatim ───────────────────────────────────────────────────────────

private fun weeklyStatsChips(
    currentDate: LocalDateTime,
    firstEventTimestamp: LocalDateTime?,
): List<Pair<Int, String>> {
    if (firstEventTimestamp == null) return emptyList()
    return generateSequence(currentDate) { it.minusWeeks(1) }
        .takeWhile { it.isAfter(firstEventTimestamp.minusWeeks(1)) }
        .mapIndexed { index, date ->
            val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
            val formatter = DateTimeFormatter.ofPattern("dd MMM")

            val startDateFormatted = formatter.format(date)
            val endDateFormatted = formatter.format(endDate)

            val startMonth = date.month
            val endMonth = endDate.month
            val startYear = date.year
            val endYear = endDate.year

            val text = when {
                startYear != currentDate.year ->
                    "$startDateFormatted, $startYear - $endDateFormatted, $endYear"

                startMonth != endMonth -> "$startDateFormatted - $endDateFormatted"
                else -> "${date.dayOfMonth} - $endDateFormatted"
            }
            Pair(index, text)
        }.toList()
}

private fun monthlyStatsChips(
    currentDate: LocalDateTime,
    firstEventTimestamp: LocalDateTime?,
): List<Pair<Int, String>> {
    if (firstEventTimestamp == null) return emptyList()
    return generateSequence(
        currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1),
    ) { it.minusMonths(1) }
        .takeWhile { it.isAfter(firstEventTimestamp.withDayOfMonth(1)) }
        .mapIndexed { index, date ->
            val formatter = DateTimeFormatter.ofPattern("MMM")
            val formattedDate = formatter.format(date)
            val text = if (date.year != currentDate.year) {
                "$formattedDate ${date.year}"
            } else {
                formattedDate
            }
            Pair(index, text)
        }.toList()
}

private fun yearlyStatsChips(
    currentDate: LocalDateTime,
    firstEventTimestamp: LocalDateTime?,
): List<Pair<Int, String>> {
    if (firstEventTimestamp == null) return emptyList()
    return generateSequence(
        currentDate.plusYears(1).withDayOfYear(1).minusDays(1),
    ) { it.minusYears(1) }
        .takeWhile { it.isAfter(firstEventTimestamp) }
        .mapIndexed { index, date -> Pair(index, "${date.year}") }
        .toList()
}
