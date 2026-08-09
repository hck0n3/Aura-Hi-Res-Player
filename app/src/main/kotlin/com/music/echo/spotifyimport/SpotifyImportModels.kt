/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.spotifyimport

import androidx.compose.runtime.Immutable

@Immutable
enum class SpotifyImportSourceType {
    PLAYLIST,
    LIKED_SONGS,
    ARTISTS,
    SAVED_ALBUMS,
}

@Immutable
enum class SpotifyImportFailureReason {
    LOW_SCORE,
    NO_CANDIDATES,
    RATE_LIMIT,
    UNAVAILABLE,
    SEARCH_ERROR,
    SOURCE_ERROR,
}

@Immutable
data class SpotifyImportFailureUi(
    val title: String,
    val artists: String,
    val album: String,
    val spotifyId: String,
    val reason: SpotifyImportFailureReason,
)

@Immutable
data class SpotifyImportSourceUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val trackCount: Int?,
    val type: SpotifyImportSourceType,
)

@Immutable
data class SpotifyImportProgressUi(
    val sourceTitle: String,
    val completedSources: Int,
    val totalSources: Int,
    val matchedTracks: Int,
    val totalTracks: Int,
    val percent: Int,
)

@Immutable
data class SpotifyImportSourceSummaryUi(
    val title: String,
    val totalTracks: Int,
    val importedTracks: Int,
    val failedTracks: Int,
    // The real count Spotify reports for this source (playlist tracks / liked songs /
    // followed artists / saved albums), captured in loadSources. [totalTracks] is what we
    // actually fetched and attempted to match; comparing the two surfaces any shortfall
    // (e.g. local-file/podcast tracks that can't be matched). Nullable: a playlist opened
    // by link may not carry a track total. Default keeps existing call sites source-compatible.
    val accountTotalTracks: Int? = null,
    val failures: List<SpotifyImportFailureUi> = emptyList(),
)

@Immutable
data class SpotifyImportSummaryUi(
    val sources: List<SpotifyImportSourceSummaryUi>,
) {
    val sourceCount: Int
        get() = sources.size

    val totalTracks: Int
        get() = sources.sumOf { it.totalTracks }

    val importedTracks: Int
        get() = sources.sumOf { it.importedTracks }

    val failedTracks: Int
        get() = sources.sumOf { it.failedTracks }

    val allFailures: List<SpotifyImportFailureUi>
        get() = sources.flatMap { it.failures }

    /**
     * True when every fetched item matched and there is no account-level shortfall.
     */
    val isComplete: Boolean
        get() = allFailures.isEmpty() &&
            failedTracks == 0 &&
            importedTracks >= accountTotalTracks

    /**
     * The real Spotify account total across all sources (falling back to the fetched
     * count when a source didn't report one). Lets the UI show "imported X of Y" against
     * the true library size and flag a shortfall, instead of only the fetched count.
     */
    val accountTotalTracks: Int
        get() = sources.sumOf { it.accountTotalTracks ?: it.totalTracks }

    /** After a retry pass: drop recovered failures and bump imported counts per source. */
    fun withRetryResult(
        remainingFailures: List<SpotifyImportFailureUi>,
    ): SpotifyImportSummaryUi {
        val remainingKeys = remainingFailures.mapTo(HashSet()) { it.failureKey() }
        return copy(
            sources = sources.map { src ->
                val stillFailed = src.failures.filter { it.failureKey() in remainingKeys }
                val recovered = src.failures.size - stillFailed.size
                src.copy(
                    importedTracks = src.importedTracks + recovered,
                    failedTracks = stillFailed.size,
                    failures = stillFailed,
                )
            },
        )
    }
}

@Immutable
data class SpotifyImportUiState(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
    val isLoading: Boolean = false,
    val sources: List<SpotifyImportSourceUi> = emptyList(),
    val selectedSourceIds: Set<String> = emptySet(),
    val progress: SpotifyImportProgressUi? = null,
    val summary: SpotifyImportSummaryUi? = null,
    val errorMessage: String? = null,
) {
    val hasSources: Boolean
        get() = sources.isNotEmpty()

    val canImport: Boolean
        get() = selectedSourceIds.isNotEmpty() && progress == null && !isLoading
}

internal fun SpotifyImportFailureUi.failureKey(): String = "$spotifyId\u0000$title\u0000$artists"
