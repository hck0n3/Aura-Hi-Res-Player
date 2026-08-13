package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.LocalItem
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.Song

/**
 * Content-type identity — Apple Music + YouTube Music hybrid for Aura exploration.
 *
 * Apple: large album squares (~156 dp), circular artists, song lists, roomy section titles.
 * YTM: hero 16:9 videos (~268 dp), explicit video language, soft playlist cards.
 * One [AuraContentKind] drives every shelf / row / poster so sizes never drift screen-to-screen.
 */
enum class AuraContentKind {
    Song,
    Video,
    Album,
    Ep,
    Single,
    Playlist,
    Artist,
    Podcast,
}

data class AuraTypeVisual(
    val kind: AuraContentKind,
    val label: String,
    val icon: ImageVector,
    val shape: Shape,
    val ratio: Float,
    val shelfWidth: Dp,
    val rowWidth: Dp,
    /** Show text label on the cover badge (YTM videos); others stay icon-only (Apple-cleaner). */
    val badgeShowsLabel: Boolean = false,
)

fun auraLooksLikeSingle(item: AlbumItem): Boolean {
    val t = item.title.trim()
    val d = item.description.orEmpty()
    return t.contains(Regex("""(?i)(^|[^\w])Single([^\w]|$)""")) ||
        t.endsWith(" - Single", ignoreCase = true) ||
        d.contains(Regex("""(?i)\bSingle\b"""))
}

fun auraContentKind(item: YTItem): AuraContentKind = when (item) {
    is SongItem -> if (item.isVideoSong) AuraContentKind.Video else AuraContentKind.Song
    is AlbumItem -> when {
        auraLooksLikeSingle(item) -> AuraContentKind.Single
        auraLooksLikeEp(item) -> AuraContentKind.Ep
        else -> AuraContentKind.Album
    }
    is PlaylistItem -> AuraContentKind.Playlist
    is ArtistItem -> AuraContentKind.Artist
}

fun auraContentKind(item: LocalItem): AuraContentKind = when (item) {
    is Song -> if (item.song.isVideo) AuraContentKind.Video else AuraContentKind.Song
    is Album -> AuraContentKind.Album
    is Artist -> AuraContentKind.Artist
    is Playlist -> AuraContentKind.Playlist
}

fun auraTypeLabel(kind: AuraContentKind): String = when (kind) {
    AuraContentKind.Song -> "Canción"
    AuraContentKind.Video -> "Vídeo"
    AuraContentKind.Album -> "Álbum"
    AuraContentKind.Ep -> "EP"
    AuraContentKind.Single -> "Single"
    AuraContentKind.Playlist -> "Playlist"
    AuraContentKind.Artist -> "Artista"
    AuraContentKind.Podcast -> "Podcast"
}

fun auraTypeIcon(kind: AuraContentKind): ImageVector = when (kind) {
    AuraContentKind.Song -> AuraIcons.Equalizer
    AuraContentKind.Video -> AuraIcons.Video
    AuraContentKind.Album -> AuraIcons.Album
    AuraContentKind.Ep -> AuraIcons.Album
    AuraContentKind.Single -> AuraIcons.Equalizer
    AuraContentKind.Playlist -> AuraIcons.Queue
    AuraContentKind.Artist -> AuraIcons.Artist
    AuraContentKind.Podcast -> AuraIcons.Radio
}

fun auraTypeVisual(kind: AuraContentKind): AuraTypeVisual {
    val label = auraTypeLabel(kind)
    val icon = auraTypeIcon(kind)
    return when (kind) {
        // YTM hero landscape — wide enough that one card dominates the shelf.
        AuraContentKind.Video -> AuraTypeVisual(
            kind, label, icon, AuraShapes.Card, 16f / 9f,
            shelfWidth = 268.dp, rowWidth = 96.dp, badgeShowsLabel = true,
        )
        // Apple Music track tiles stay compact.
        AuraContentKind.Song -> AuraTypeVisual(
            kind, label, icon, AuraShapes.Artwork, 1f,
            shelfWidth = 118.dp, rowWidth = 52.dp,
        )
        // Apple album — the editorial size of a modern Music.app shelf.
        AuraContentKind.Album -> AuraTypeVisual(
            kind, label, icon, AuraShapes.Artwork, 1f,
            shelfWidth = 156.dp, rowWidth = 56.dp,
        )
        AuraContentKind.Ep -> AuraTypeVisual(
            kind, label, icon, AuraShapes.Artwork, 1f,
            shelfWidth = 156.dp, rowWidth = 56.dp, badgeShowsLabel = true,
        )
        AuraContentKind.Single -> AuraTypeVisual(
            kind, label, icon, AuraShapes.Artwork, 1f,
            shelfWidth = 156.dp, rowWidth = 56.dp, badgeShowsLabel = true,
        )
        AuraContentKind.Playlist -> AuraTypeVisual(
            kind, label, icon, AuraShapes.Artwork, 1f,
            shelfWidth = 156.dp, rowWidth = 56.dp,
        )
        AuraContentKind.Artist -> AuraTypeVisual(
            kind, label, icon, CircleShape, 1f,
            shelfWidth = 132.dp, rowWidth = 52.dp,
        )
        AuraContentKind.Podcast -> AuraTypeVisual(
            kind, label, icon, AuraShapes.Card, 1f,
            shelfWidth = 148.dp, rowWidth = 54.dp, badgeShowsLabel = true,
        )
    }
}

fun auraTypeVisual(item: YTItem): AuraTypeVisual = auraTypeVisual(auraContentKind(item))

fun auraTypeVisual(item: LocalItem): AuraTypeVisual = auraTypeVisual(auraContentKind(item))

/** Default album shelf width — use instead of hard-coded 136.dp. */
val AuraAlbumShelfWidth: Dp get() = auraTypeVisual(AuraContentKind.Album).shelfWidth

/**
 * Display order inside a mixed Home shelf — keep like with like so 16:9 videos never
 * sit next to square songs in the same grid cell (that left huge empty gaps).
 */
fun auraKindShelfOrder(kind: AuraContentKind): Int = when (kind) {
    AuraContentKind.Video -> 0
    AuraContentKind.Song -> 1
    AuraContentKind.Single -> 2
    AuraContentKind.Ep -> 3
    AuraContentKind.Album -> 4
    AuraContentKind.Playlist -> 5
    AuraContentKind.Podcast -> 6
    AuraContentKind.Artist -> 7
}

/** Group [items] by Aura kind, sorted for Home shelves. Empty groups omitted. */
fun <T> List<T>.groupedByAuraKind(kindOf: (T) -> AuraContentKind): List<Pair<AuraContentKind, List<T>>> =
    asSequence()
        .groupBy(kindOf)
        .entries
        .sortedBy { auraKindShelfOrder(it.key) }
        .map { it.key to it.value }
        .toList()
