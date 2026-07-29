/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package iad1tya.echo.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import iad1tya.echo.music.MainActivity
import iad1tya.echo.music.R
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val imageLoader
        get() = context.imageLoader

    /**
     * True when at least one playlist widget is placed. Lets the 1 Hz media updater skip this widget's work
     * entirely — it is the most expensive one: a full RemoteViews grid rebuild, and historically five Room
     * queries per refresh including an unindexed most-played scan (#55 battery/heat). The queries are now
     * served from [cachedQuickPicks]; the presence gate still saves the binder/RemoteViews work.
     */
    fun hasAnyWidget(): Boolean = runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PlaylistWidgetReceiver::class.java))
            .isNotEmpty()
    }.getOrDefault(true)

    @Volatile
    private var cachedArtworkUri: String? = null
    @Volatile
    private var cachedAlbumArt: Bitmap? = null
    @Volatile
    private var cachedRoundedAlbumArt: Bitmap? = null
    @Volatile
    private var cachedRoundedSource: Bitmap? = null
    private val fallbackArtworkCache = ConcurrentHashMap<FallbackArtworkKey, Bitmap>()
    private val roundedAppIconCache = ConcurrentHashMap<Int, Bitmap>()

    @Volatile
    private var lastWidgetState = WidgetState(
        title = context.getString(R.string.not_playing),
        artist = context.getString(R.string.choose_something_below),
        artworkUri = null,
        isPlaying = false,
        isLiked = false,
        duration = 0,
        currentPosition = 0,
    )

    /**
     * Quick-picks list served to every render, maintained by [observeQuickPickChanges] (Room flows,
     * ~500 ms debounce). The 1 Hz ticker used to bypass this and issue FIVE fresh Room queries per
     * second via [updateWidgets] — including an unindexed most-played aggregate over the whole Event
     * table — ~300 queries/minute of pure heat while music played (#55). Renders now read this cache;
     * queries only run when the underlying data actually changes (flow emission) or on explicit
     * widget events (added/resized), never on the playback tick.
     */
    @Volatile
    private var cachedQuickPicks: List<QuickPick>? = null

    /**
     * Fingerprint of the last FULL render. While it matches the incoming state, the per-second tick
     * only moves the progress bar — everything else on the widget (titles, artwork grid, buttons) is
     * unchanged — so we ship a one-action [AppWidgetManager.partiallyUpdateAppWidget] instead of
     * re-parceling the whole grid (bitmaps included) and forcing the launcher to re-apply it.
     */
    @Volatile
    private var lastRenderedContent: RenderedContent? = null

    // Ticks since the last full render. A periodic full render (~1/min) self-heals the rare cases where
    // the system's cached full RemoteViews was lost (system_server restart) and partial merges would
    // otherwise leave a hollow widget until the next song change.
    private var ticksSinceFullRender = 0

    // Ticks spent showing the fallback icon because the CURRENT song's artwork failed to load. Used to
    // retry the load occasionally (~30 s) instead of on every tick — same policy as EchoMusicWidgetManager.
    private var artworkRetryTicks = 0

    init {
        observeQuickPickChanges()
    }

    suspend fun updateIdleWidgets() {
        updateWidgets(
            title = context.getString(R.string.not_playing),
            artist = context.getString(R.string.choose_something_below),
            artworkUri = null,
            isPlaying = false,
            isLiked = false,
            duration = 0,
            currentPosition = 0,
        )
    }

    suspend fun updateIdleWidget(
        appWidgetId: Int,
        options: Bundle,
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val state = lastWidgetState
        val albumArt = getCachedAlbumArt(state.artworkUri)
        // Explicit widget event (added/resized) — rebuild from the database so a brand-new widget never
        // shows a stale grid, and refresh the cache with the result. Rare and user-initiated, so the
        // query cost is fine here; the 1 Hz tick is the path that must never query.
        val quickPicks = freshQuickPicks()
        val views = createRemoteViews(
            options = options,
            title = state.title,
            artist = state.artist,
            albumArt = albumArt,
            isPlaying = state.isPlaying,
            isLiked = state.isLiked,
            duration = state.duration,
            currentPosition = state.currentPosition,
            quickPicks = quickPicks,
        )
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private suspend fun refreshWidgetsFromLastState() {
        val state = lastWidgetState
        updateWidgets(
            title = state.title,
            artist = state.artist,
            artworkUri = state.artworkUri,
            isPlaying = state.isPlaying,
            isLiked = state.isLiked,
            duration = state.duration,
            currentPosition = state.currentPosition,
        )
    }

    suspend fun updateWidgets(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
    ) {
        lastWidgetState = WidgetState(
            title = title,
            artist = artist,
            artworkUri = artworkUri,
            isPlaying = isPlaying,
            isLiked = isLiked,
            duration = duration,
            currentPosition = currentPosition,
        )

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, PlaylistWidgetReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isEmpty()) return

        // Cached snapshot — NO Room queries on this path (see cachedQuickPicks). The only query left
        // here is the one-time cold-start fill before the observe flow's first emission.
        val quickPicks = currentQuickPicks()
        val content = RenderedContent(
            title = title,
            artist = artist,
            artworkUri = artworkUri,
            isPlaying = isPlaying,
            isLiked = isLiked,
            quickPicks = quickPicks,
            widgetIds = widgetIds.sorted(),
        )

        val needsFullRender = content != lastRenderedContent ||
            ticksSinceFullRender >= FULL_RENDER_MAX_TICKS ||
            shouldRetryFailedArtwork(artworkUri)

        if (!needsFullRender) {
            // Light path — the steady-state 1 Hz tick. Between ticks only the playback position moved,
            // so ship a single setImageLevel action; the system merges it into its cached full
            // RemoteViews and the launcher re-applies in place instead of re-inflating the grid.
            ticksSinceFullRender++
            val partialViews = RemoteViews(context.packageName, R.layout.widget_playlist)
            partialViews.setInt(
                R.id.widget_playlist_progress_fill,
                "setImageLevel",
                progressLevel(duration, currentPosition),
            )
            widgetIds.forEach { widgetId ->
                appWidgetManager.partiallyUpdateAppWidget(widgetId, partialViews)
            }
            return
        }

        // Full path — content actually changed (song/like/play-state/quick-picks/widget set), the
        // periodic self-heal fired, or a failed artwork load is due for a retry.
        val albumArt = getCachedAlbumArt(artworkUri)

        widgetIds.forEach { widgetId ->
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val views = createRemoteViews(
                options = options,
                title = title,
                artist = artist,
                albumArt = albumArt,
                isPlaying = isPlaying,
                isLiked = isLiked,
                duration = duration,
                currentPosition = currentPosition,
                quickPicks = quickPicks,
            )
            appWidgetManager.updateAppWidget(widgetId, views)
        }
        lastRenderedContent = content
        ticksSinceFullRender = 0
    }

    /**
     * True (about once every [ARTWORK_RETRY_TICKS] calls) while the requested artwork is NOT the one in
     * the cache — i.e. the last load failed and the widget is stuck on the fallback icon. Forces a full
     * render so [getCachedAlbumArt] re-attempts the load; without this, the partial path would pin the
     * fallback icon for the rest of the song after one network blip at track start.
     */
    private fun shouldRetryFailedArtwork(artworkUri: String?): Boolean {
        if (artworkUri == null || artworkUri == cachedArtworkUri) {
            artworkRetryTicks = 0
            return false
        }
        if (++artworkRetryTicks >= ARTWORK_RETRY_TICKS) {
            artworkRetryTicks = 0
            return true
        }
        return false
    }

    private fun progressLevel(duration: Long, currentPosition: Long): Int =
        if (duration > 0) {
            ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt().coerceIn(0, 10000)
        } else {
            0
        }

    @OptIn(FlowPreview::class)
    private fun observeQuickPickChanges() {
        quickPickSnapshots()
            .distinctUntilChanged()
            .debounce(500L)
            .onEach { snapshot ->
                // Refresh the cache FIRST so the render below (and every 1 Hz tick after it) sees the
                // new content; the tick's fingerprint check then promotes exactly one full render.
                cachedQuickPicks = buildQuickPicks(snapshot)
                refreshWidgetsFromLastState()
            }
            .launchIn(applicationScope)
    }

    /** The five Room flows that feed the quick-picks grid, combined into one immutable snapshot. */
    private fun quickPickSnapshots(): Flow<QuickPickSnapshot> {
        return combine(
            database.speedDialDao.getAll().map { items ->
                items.map { item ->
                    SpeedDialSnapshot(
                        type = item.type,
                        id = item.id,
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                    )
                }
            },
            database.playlistsByCreateDateAsc().map { playlists ->
                playlists.map { playlist ->
                    PlaylistSnapshot(
                        id = playlist.playlist.id,
                        browseId = playlist.playlist.browseId,
                        name = playlist.playlist.name,
                        thumbnailUrl = playlist.thumbnails.firstOrNull(),
                        isLocal = playlist.playlist.isLocal,
                    )
                }
            },
            database.likedSongsByCreateDateAsc().map { songs ->
                songs.map { song ->
                    SongSnapshot(
                        id = song.id,
                        thumbnailUrl = song.thumbnailUrl,
                    )
                }
            },
            database.downloadedSongsByCreateDateAsc().map { songs ->
                songs.map { song ->
                    SongSnapshot(
                        id = song.id,
                        thumbnailUrl = song.thumbnailUrl,
                    )
                }
            },
            database.mostPlayedSongs(0L, limit = 50).map { songs ->
                songs.map { song ->
                    SongSnapshot(
                        id = song.id,
                        thumbnailUrl = song.thumbnailUrl,
                    )
                }
            },
        ) { speedDial, playlists, likedSongs, downloadedSongs, topSongs ->
            QuickPickSnapshot(
                speedDial = speedDial,
                playlists = playlists,
                likedSongs = likedSongs,
                downloadedSongs = downloadedSongs,
                topSongs = topSongs,
            )
        }
    }

    private suspend fun createRemoteViews(
        options: Bundle,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long,
        quickPicks: List<QuickPick>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playlist)

        views.setTextViewText(R.id.widget_playlist_song_title, title)
        views.setTextViewText(R.id.widget_playlist_artist_name, artist)

        if (albumArt != null) {
            views.setImageViewBitmap(R.id.widget_playlist_album_art, getRoundedAlbumArt(albumArt))
        } else {
            views.setImageViewBitmap(R.id.widget_playlist_album_art, getRoundedAppIcon(36f))
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_playlist_play_pause, playPauseIcon)
        views.setImageViewResource(
            R.id.widget_playlist_like_button,
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav,
        )

        views.setInt(
            R.id.widget_playlist_progress_fill,
            "setImageLevel",
            progressLevel(duration, currentPosition),
        )

        views.setOnClickPendingIntent(R.id.widget_playlist_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(
            R.id.widget_playlist_prev_container,
            getMusicWidgetIntent(MusicWidgetReceiver.ACTION_PREVIOUS, 501),
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_play_pause_container,
            getMusicWidgetIntent(MusicWidgetReceiver.ACTION_PLAY_PAUSE, 502),
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_next_container,
            getMusicWidgetIntent(MusicWidgetReceiver.ACTION_NEXT, 503),
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_like_button,
            getMusicWidgetIntent(MusicWidgetReceiver.ACTION_LIKE, 504),
        )

        bindQuickPicks(views, options, quickPicks)
        return views
    }

    private suspend fun bindQuickPicks(
        views: RemoteViews,
        options: Bundle,
        quickPicks: List<QuickPick>,
    ) {
        val maxItems = maxQuickPicksForSize(options)
        val displayCount = balancedDisplayCount(quickPicks.size, maxItems)
        val visiblePicks = quickPicks.take(displayCount)
        val visibleSlots = (0 until displayCount).toSet()
        val reservedEmptySlots = reservedEmptySlotsFor(displayCount)

        views.setViewVisibility(
            R.id.widget_playlist_quick_picks_section,
            if (displayCount > 0) View.VISIBLE else View.GONE,
        )
        views.setViewVisibility(
            R.id.widget_playlist_row_2,
            if (displayCount > 4) View.VISIBLE else View.GONE,
        )

        cardSlots.forEachIndexed { index, slot ->
            val visibility = when {
                index in visibleSlots -> View.VISIBLE
                index in reservedEmptySlots -> View.INVISIBLE
                else -> View.GONE
            }
            views.setViewVisibility(slot.containerId, visibility)
        }

        visiblePicks.forEachIndexed { index, item ->
            val slot = cardSlots[index]
            views.setTextViewText(slot.titleId, item.title)
            views.setImageViewResource(slot.playIconId, R.drawable.ic_widget_play_low)
            views.setImageViewBitmap(
                slot.artworkId,
                item.thumbnailUrl
                    ?.let { loadBitmap(it, 180) }
                    ?.let { getRoundedCornerBitmap(it, 30f) }
                    ?: getFallbackArtwork(item, 30f),
            )
            views.setOnClickPendingIntent(slot.containerId, getOpenTargetIntent(item))
            views.setOnClickPendingIntent(slot.playContainerId, getPlayTargetIntent(item))
        }
    }
    // Pick the widget size bucket for the shortcut grid
    private fun maxQuickPicksForSize(options: Bundle): Int {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        return when {
            minWidth < 210 -> 0
            minHeight < 330 -> 4
            minWidth >= 360 -> 8
            else -> 4
        }
    }
    // The widget intentionally avoids an incomplete second row:
    // show up to 4 playlists in one row, collapse 5-7 to 4, and only use two rows when 8 are available
    private fun balancedDisplayCount(available: Int, maxItems: Int): Int {
        val capped = minOf(available, maxItems, 8)
        return when {
            capped >= 8 -> 8
            capped >= 4 -> 4
            else -> capped
        }
    }


    private fun reservedEmptySlotsFor(displayCount: Int): Set<Int> = when (displayCount) {
        1 -> setOf(1, 2, 3)
        2 -> setOf(2, 3)
        3 -> setOf(3)
        else -> emptySet()
    }

    /** Cache-first read used by the render path; queries only before the flow's first emission. */
    private suspend fun currentQuickPicks(): List<QuickPick> =
        cachedQuickPicks ?: freshQuickPicks()

    /** One-shot database rebuild (five Room queries) that also refreshes the cache. */
    private suspend fun freshQuickPicks(): List<QuickPick> =
        withContext(Dispatchers.IO) { buildQuickPicks(quickPickSnapshots().first()) }
            .also { cachedQuickPicks = it }

    /** Pure transformation — no database access; every render-path caller works from a snapshot. */
    private fun buildQuickPicks(snapshot: QuickPickSnapshot): List<QuickPick> {
        val result = mutableListOf<QuickPick>()
        val seen = mutableSetOf<String>()

        fun add(item: QuickPick) {
            if (seen.add(item.key)) result += item
        }

        snapshot.speedDial
            .filter { it.type == "PLAYLIST" || it.type == "LOCAL_PLAYLIST" }
            .forEach { item ->
                if (item.type == "LOCAL_PLAYLIST") {
                    add(
                        QuickPick(
                            key = "local:${item.id}",
                            targetType = PlaylistWidgetReceiver.TARGET_TYPE_LOCAL,
                            targetId = item.id,
                            title = item.title,
                            thumbnailUrl = item.thumbnailUrl,
                            fallbackIconRes = R.drawable.playlist_play,
                        ),
                    )
                } else {
                    val saved = snapshot.playlists.firstOrNull {
                        it.browseId == item.id || it.id == item.id
                    }
                    if (saved != null) {
                        add(saved.toQuickPick())
                    } else {
                        add(
                            QuickPick(
                                key = "online:${item.id}",
                                targetType = PlaylistWidgetReceiver.TARGET_TYPE_ONLINE,
                                targetId = item.id,
                                title = item.title,
                                thumbnailUrl = item.thumbnailUrl,
                                fallbackIconRes = R.drawable.playlist_play,
                            ),
                        )
                    }
                }
            }

        snapshot.playlists
            .filter { it.browseId == null || it.isLocal }
            .forEach { add(it.toQuickPick()) }

        snapshot.playlists
            .filter { it.browseId != null && !it.isLocal }
            .forEach { add(it.toQuickPick()) }

        if (snapshot.likedSongs.isNotEmpty()) {
            add(
                QuickPick(
                    key = "auto:liked",
                    targetType = PlaylistWidgetReceiver.TARGET_TYPE_LIKED,
                    targetId = PlaylistWidgetReceiver.TARGET_TYPE_LIKED,
                    title = context.getString(R.string.liked_songs),
                    thumbnailUrl = snapshot.likedSongs.firstOrNull()?.thumbnailUrl,
                    fallbackIconRes = R.drawable.ic_widget_heart_nav,
                ),
            )
        }

        if (snapshot.downloadedSongs.isNotEmpty()) {
            add(
                QuickPick(
                    key = "auto:downloaded",
                    targetType = PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED,
                    targetId = PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED,
                    title = context.getString(R.string.downloaded_songs),
                    thumbnailUrl = snapshot.downloadedSongs.firstOrNull()?.thumbnailUrl,
                    fallbackIconRes = R.drawable.cached,
                ),
            )
        }

        if (snapshot.topSongs.isNotEmpty()) {
            add(
                QuickPick(
                    key = "auto:top50",
                    targetType = PlaylistWidgetReceiver.TARGET_TYPE_TOP,
                    targetId = "50",
                    title = context.getString(R.string.my_top),
                    thumbnailUrl = snapshot.topSongs.firstOrNull()?.thumbnailUrl,
                    fallbackIconRes = R.drawable.trending_up,
                ),
            )
        }

        return result
    }

    private fun PlaylistSnapshot.toQuickPick(): QuickPick {
        val isOnline = browseId != null && !isLocal
        return QuickPick(
            key = if (isOnline) "online:$browseId" else "local:$id",
            targetType = if (isOnline) {
                PlaylistWidgetReceiver.TARGET_TYPE_ONLINE
            } else {
                PlaylistWidgetReceiver.TARGET_TYPE_LOCAL
            },
            targetId = browseId ?: id,
            title = name,
            thumbnailUrl = thumbnailUrl,
            fallbackIconRes = R.drawable.playlist_play,
        )
    }

    private suspend fun getCachedAlbumArt(artworkUri: String?): Bitmap? {
        if (artworkUri == null) {
            cachedArtworkUri = null
            cachedAlbumArt = null
            cachedRoundedAlbumArt = null
            cachedRoundedSource = null
            return null
        }

        if (artworkUri != cachedArtworkUri) {
            val loaded = loadBitmap(artworkUri, 240)
            cachedAlbumArt = loaded
            cachedRoundedAlbumArt = null
            cachedRoundedSource = null
            cachedArtworkUri = artworkUri.takeIf { loaded != null }
        }

        return cachedAlbumArt
    }

    private fun getRoundedAlbumArt(albumArt: Bitmap): Bitmap {
        val cached = cachedRoundedAlbumArt
        if (cached != null && cachedRoundedSource === albumArt) return cached

        return getRoundedCornerBitmap(albumArt, 36f).also {
            cachedRoundedSource = albumArt
            cachedRoundedAlbumArt = it
        }
    }

    private suspend fun loadBitmap(uri: String, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(size, size)
                .allowHardware(false)
                .crossfade(false)
                .build()
            imageLoader.execute(request).image?.toBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        if (squareBitmap != bitmap) squareBitmap.recycle()
        return output
    }

    private fun getFallbackArtwork(item: QuickPick, cornerRadius: Float): Bitmap {
        val cacheKey = FallbackArtworkKey(
            targetType = item.targetType,
            fallbackIconRes = item.fallbackIconRes,
            cornerRadius = cornerRadius.toInt(),
        )

        fallbackArtworkCache[cacheKey]?.let { return it }

        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint().apply {
            isAntiAlias = true
            color = when (item.targetType) {
                PlaylistWidgetReceiver.TARGET_TYPE_LIKED -> Color.rgb(198, 40, 86)
                PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED -> Color.rgb(38, 128, 118)
                PlaylistWidgetReceiver.TARGET_TYPE_TOP -> Color.rgb(122, 86, 178)
                else -> Color.rgb(66, 72, 86)
            }
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, background)

        val icon = context.getDrawable(item.fallbackIconRes)?.mutate()
        icon?.setTint(Color.WHITE)
        val iconSize = 128
        val iconOffset = (size - iconSize) / 2
        icon?.setBounds(iconOffset, iconOffset, iconOffset + iconSize, iconOffset + iconSize)
        icon?.draw(canvas)

        fallbackArtworkCache[cacheKey] = bitmap
        return bitmap
    }

    private fun getRoundedAppIcon(cornerRadius: Float): Bitmap {
        val cacheKey = cornerRadius.toInt()
        roundedAppIconCache[cacheKey]?.let { return it }

        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)

        return getRoundedCornerBitmap(bitmap, cornerRadius).also {
            roundedAppIconCache[cacheKey] = it
        }
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            500,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getOpenTargetIntent(item: QuickPick): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "iad1tya.echo.music.action.OPEN_WIDGET_TARGET"
            putExtra("extra_widget_target_type", item.targetType)
            putExtra("extra_widget_target_id", item.targetId)
        }
        return PendingIntent.getActivity(
            context,
            item.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getPlayTargetIntent(item: QuickPick): PendingIntent {
        val intent = Intent(context, PlaylistWidgetReceiver::class.java).apply {
            action = PlaylistWidgetReceiver.ACTION_PLAY_TARGET
            putExtra(PlaylistWidgetReceiver.EXTRA_TARGET_TYPE, item.targetType)
            putExtra(PlaylistWidgetReceiver.EXTRA_TARGET_ID, item.targetId)
            putExtra(PlaylistWidgetReceiver.EXTRA_TARGET_TITLE, item.title)
        }
        return PendingIntent.getBroadcast(
            context,
            item.key.hashCode() xor 0x3522,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getMusicWidgetIntent(
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class WidgetState(
        val title: String,
        val artist: String,
        val artworkUri: String?,
        val isPlaying: Boolean,
        val isLiked: Boolean,
        val duration: Long,
        val currentPosition: Long,
    )

    /**
     * Everything a full render depends on EXCEPT playback position/duration. While two consecutive
     * updates compare equal here, only the progress bar can differ, so the partial-update path is safe.
     * QuickPick is a plain data class, so list comparison is structural. Widget ids are included so
     * adding a widget mid-session forces a full render for it on the next tick.
     */
    private data class RenderedContent(
        val title: String,
        val artist: String,
        val artworkUri: String?,
        val isPlaying: Boolean,
        val isLiked: Boolean,
        val quickPicks: List<QuickPick>,
        val widgetIds: List<Int>,
    )

    private data class QuickPickSnapshot(
        val speedDial: List<SpeedDialSnapshot>,
        val playlists: List<PlaylistSnapshot>,
        val likedSongs: List<SongSnapshot>,
        val downloadedSongs: List<SongSnapshot>,
        val topSongs: List<SongSnapshot>,
    )

    private data class SpeedDialSnapshot(
        val type: String,
        val id: String,
        val title: String,
        val thumbnailUrl: String?,
    )

    private data class PlaylistSnapshot(
        val id: String,
        val browseId: String?,
        val name: String,
        val thumbnailUrl: String?,
        val isLocal: Boolean,
    )

    private data class SongSnapshot(
        val id: String,
        val thumbnailUrl: String?,
    )

    private data class QuickPick(
        val key: String,
        val targetType: String,
        val targetId: String,
        val title: String,
        val thumbnailUrl: String?,
        val fallbackIconRes: Int,
    )

    private data class CardSlot(
        val containerId: Int,
        val artworkId: Int,
        val playContainerId: Int,
        val playIconId: Int,
        val titleId: Int,
    )

    private data class FallbackArtworkKey(
        val targetType: String,
        val fallbackIconRes: Int,
        val cornerRadius: Int,
    )

    private companion object {
        /** ~30 s between retries of a FAILED artwork load (matches EchoMusicWidgetManager's policy). */
        const val ARTWORK_RETRY_TICKS = 30

        /** Periodic full-render self-heal: at most one full grid re-parcel per minute at steady state. */
        const val FULL_RENDER_MAX_TICKS = 60
    }

    private val cardSlots = listOf(
        CardSlot(R.id.widget_playlist_card_1, R.id.widget_playlist_card_1_art, R.id.widget_playlist_card_1_play_container, R.id.widget_playlist_card_1_play, R.id.widget_playlist_card_1_title),
        CardSlot(R.id.widget_playlist_card_2, R.id.widget_playlist_card_2_art, R.id.widget_playlist_card_2_play_container, R.id.widget_playlist_card_2_play, R.id.widget_playlist_card_2_title),
        CardSlot(R.id.widget_playlist_card_3, R.id.widget_playlist_card_3_art, R.id.widget_playlist_card_3_play_container, R.id.widget_playlist_card_3_play, R.id.widget_playlist_card_3_title),
        CardSlot(R.id.widget_playlist_card_4, R.id.widget_playlist_card_4_art, R.id.widget_playlist_card_4_play_container, R.id.widget_playlist_card_4_play, R.id.widget_playlist_card_4_title),
        CardSlot(R.id.widget_playlist_card_5, R.id.widget_playlist_card_5_art, R.id.widget_playlist_card_5_play_container, R.id.widget_playlist_card_5_play, R.id.widget_playlist_card_5_title),
        CardSlot(R.id.widget_playlist_card_6, R.id.widget_playlist_card_6_art, R.id.widget_playlist_card_6_play_container, R.id.widget_playlist_card_6_play, R.id.widget_playlist_card_6_title),
        CardSlot(R.id.widget_playlist_card_7, R.id.widget_playlist_card_7_art, R.id.widget_playlist_card_7_play_container, R.id.widget_playlist_card_7_play, R.id.widget_playlist_card_7_title),
        CardSlot(R.id.widget_playlist_card_8, R.id.widget_playlist_card_8_art, R.id.widget_playlist_card_8_play_container, R.id.widget_playlist_card_8_play, R.id.widget_playlist_card_8_title),
    )
}
