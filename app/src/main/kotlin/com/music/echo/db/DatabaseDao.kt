

package iad1tya.echo.music.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.RoomWarnings
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import iad1tya.echo.music.constants.AlbumSortType
import iad1tya.echo.music.constants.ArtistSongSortType
import iad1tya.echo.music.constants.ArtistSortType
import iad1tya.echo.music.constants.PlaylistSortType
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.AlbumArtistMap
import iad1tya.echo.music.db.entities.AlbumEntity
import iad1tya.echo.music.db.entities.AlbumWithSongs
import iad1tya.echo.music.db.entities.EnhancedShuffleContextEntity
import iad1tya.echo.music.db.entities.EnhancedShufflePlayedEntity
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.Event
import iad1tya.echo.music.db.entities.EventWithSong
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.db.entities.LyricsEntity
import iad1tya.echo.music.db.entities.PlayCountEntity
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSong
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.db.entities.RecognitionHistory
import iad1tya.echo.music.db.entities.RelatedSongMap
import iad1tya.echo.music.db.entities.ReleaseRadarItem
import iad1tya.echo.music.db.entities.SearchHistory
import iad1tya.echo.music.db.entities.SetVideoIdEntity
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongAlbumMap
import iad1tya.echo.music.db.entities.SongArtistMap
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.db.entities.SongWithStats
import iad1tya.echo.music.extensions.reversed
import iad1tya.echo.music.extensions.toSQLiteQuery
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.ui.utils.resize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.text.Collator
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

@Dao
@RewriteQueriesToDropUnusedColumns
interface DatabaseDao {
    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY rowId")
    fun songsByRowIdAsc(): Flow<List<Song>>

    // Bounded seed for the on-device taste model: newest-first with a SQL LIMIT, so a 15-20k-song imported
    // library doesn't fully materialize (with all @Relation joins) before being capped — that would starve the
    // playback path, the same hazard recentEventsWithSong avoids.
    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY rowId DESC LIMIT :limit")
    fun librarySongsForTaste(limit: Int): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary")
    fun songsByCreateDateAsc(): Flow<List<Song>>

    // Newest-added library songs with a SQL LIMIT — bounded consumers (e.g. the suggestions library
    // backfill) must not materialize the WHOLE library (with all @Relation joins) just to take a few.
    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary DESC LIMIT :limit")
    fun songsByCreateDateDesc(limit: Int): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY title")
    fun songsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY totalPlayTime")
    fun songsByPlayTimeAsc(): Flow<List<Song>>


    fun songs(
        sortType: SongSortType,
        descending: Boolean,
    ) = when (sortType) {
        SongSortType.CREATE_DATE -> songsByCreateDateAsc()
        SongSortType.NAME ->
            songsByNameAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }

        SongSortType.ARTIST ->
            songsByRowIdAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs
                    .sortedWith(
                        compareBy(collator) { song ->
                            song.artists.joinToString("") { it.name }
                        },
                    ).groupBy { it.album?.title }
                    .flatMap { (_, songsByAlbum) ->
                        songsByAlbum.sortedBy { album ->
                            album.artists.joinToString(
                                "",
                            ) { it.name }
                        }
                    }
            }

        SongSortType.PLAY_TIME -> songsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY rowId")
    fun likedSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY likedDate")
    fun likedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY title")
    fun likedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY totalPlayTime")
    fun likedSongsByPlayTimeAsc(): Flow<List<Song>>

    fun likedSongs(
        sortType: SongSortType,
        descending: Boolean,
    ) = when (sortType) {
        SongSortType.CREATE_DATE -> likedSongsByCreateDateAsc()
        SongSortType.NAME ->
            likedSongsByNameAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }

        SongSortType.ARTIST ->
            likedSongsByRowIdAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs
                    .sortedWith(
                        compareBy(collator) { song ->
                            song.artists.joinToString("") { it.name }
                        },
                    ).groupBy { it.album?.title }
                    .flatMap { (_, songsByAlbum) ->
                        songsByAlbum.sortedBy { album ->
                            album.artists.joinToString(
                                "",
                            ) { it.name }
                        }
                    }
            }

        SongSortType.PLAY_TIME -> likedSongsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT COUNT(1) FROM song WHERE liked")
    fun likedSongsCount(): Flow<Int>

    // Lean SELECT-only projection of just the liked song ids, so online surfaces (which carry no local
    // `liked` flag on their SongItems) can cross-reference and pin liked songs to the top.
    @Query("SELECT id FROM song WHERE liked")
    fun likedSongIds(): Flow<List<String>>

    @Transaction
    @Query("SELECT song.* FROM song JOIN song_album_map ON song.id = song_album_map.songId WHERE song_album_map.albumId = :albumId")
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY inLibrary",
    )
    fun artistSongsByCreateDateAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY title",
    )
    fun artistSongsByNameAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY totalPlayTime",
    )
    fun artistSongsByPlayTimeAsc(artistId: String): Flow<List<Song>>

    fun artistSongs(
        artistId: String,
        sortType: ArtistSongSortType,
        descending: Boolean,
        fromTimeStamp: Long? = null,
        toTimeStamp: Long? = null,
        limit: Int = -1
    ): Flow<List<Song>> {
        val songsFlow = when (sortType) {
            ArtistSongSortType.CREATE_DATE -> artistSongsByCreateDateAsc(artistId)
            ArtistSongSortType.NAME ->
                artistSongsByNameAsc(artistId).map { artistSongs ->
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    artistSongs.sortedWith(compareBy(collator) { it.song.title })
                }

            ArtistSongSortType.PLAY_TIME -> {
                if (fromTimeStamp != null && toTimeStamp != null) {
                    mostPlayedSongsByArtist(artistId, fromTimeStamp, toTimeStamp)
                } else {
                    artistSongsByPlayTimeAsc(artistId)
                }
            }
        }

        return songsFlow.map { songs ->
            val limitedSongs = if (limit > 0) songs.take(limit) else songs
            limitedSongs.reversed(descending)
        }
    }

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT s.*
        FROM song s
        JOIN (
            SELECT e.songId, SUM(e.playTime) as totalPlayTime
            FROM event e
            JOIN song_artist_map sam ON e.songId = sam.songId
            WHERE sam.artistId = :artistId AND e.timestamp >= :fromTimeStamp AND e.timestamp <= :toTimeStamp
            GROUP BY e.songId
        ) AS play_times ON s.id = play_times.songId
        ORDER BY play_times.totalPlayTime DESC
        """
    )
    fun mostPlayedSongsByArtist(artistId: String, fromTimeStamp: Long, toTimeStamp: Long): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL LIMIT :previewSize",
    )
    fun artistSongsPreview(
        artistId: String,
        previewSize: Int = 3,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *, COUNT(1) AS referredCount
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN song ON song.id = map.relatedSongId
        WHERE songId IN (SELECT songId
                         FROM (SELECT songId
                               FROM event
                               ORDER BY ROWID DESC
                               LIMIT 5)
                         UNION
                         SELECT songId
                         FROM (SELECT songId
                               FROM event
                               WHERE timestamp > :now - 86400000 * 7
                               GROUP BY songId
                               ORDER BY SUM(playTime) DESC
                               LIMIT 5)
                         UNION
                         SELECT id
                         FROM (SELECT id
                               FROM song
                               ORDER BY totalPlayTime DESC
                               LIMIT 10))
        ORDER BY referredCount DESC
        LIMIT 100
    """,
    )
    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    // Home "Reproducido recientemente": distinct songs ordered by their MOST RECENT play event
    // (chronological history, no play-count weighting). event.id is the autoincrement PK, so
    // MAX(id) per songId == the latest listen. SELECT-only — no schema change.
    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
                 JOIN (SELECT songId, MAX(id) AS lastEventId
                       FROM event
                       GROUP BY songId) recent ON song.id = recent.songId
        ORDER BY recent.lastEventId DESC
        LIMIT :limit
    """,
    )
    fun recentlyPlayedSongs(limit: Int = 15): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT
            song.*
        FROM
            event
        JOIN
            song ON event.songId = song.id
        WHERE
            event.timestamp > (:now - 86400000 * 7 * 2)
        GROUP BY
            song.albumId
        HAVING
            song.albumId IS NOT NULL
        ORDER BY
            sum(event.playTime) DESC
        LIMIT :limit
        OFFSET :offset

        """,
    )
    fun getRecommendationAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT s.id, s.title, s.thumbnailUrl, s.isVideo,
               (SELECT name FROM artist WHERE id = sam.artistId) as artistName,
               (SELECT COUNT(1)
                FROM event
                WHERE songId = s.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCountListened,
               (SELECT SUM(event.playTime)
                FROM event
                WHERE songId = s.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM song s
        LEFT JOIN song_artist_map sam ON s.id = sam.songId
        JOIN (SELECT songId
              FROM event
              WHERE timestamp > :fromTimeStamp
                AND timestamp <= :toTimeStamp
              GROUP BY songId
              ORDER BY SUM(playTime) DESC
              LIMIT :limit) AS top_songs ON s.id = top_songs.songId
        GROUP BY s.id
        ORDER BY timeListened DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun mostPlayedSongsStats(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<SongWithStats>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT song.*,
               (SELECT COUNT(1)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCountListened,
               (SELECT SUM(event.playTime)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM song
        JOIN (SELECT songId
                     FROM event
                     WHERE timestamp > :fromTimeStamp
                     AND timestamp <= :toTimeStamp
                     GROUP BY songId
                     ORDER BY SUM(playTime) DESC
                     LIMIT :limit)
        ON song.id = songId
        LIMIT :limit
        OFFSET :offset
    """,
    )
    fun mostPlayedSongs(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCount,
               (SELECT SUM(event.playTime)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM artist
                 JOIN(SELECT artistId, SUM(songTotalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN (SELECT songId, SUM(playTime) AS songTotalPlayTime
                                     FROM event
                                     WHERE timestamp > :fromTimeStamp
                                     AND timestamp <= :toTimeStamp
                                     GROUP BY songId) AS e
                                    ON song_artist_map.songId = e.songId
                      GROUP BY artistId
                      ORDER BY totalPlayTime DESC
                      LIMIT :limit
                      OFFSET :offset)
                     ON artist.id = artistId
    """,
    )
    fun mostPlayedArtists(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
    SELECT album.*,
           COUNT(DISTINCT song_album_map.songId) as downloadCount,
           (SELECT COUNT(1)
            FROM song_album_map
                     JOIN event e ON song_album_map.songId = e.songId
            WHERE albumId = album.id
              AND e.timestamp > :fromTimeStamp
              AND e.timestamp <= :toTimeStamp) AS songCountListened,
           (SELECT SUM(e.playTime)
            FROM song_album_map
                     JOIN event e ON song_album_map.songId = e.songId
            WHERE albumId = album.id
              AND e.timestamp > :fromTimeStamp
              AND e.timestamp <= :toTimeStamp) AS timeListened
    FROM album
    JOIN song_album_map ON album.id = song_album_map.albumId
    WHERE album.id IN (
        SELECT sam.albumId
        FROM event
                 JOIN song_album_map sam ON event.songId = sam.songId
        WHERE event.timestamp > :fromTimeStamp
          AND event.timestamp <= :toTimeStamp
        GROUP BY sam.albumId
        HAVING sam.albumId IS NOT NULL
    )
    GROUP BY album.id
    ORDER BY timeListened DESC
    LIMIT :limit OFFSET :offset
    """
    )
    fun mostPlayedAlbums(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Album>>

    @Query("SELECT SUM(playTime) FROM event WHERE timestamp >= :fromTimeStamp AND timestamp <= :toTimeStamp")
    fun getTotalPlayTimeInRange(fromTimeStamp: Long, toTimeStamp: Long): Flow<Long?>

    @Query("SELECT COUNT(DISTINCT songId) FROM event WHERE timestamp >= :fromTimeStamp AND timestamp <= :toTimeStamp")
    fun getUniqueSongCountInRange(fromTimeStamp: Long, toTimeStamp: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT artistId)
        FROM event
        JOIN song_artist_map ON event.songId = song_artist_map.songId
        WHERE timestamp >= :fromTimeStamp AND timestamp <= :toTimeStamp
    """
    )
    fun getUniqueArtistCountInRange(fromTimeStamp: Long, toTimeStamp: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT albumId)
        FROM event
        JOIN song ON event.songId = song.id
        WHERE timestamp >= :fromTimeStamp AND timestamp <= :toTimeStamp
    """
    )
    fun getUniqueAlbumCountInRange(fromTimeStamp: Long, toTimeStamp: Long): Flow<Int>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album_artist_map
            JOIN album ON album_artist_map.albumId = album.id
            JOIN song ON album_artist_map.albumId = song.albumId
        WHERE artistId = :artistId
        GROUP BY album.id
        LIMIT :previewSize
    """
    )
    fun artistAlbumsPreview(artistId: String, previewSize: Int = 6): Flow<List<Album>>

    @Query("SELECT sum(count) from playCount WHERE song = :songId")
    fun getLifetimePlayCount(songId: String?): Flow<Int>

    @Query("SELECT sum(count) from playCount WHERE song = :songId AND year = :year")
    fun getPlayCountByYear(songId: String?, year: Int): Flow<Int>

    @Query("SELECT count from playCount WHERE song = :songId AND year = :year AND month = :month")
    fun getPlayCountByMonth(songId: String?, year: Int, month: Int): Flow<Int>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT n.songId      AS eid,
                     SUM(playTime) AS oldPlayTime,
                     newPlayTime
              FROM event
                       JOIN
                   (SELECT songId, SUM(playTime) AS newPlayTime
                    FROM event
                    WHERE timestamp > (:now - 86400000 * 30 * 1)
                    GROUP BY songId
                    ORDER BY newPlayTime) as n
                   ON event.songId = n.songId
              WHERE timestamp < (:now - 86400000 * 30 * 1)
              GROUP BY n.songId
              ORDER BY oldPlayTime) AS t
                 JOIN song on song.id = t.eid
        WHERE 0.2 * t.oldPlayTime > t.newPlayTime
        LIMIT 100
    """
    )
    fun forgottenFavorites(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM event
                 JOIN
             song ON event.songId = song.id
        WHERE event.timestamp > (:now - 86400000 * 7 * 2)
        GROUP BY song.albumId
        HAVING song.albumId IS NOT NULL
        ORDER BY sum(event.playTime) DESC
        LIMIT :limit
        OFFSET :offset
        """,
    )
    fun recommendedAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    fun song(songId: String?): Flow<Song?>

    @Transaction
    @Query("""
        SELECT song.* FROM song 
        LEFT JOIN song_artist_map ON song.id = song_artist_map.songId 
        LEFT JOIN artist ON song_artist_map.artistId = artist.id 
        WHERE song.id = :id 
           OR (song.title = :title AND (:artistName IS NULL OR artist.name = :artistName))
        ORDER BY CASE WHEN song.id = :id THEN 0 ELSE 1 END
        LIMIT 1
    """)
    fun songWithEquivalent(id: String, title: String, artistName: String?): Flow<Song?>


    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    suspend fun getSongById(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    fun getSongByIdBlocking(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<String>): List<Song>

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    fun getSongsByIdsFlow(songIds: List<String>): Flow<List<Song>>


    @Transaction
    @Query("SELECT * FROM song_artist_map WHERE songId = :songId")
    fun songArtistMap(songId: String): List<SongArtistMap>

    @Transaction
    @Query("SELECT * FROM song")
    fun allSongs(): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT DISTINCT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id) AS songCount
        FROM artist
                 LEFT JOIN(SELECT artistId, SUM(songTotalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN (SELECT songId, SUM(playTime) AS songTotalPlayTime
                                     FROM event
                                     GROUP BY songId) AS e
                                    ON song_artist_map.songId = e.songId
                      GROUP BY artistId
                      ORDER BY totalPlayTime DESC) AS artistTotalPlayTime
                     ON artist.id = artistId
                     OR artist.bookmarkedAt IS NOT NULL
                     ORDER BY
                      CASE
                        WHEN artistTotalPlayTime.artistId IS NULL THEN 1
                        ELSE 0
                      END,
                      artistTotalPlayTime.totalPlayTime DESC
    """,
    )
    fun allArtistsByPlayTime(): Flow<List<Artist>>

    @Query("SELECT * FROM set_video_id WHERE videoId = :videoId")
    suspend fun getSetVideoId(videoId: String): SetVideoIdEntity?

    @Transaction
    @Query("SELECT * FROM format WHERE id = :id")
    fun format(id: String?): Flow<FormatEntity?>

    /** Synchronous single-column read: lets the local-media scanner PRESERVE a cached per-play loudness
     *  measurement when it rebuilds a local song's FormatEntity on rescan (else the rescan wipes it to null). */
    @Query("SELECT measuredLoudnessDb FROM format WHERE id = :id")
    fun measuredLoudnessDbForId(id: String): Double?

    @Transaction
    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY rowId")
    fun artistsByCreateDateAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY name")
    fun artistsByNameAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY songCount")
    fun artistsBySongCountAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN song ON song_artist_map.songId = song.id
                WHERE artistId = artist.id
                  AND song.inLibrary IS NOT NULL) AS songCount
        FROM artist
                 JOIN(SELECT artistId, SUM(totalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN song
                                    ON song_artist_map.songId = song.id
                      GROUP BY artistId
                      ORDER BY totalPlayTime)
                     ON artist.id = artistId
        WHERE songCount > 0
    """
    )
    fun artistsByPlayTimeAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt")
    fun artistsBookmarkedByCreateDateAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY name")
    fun artistsBookmarkedByNameAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun artistsBookmarkedBySongCountAsc(): Flow<List<Artist>>

    // LEFT JOIN + COALESCE on purpose: an INNER JOIN dropped every followed artist with no rows in
    // song_artist_map yet (nothing of theirs is in the `song` table), so picking "play time" made
    // followed artists DISAPPEAR from the Library instead of just sorting them. They now sort with 0.
    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN song ON song_artist_map.songId = song.id
                WHERE artistId = artist.id
                  AND song.inLibrary IS NOT NULL) AS songCount
        FROM artist
                 LEFT JOIN(SELECT artistId, SUM(totalPlayTime) AS totalPlayTime
                           FROM song_artist_map
                                    JOIN song
                                         ON song_artist_map.songId = song.id
                           GROUP BY artistId) AS artistPlayTime
                     ON artist.id = artistPlayTime.artistId
        WHERE bookmarkedAt IS NOT NULL
        ORDER BY COALESCE(artistPlayTime.totalPlayTime, 0)
    """
    )
    fun artistsBookmarkedByPlayTimeAsc(): Flow<List<Artist>>

    fun artists(sortType: ArtistSortType, descending: Boolean) =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsByCreateDateAsc()
            ArtistSortType.NAME -> artistsByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsByPlayTimeAsc()
        }.map { artists ->
            artists
                .filter { it.artist.isYouTubeArtist || it.artist.isLocal } 
                .reversed(descending)
        }

    /**
     * Followed ("liked") artists. Deliberately NOT filtered by id format: every query below already
     * restricts to `bookmarkedAt IS NOT NULL`, which IS the definition of "followed". The old
     * `isYouTubeArtist || isLocal` filter silently dropped artists created with
     * [ArtistEntity.generateArtistId] (ids like "LA########") — e.g. the ones
     * [followArtistsWithContent] auto-follows — so they were followed yet could never be listed.
     */
    fun artistsBookmarked(sortType: ArtistSortType, descending: Boolean) =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsBookmarkedByCreateDateAsc()
            ArtistSortType.NAME -> artistsBookmarkedByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBookmarkedBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsBookmarkedByPlayTimeAsc()
        }.map { artists ->
            artists.reversed(descending)
        }

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE id = :id")
    fun artist(id: String): Flow<Artist?>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY rowId")
    fun albumsByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY title")
    fun albumsByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY year")
    fun albumsByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY songCount")
    fun albumsBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY duration")
    fun albumsByLengthAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL)
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """,
    )
    fun albumsByPlayTimeAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun albumsLikedByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY title")
    fun albumsLikedByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY year")
    fun albumsLikedByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun albumsLikedBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY duration")
    fun albumsLikedByLengthAsc(): Flow<List<Album>>

    // LEFT JOIN + COALESCE on purpose: an INNER JOIN dropped every liked album whose tracks aren't in
    // the `song` table yet (saved but never opened/downloaded), so picking "play time" made liked
    // albums DISAPPEAR from the Library instead of just sorting them. They now sort with 0.
    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 LEFT JOIN song
                      ON song.albumId = album.id
        WHERE bookmarkedAt IS NOT NULL
        GROUP BY album.id
        ORDER BY COALESCE(SUM(song.totalPlayTime), 0)
    """
    )
    fun albumsLikedByPlayTimeAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE isUploaded = 1 ORDER BY rowId")
    fun albumsUploadedByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE isUploaded = 1 ORDER BY title")
    fun albumsUploadedByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE isUploaded = 1 ORDER BY year")
    fun albumsUploadedByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE isUploaded = 1 ORDER BY songCount")
    fun albumsUploadedBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE isUploaded = 1 ORDER BY duration")
    fun albumsUploadedByLengthAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE album.isUploaded = 1
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """
    )
    fun albumsUploadedByPlayTimeAsc(): Flow<List<Album>>

    fun albums(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsByCreateDateAsc()
        AlbumSortType.NAME ->
            albumsByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }

        AlbumSortType.ARTIST ->
            albumsByCreateDateAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { it.name } })
            }

        AlbumSortType.YEAR -> albumsByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsBySongCountAsc()
        AlbumSortType.LENGTH -> albumsByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    fun albumsLiked(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsLikedByCreateDateAsc()
        AlbumSortType.NAME ->
            albumsLikedByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }

        AlbumSortType.ARTIST ->
            albumsLikedByCreateDateAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { it.name } })
            }

        AlbumSortType.YEAR -> albumsLikedByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsLikedBySongCountAsc()
        AlbumSortType.LENGTH -> albumsLikedByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsLikedByPlayTimeAsc()
    }.map { it.reversed(descending) }

    fun albumsUploaded(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsUploadedByCreateDateAsc()
        AlbumSortType.NAME ->
            albumsUploadedByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }

        AlbumSortType.ARTIST ->
            albumsUploadedByCreateDateAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { it.name } })
            }

        AlbumSortType.YEAR -> albumsUploadedByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsUploadedBySongCountAsc()
        AlbumSortType.LENGTH -> albumsUploadedByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsUploadedByPlayTimeAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id = :id")
    fun album(id: String): Flow<Album?>

    @Transaction
    @Query("SELECT * FROM album WHERE id = :albumId")
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?>

    @Transaction
    @Query("SELECT * FROM album_artist_map WHERE albumId = :albumId")
    fun albumArtistMaps(albumId: String): List<AlbumArtistMap>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun playlistsByCreateDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY lastUpdateTime",
    )
    fun playlistsByUpdatedDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY name")
    fun playlistsByNameAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE isEditable AND bookmarkedAt IS NOT NULL ORDER BY name")
    fun editablePlaylistsByNameAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun playlistsBySongCountAsc(): Flow<List<Playlist>>

    fun playlists(
        sortType: PlaylistSortType,
        descending: Boolean,
    ) = when (sortType) {
        PlaylistSortType.CREATE_DATE -> playlistsByCreateDateAsc()
        PlaylistSortType.NAME ->
            playlistsByNameAsc().map { playlists ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                playlists.sortedWith(compareBy(collator) { it.playlist.name })
            }

        PlaylistSortType.SONG_COUNT -> playlistsBySongCountAsc()
        PlaylistSortType.LAST_UPDATED -> playlistsByUpdatedDateAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId")
    fun playlist(playlistId: String): Flow<Playlist?>
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): Playlist?

    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId")
    fun getPlaylistByIdBlocking(playlistId: String): Playlist?

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE isEditable AND bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>>

    // ORDER BY + LIMIT are load-bearing: with a legacy duplicate pair (one row the user un-saved, one the
    // old sync re-inserted) an unordered query returned whichever row the cursor happened to yield first,
    // so the online screen's Save/Saved state could target the wrong row and create a THIRD one.
    // Saved rows win; among equals the oldest.
    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE browseId = :browseId ORDER BY bookmarkedAt IS NULL, rowId LIMIT 1")
    fun playlistByBrowseId(browseId: String): Flow<Playlist?>

    /**
     * EVERY local row for a remote playlist, including rows the user un-saved/removed (bookmarkedAt
     * NULL) and any accidental duplicates. The sync MUST look here, not through the Library-filtered
     * queries: those only see bookmarked rows, so a playlist the user removed looked "missing" and got
     * re-inserted as a second row on the next sync — the removal undid itself and left a duplicate.
     */
    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE browseId = :browseId ORDER BY rowId")
    fun playlistsByBrowseIdBlocking(browseId: String): List<Playlist>

    /**
     * EVERY row that came from a remote playlist, INCLUDING tombstones (bookmarkedAt NULL — rows kept so
     * the sync won't resurrect a playlist the user removed). "Clear synced content" must use this: a
     * Library-filtered read cannot see a browseId whose only row is a tombstone, so those rows survived
     * a full reset holding their entire song map, and the sync then skipped those playlists forever.
     */
    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE browseId IS NOT NULL ORDER BY rowId")
    fun playlistsWithBrowseIdBlocking(): List<Playlist>

    @Transaction
    @Query("SELECT COUNT(*) from playlist_song_map WHERE playlistId = :playlistId AND songId = :songId LIMIT 1")
    fun checkInPlaylist(
        playlistId: String,
        songId: String,
    ): Int

    @Query("SELECT songId from playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    fun playlistDuplicates(
        playlistId: String,
        songIds: List<String>,
    ): List<String>

    @Transaction
    fun addSongToPlaylist(playlist: Playlist, songIds: List<String>) {
        var position = playlist.songCount
        songIds.forEach { id ->
            insert(
                PlaylistSongMap(
                    songId = id,
                    playlistId = playlist.id,
                    position = position++
                )
            )
        }
        update(playlist.playlist.copy(lastUpdateTime = java.time.LocalDateTime.now()))
    }

    fun downloadedSongs(
        sortType: SongSortType,
        descending: Boolean
    ): Flow<List<Song>> = when (sortType) {
        SongSortType.CREATE_DATE -> downloadedSongsByCreateDateAsc()
        SongSortType.NAME -> downloadedSongsByNameAsc().map { songs ->
            val collator = Collator.getInstance(Locale.getDefault())
            collator.strength = Collator.PRIMARY
            songs.sortedWith(compareBy(collator) { it.song.title })
        }

        SongSortType.ARTIST -> downloadedSongsByNameAsc().map { songs ->
            val collator = Collator.getInstance(Locale.getDefault())
            collator.strength = Collator.PRIMARY
            songs.sortedWith(compareBy(collator) { song ->
                song.artists.joinToString("") { it.name }
            })
        }

        SongSortType.PLAY_TIME -> downloadedSongsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded = 1 ORDER BY dateDownload")
    fun downloadedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded = 1 ORDER BY title")
    fun downloadedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded = 1 ORDER BY totalPlayTime")
    fun downloadedSongsByPlayTimeAsc(): Flow<List<Song>>

    @Query("UPDATE song SET isDownloaded = :downloaded, dateDownload = :date WHERE id = :songId")
    fun updateDownloadedInfo(songId: String, downloaded: Boolean, date: LocalDateTime?)

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY dateDownload")
    fun uploadedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY title")
    fun uploadedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY totalPlayTime")
    fun uploadedSongsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY rowId")
    fun uploadedSongsByRowIdAsc(): Flow<List<Song>>

    fun uploadedSongs(
        sortType: SongSortType,
        descending: Boolean,
    ) = when (sortType) {
        SongSortType.CREATE_DATE -> uploadedSongsByCreateDateAsc()
        SongSortType.NAME ->
            uploadedSongsByNameAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }

        SongSortType.ARTIST ->
            uploadedSongsByRowIdAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs
                    .sortedWith(
                        compareBy(collator) { song ->
                            song.artists.joinToString("") { it.name }
                        },
                    ).groupBy { it.album?.title }
                    .flatMap { (_, songsByAlbum) ->
                        songsByAlbum.sortedBy { album ->
                            album.artists.joinToString(
                                "",
                            ) { it.name }
                        }
                    }
            }

        SongSortType.PLAY_TIME -> uploadedSongsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT * FROM song WHERE title LIKE '%' || :query || '%' AND (inLibrary IS NOT NULL OR liked = 1) LIMIT :previewSize")
    fun searchSongs(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE name LIKE '%' || :query || '%' AND songCount > 0 LIMIT :previewSize",
    )
    fun searchArtists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE title LIKE '%' || :query || '%' AND EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) LIMIT :previewSize",
    )
    fun searchAlbums(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE name LIKE '%' || :query || '%' LIMIT :previewSize",
    )
    fun searchPlaylists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId DESC")
    fun events(): Flow<List<EventWithSong>>

    // Bounded recent history for the taste engine: loading the ENTIRE history (with its song+artist
    // relations) on big restored databases starved the playback path. The recent events dominate taste.
    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId DESC LIMIT :limit")
    fun recentEventsWithSong(limit: Int): Flow<List<EventWithSong>>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId ASC LIMIT 1")
    fun firstEvent(): Flow<EventWithSong?>

    // Most-recent single event (mirror of firstEvent). Home only needs the latest played song; loading the
    // whole event table (events()) just to read element 0 materialised every row + its relations.
    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId DESC LIMIT 1")
    fun lastEvent(): Flow<EventWithSong?>

    // Bulk "unsync" statements for clearAllSyncedContent — each runs as ONE SQL UPDATE instead of materialising
    // the whole library (song+artist relations) into memory and updating row-by-row (OOM/ANR on big libraries).
    @Query("UPDATE song SET liked = 0, likedDate = NULL WHERE liked")
    suspend fun clearAllLikedSongs()

    @Query("UPDATE song SET inLibrary = NULL WHERE inLibrary IS NOT NULL")
    suspend fun clearAllLibrarySongs()

    @Query("UPDATE album SET bookmarkedAt = NULL WHERE bookmarkedAt IS NOT NULL")
    suspend fun clearAllLikedAlbums()

    // Clears the upload markers TOO, and deliberately without a WHERE on bookmarkedAt. This is a LOCAL
    // reset (logout, "borrar contenido sincronizado", account switch) — it must never turn into a mass
    // unsubscribe on the user's real YouTube account. Wiping local state is not a statement about that
    // account, so every marker goes at once and the reconciler is left with nothing to act on in
    // either direction. Mirror of ArtistSyncPolicy.afterLocalReset.
    @Query("UPDATE artist SET bookmarkedAt = NULL, followedByUserAt = NULL, ytmSyncedAt = NULL, unfollowedByUserAt = NULL WHERE bookmarkedAt IS NOT NULL OR followedByUserAt IS NOT NULL OR ytmSyncedAt IS NOT NULL OR unfollowedByUserAt IS NOT NULL")
    suspend fun clearAllBookmarkedArtists()

    /**
     * Cut every artist row loose from the YouTube ACCOUNT that is being detached — logout (BOTH
     * buttons), account switch, unparseable cookie, or a library restored from someone else's backup.
     * Mirror of `ArtistSyncPolicy.afterAccountDetached`; change one, change both.
     *
     * Deliberately does NOT touch `bookmarkedAt` (that is the library, and "cerrar sesión (mantener
     * datos)" promises to keep it) and does NOT touch `followedByUserAt` — clearing that would silently
     * destroy a follow that had not been pushed up yet, since the subscription read-back can only
     * re-stamp ids the account actually returns.
     *
     * `followedByUserAt` is NOT account-neutral, whatever an earlier version of this comment said:
     * [markArtistsSubscribedOnYtm] writes it wholesale from the attached account's remote list, so
     * most of the markers left standing here are a copy of the DETACHED account's follows. They are a
     * pending-SUBSCRIBE shape aimed at whoever signs in next. What keeps that harmless is that
     * `App.forgetAccount` revokes the library-upload switch in the same breath
     * (`LibraryUploadOptIn.onAccountDetached`), so no bulk push can run until the new account's owner
     * consents. See `ArtistSyncPolicy.afterAccountDetached`.
     *
     * The two columns it does clear are the account-scoped ones, and clearing them is what closes the
     * blocker: a row left in the pending-UNSUBSCRIBE shape (`unfollowedByUserAt` + `ytmSyncedAt`) after
     * a keep-data logout was later flushed against a DIFFERENT account, unsubscribing channels that
     * account's owner had never unfollowed.
     */
    @Query(
        """
        UPDATE artist SET ytmSyncedAt = NULL, unfollowedByUserAt = NULL
        WHERE ytmSyncedAt IS NOT NULL OR unfollowedByUserAt IS NOT NULL
        """
    )
    suspend fun clearArtistAccountSyncMarkers()

    /**
     * Cheap "did this database arrive with data already in it?" probe, used ONCE at startup by
     * `App.classifyInstallOrigin`. A genuine first-ever launch has an empty song table; a database
     * restored by a device-to-device transfer or a cloud backup does not. `EXISTS ... LIMIT 1` stops at
     * the first row, so this costs nothing even on a huge library.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM song LIMIT 1)")
    suspend fun hasAnySong(): Boolean

    @Query("UPDATE song SET isUploaded = 0 WHERE isUploaded = 1")
    suspend fun clearAllUploadedSongs()

    @Query("UPDATE album SET isUploaded = 0 WHERE isUploaded = 1")
    suspend fun clearAllUploadedAlbums()

    @Query("SELECT COUNT(*) FROM event")
    fun eventCount(): Flow<Int>

    @Transaction
    @Query("DELETE FROM event")
    fun clearListenHistory()

    @Transaction
    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Transaction
    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    
    @Transaction
    @Query("SELECT * FROM recognition_history ORDER BY recognizedAt DESC")
    fun recognitionHistory(): Flow<List<RecognitionHistory>>

    @Transaction
    @Query("SELECT * FROM recognition_history WHERE id = :id")
    fun recognitionHistoryById(id: Long): Flow<RecognitionHistory?>

    @Transaction
    @Query("SELECT * FROM recognition_history WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY recognizedAt DESC")
    fun searchRecognitionHistory(query: String): Flow<List<RecognitionHistory>>

    @Transaction
    @Query("DELETE FROM recognition_history")
    fun clearRecognitionHistory()

    @Transaction
    @Query("DELETE FROM recognition_history WHERE id = :id")
    fun deleteRecognitionHistoryById(id: Long)

    @Transaction
    @Query("UPDATE recognition_history SET liked = :liked WHERE id = :id")
    fun updateRecognitionHistoryLiked(id: Long, liked: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(recognitionHistory: RecognitionHistory): Long

    @Delete
    fun delete(recognitionHistory: RecognitionHistory)

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :playTime WHERE id = :songId")
    fun incrementTotalPlayTime(songId: String, playTime: Long)

    @Query("UPDATE playCount SET count = count + 1 WHERE song = :songId AND year = :year AND month = :month")
    fun incrementPlayCount(songId: String, year: Int, month: Int)

    
    fun incrementPlayCount(songId: String) {
        val time = LocalDateTime.now().atOffset(ZoneOffset.UTC)
        var oldCount: Int
        runBlocking {
            oldCount = getPlayCountByMonth(songId, time.year, time.monthValue).first()
        }

        
        if (oldCount <= 0) {
            insert(PlayCountEntity(songId, time.year, time.monthValue, 0))
        }
        incrementPlayCount(songId, time.year, time.monthValue)
    }

    @Transaction
    @Query("UPDATE song SET inLibrary = :inLibrary WHERE id = :songId")
    fun inLibrary(
        songId: String,
        inLibrary: LocalDateTime?,
    )

    @Transaction
    @Query("UPDATE song SET libraryAddToken = :libraryAddToken, libraryRemoveToken = :libraryRemoveToken WHERE id = :songId")
    fun addLibraryTokens(
        songId: String,
        libraryAddToken: String?,
        libraryRemoveToken: String?,
    )

    @Transaction
    @Query("SELECT COUNT(1) FROM related_song_map WHERE songId = :songId LIMIT 1")
    fun hasRelatedSongs(songId: String): Boolean

    @Transaction
    @Query(
        "SELECT song.* FROM (SELECT * from related_song_map GROUP BY relatedSongId) map JOIN song ON song.id = map.relatedSongId where songId = :songId",
    )
    fun getRelatedSongs(songId: String): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN
             song
             ON song.id = map.relatedSongId
        WHERE songId = :songId
        """
    )
    fun relatedSongs(songId: String): List<Song>

    @Transaction
    @Query(
        """
        UPDATE playlist_song_map SET position =
            CASE
                WHEN position < :fromPosition THEN position + 1
                WHEN position > :fromPosition THEN position - 1
                ELSE :toPosition
            END
        WHERE playlistId = :playlistId AND position BETWEEN MIN(:fromPosition, :toPosition) AND MAX(:fromPosition, :toPosition)
    """,
    )
    fun move(
        playlistId: String,
        fromPosition: Int,
        toPosition: Int,
    )

    @Transaction
    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    fun clearPlaylist(playlistId: String)

    @Transaction
    @Query("SELECT * FROM artist WHERE name = :name")
    fun artistByName(name: String): ArtistEntity?

    @Query("SELECT * FROM artist WHERE id = :id LIMIT 1")
    fun getArtistById(id: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artist: ArtistEntity)

    /** Follow (bookmark) every artist that has songs or albums in the library but isn't followed yet —
     *  so artists imported/synced from YouTube Music show up under "your artists", like Spotify follows.
     *
     *  Skips rows carrying a pending unfollow. The artist sync clears `bookmarkedAt` on those on
     *  purpose (the user unfollowed them and the unsubscribe has not reached YouTube yet); this
     *  statement runs later in the SAME pass and used to put them straight back into "tus artistas"
     *  while the queued unsubscribe was still going to fire. The user saw the artist reappear and then
     *  vanish from YouTube. Their own unfollow is the newer instruction, so it wins until it has been
     *  honoured — at which point `clearArtistsSyncedToYtm` drops the marker and a later song of theirs
     *  can bookmark them again exactly as before. */
    @Query(
        """
        UPDATE artist SET bookmarkedAt = :now
        WHERE bookmarkedAt IS NULL AND unfollowedByUserAt IS NULL AND id IN (
            SELECT artistId FROM song_artist_map
            UNION
            SELECT artistId FROM album_artist_map
        )
        """
    )
    fun followArtistsWithContent(now: java.time.LocalDateTime)

    // ---- Library UPLOAD sync (Aura -> YouTube Music) -------------------------------------------
    // These queries are the ONLY source the uploader reads, and every one of them is a mirror of
    // `iad1tya.echo.music.utils.ArtistSyncPolicy` — change one, change both.
    //
    // Two rules, both learnt the hard way:
    //  1. NEVER key off `bookmarkedAt`. followArtistsWithContent() sets it on every artist that merely
    //     has a song in the library; uploading that set is what caused "me aparecen muchas
    //     suscripciones de cantantes que no sigo".
    //  2. NEVER infer the destructive direction from an ABSENCE. An unsubscribe requires the positive
    //     `unfollowedByUserAt` marker. A missing follow marker is what a logout, a "borrar contenido
    //     sincronizado", a restore from an old backup, an account switch and a half-written down-sync
    //     all produce — and a local clear must never be readable as "unsubscribe me from all of these".

    /** Deliberate follows that YouTube does not have a subscription for yet -> pending SUBSCRIBE. */
    @Query(
        """
        SELECT * FROM artist
        WHERE followedByUserAt IS NOT NULL AND ytmSyncedAt IS NULL AND unfollowedByUserAt IS NULL
          AND isLocal = 0
        ORDER BY followedByUserAt LIMIT :limit
        """
    )
    suspend fun artistsPendingSubscribe(limit: Int): List<ArtistEntity>

    /**
     * Rows the user deliberately UNFOLLOWED while we still hold a subscription for them -> pending
     * UNSUBSCRIBE. This is the ONLY query that can lead to removing something from the account.
     *
     * It matches on `unfollowedByUserAt` — written exclusively by [ArtistEntity.localToggleLike] when
     * the user unfollows an artist they had deliberately followed. Nothing that merely LOSES local
     * state can set it, which is what makes a local wipe inert instead of catastrophic. The two
     * absence conditions are kept as belt and braces: a re-follow supersedes the queued unsubscribe,
     * and a row we hold no subscription for has nothing to remove.
     */
    @Query(
        """
        SELECT * FROM artist
        WHERE unfollowedByUserAt IS NOT NULL AND followedByUserAt IS NULL AND ytmSyncedAt IS NOT NULL
          AND isLocal = 0
        LIMIT :limit
        """
    )
    suspend fun artistsPendingUnsubscribe(limit: Int): List<ArtistEntity>

    @Query("SELECT COUNT(*) FROM artist WHERE followedByUserAt IS NOT NULL AND isLocal = 0")
    suspend fun countDeliberateFollows(): Int

    @Query("SELECT COUNT(*) FROM artist WHERE followedByUserAt IS NOT NULL AND ytmSyncedAt IS NOT NULL AND isLocal = 0")
    suspend fun countFollowsSyncedToYtm(): Int

    /** Must stay identical to the predicate of [artistsPendingUnsubscribe] or the report lies. */
    @Query(
        """
        SELECT COUNT(*) FROM artist
        WHERE unfollowedByUserAt IS NOT NULL AND followedByUserAt IS NULL AND ytmSyncedAt IS NOT NULL
          AND isLocal = 0
        """
    )
    suspend fun countPendingUnsubscribes(): Int

    /** Stamp artists as "the account has this subscription" after a confirmed subscribe. */
    @Query("UPDATE artist SET ytmSyncedAt = :now WHERE id IN (:ids)")
    suspend fun markArtistsSyncedToYtm(ids: List<String>, now: java.time.LocalDateTime)

    /**
     * Clear the subscription state after a confirmed unsubscribe. The INTENT marker goes with it: it
     * has been honoured, and leaving it set would make a later re-follow ambiguous (and would keep the
     * row queued for an unsubscribe forever).
     */
    @Query("UPDATE artist SET ytmSyncedAt = NULL, unfollowedByUserAt = NULL WHERE id IN (:ids)")
    suspend fun clearArtistsSyncedToYtm(ids: List<String>)

    /**
     * Retire ONE queued unfollow that the LIVE call in [ArtistEntity.toggleLike] has just carried out
     * successfully. Mirror of `ArtistSyncPolicy.liveCallHonouredTheUnfollow` +
     * `ArtistSyncPolicy.afterUnsubscribed`; change one, change both.
     *
     * Without it, `toggleLike` was fire-and-forget and an unfollow it had already delivered stayed on
     * the row as a standing order with `ytmSyncedAt` null — a shape [artistsPendingUnsubscribe] cannot
     * see (it requires `ytmSyncedAt IS NOT NULL`), so the STALE_INTENT retirement never reached it
     * either. If the user re-subscribed to that artist on YouTube itself within the TTL, the read-back
     * stamped `ytmSyncedAt`, the row became a genuine-looking pending unsubscribe, and Aura reversed
     * the subscription the user had just made.
     *
     * ### Why the three extra conditions, instead of updating by id alone
     * The live call is asynchronous, so the user can act again before it returns:
     *  - `followedByUserAt IS NULL` — they re-followed in the meantime. `localToggleLike` already
     *    cleared the marker and set the follow; blanking `ytmSyncedAt`/the marker here is harmless but
     *    writing at all is not, so stay out of the way and let the newest action win.
     *  - `unfollowedByUserAt IS NOT NULL AND unfollowedByUserAt <= :unfollowedAt` — they unfollowed
     *    AGAIN (re-follow, unfollow) while this call was in flight. That newer instruction has not
     *    been delivered, and clearing it would be exactly the silent loss this column exists to stop.
     *
     * A targeted UPDATE rather than an entity write: the caller holds a snapshot taken before the
     * network call, and persisting it would also stomp any `name`/`thumbnailUrl`/`channelId` a
     * down-sync refreshed in between.
     */
    @Query(
        """
        UPDATE artist SET ytmSyncedAt = NULL, unfollowedByUserAt = NULL
        WHERE id = :artistId
          AND followedByUserAt IS NULL
          AND unfollowedByUserAt IS NOT NULL
          AND unfollowedByUserAt <= :unfollowedAt
        """
    )
    suspend fun confirmArtistUnsubscribed(artistId: String, unfollowedAt: java.time.LocalDateTime)

    /**
     * Backfill for the v39->v40 columns and for every artist down-sync: record that the account really
     * IS subscribed to these artists. Only ids read back from FEmusic_library_corpus_artists get here,
     * so each one is a subscription that demonstrably exists on the user's account.
     *
     * Mirror of [iad1tya.echo.music.utils.ArtistSyncPolicy.afterRemoteSubscriptionSeen]. Two properties
     * matter more than anything else in this file:
     *
     *  - It does NOT read `bookmarkedAt`. The previous version did, and stamped `ytmSyncedAt` while
     *    leaving `followedByUserAt` NULL for every row whose bookmark had been cleared or never set —
     *    manufacturing the old pending-UNSUBSCRIBE signature out of thin air. Since the uploader reads
     *    the account list and flushes unsubscribes IN THE SAME PASS, that turned a logout, a restore or
     *    a plain incidental artist row into a real, irreversible mass unsubscribe.
     *  - It can never PRODUCE the pending-unsubscribe shape. `followedByUserAt` is stamped alongside
     *    `ytmSyncedAt` — except on rows that already carry a genuine `unfollowedByUserAt`, where the
     *    queued unfollow is preserved so an unsubscribe that never reached YouTube (offline, expired
     *    cookie) is retried instead of being silently lost.
     */
    @Query(
        """
        UPDATE artist SET
            followedByUserAt = CASE
                WHEN unfollowedByUserAt IS NOT NULL THEN followedByUserAt
                ELSE COALESCE(followedByUserAt, :now)
            END,
            ytmSyncedAt = :now
        WHERE id IN (:ids)
        """
    )
    suspend fun markArtistsSubscribedOnYtm(ids: List<String>, now: java.time.LocalDateTime)

    /**
     * Playlists that live only in Aura and should be created on the account ("las que ya tenga creadas
     * las asocie a mi cuenta"). A playlist that already has a browseId is already linked and is
     * EXCLUDED here — that is what makes re-running the upload unable to duplicate anything.
     * Excludes local-file playlists and the regenerated auto-recommendations playlist (a fixed-id row
     * this app rewrites in the background; uploading it would churn the account).
     */
    @Query(
        """
        SELECT * FROM playlist
        WHERE browseId IS NULL AND bookmarkedAt IS NOT NULL AND isLocal = 0
          AND isEditable = 1 AND id != 'AURA_AI_RECS'
        ORDER BY bookmarkedAt LIMIT :limit
        """
    )
    suspend fun playlistsPendingUpload(limit: Int): List<PlaylistEntity>

    @Query(
        """
        SELECT COUNT(*) FROM playlist
        WHERE browseId IS NULL AND bookmarkedAt IS NOT NULL AND isLocal = 0
          AND isEditable = 1 AND id != 'AURA_AI_RECS'
        """
    )
    suspend fun countPlaylistsPendingUpload(): Int

    @Query("SELECT COUNT(*) FROM playlist WHERE browseId IS NOT NULL AND bookmarkedAt IS NOT NULL AND isLocal = 0")
    suspend fun countPlaylistsLinkedToYtm(): Int

    @Query("SELECT COUNT(*) FROM song WHERE liked = 1 AND isLocal = 0")
    suspend fun countLikedSongsSyncable(): Int

    @Query("SELECT id FROM song WHERE liked = 1 AND isLocal = 0")
    suspend fun likedSongIdsSyncable(): List<String>

    @Query("SELECT COUNT(*) FROM album WHERE bookmarkedAt IS NOT NULL AND isLocal = 0")
    suspend fun countLikedAlbumsSyncable(): Int

    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL AND isLocal = 0 AND playlistId IS NOT NULL")
    suspend fun likedAlbumsSyncable(): List<AlbumEntity>

    /** Song ids of a playlist in position order — the payload for uploading a local-only playlist. */
    @Query("SELECT songId FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    suspend fun playlistSongIdsInOrder(playlistId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongArtistMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongAlbumMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: AlbumArtistMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: PlaylistSongMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchHistory: SearchHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: RelatedSongMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playCountEntity: PlayCountEntity): Long

    // ---- Batch insert primitives (sync path). Writing a whole block of rows through these inside a
    // single transaction makes Room's invalidation tracker emit ONCE per block instead of once per row
    // — that (plus dropping the old per-row throttle) is what removes the Library "flicker" during a
    // sync while still filling the list in near real time. IGNORE-on-conflict matches the single-row
    // inserts these replace. ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongArtistMaps(maps: List<SongArtistMap>)

    /**
     * Batch equivalent of [insert] (MediaMetadata): inserts a block of brand-new songs together with
     * their artist rows and song↔artist maps in a single transaction. [songs] and [mediaList] are
     * parallel / index-aligned — [songs] carries the fully-built SongEntity (liked/likedDate/isVideo/
     * library flags already applied by the caller) and [mediaList] carries the matching MediaMetadata
     * used to derive the artists. Mirrors the row-by-row semantics of [insert] (IGNORE-on-conflict,
     * artist id resolution) but emits a single Room invalidation for the whole block.
     */
    @Transaction
    fun insertSongsWithArtists(
        songs: List<SongEntity>,
        mediaList: List<MediaMetadata>,
    ) {
        if (songs.isEmpty()) return
        insertSongs(songs)
        val artistEntities = ArrayList<ArtistEntity>()
        val songArtistMaps = ArrayList<SongArtistMap>()
        // Cache generated ids for id-less artists so two songs in the SAME block that share an artist
        // without a channel id map to one artist row (the row-by-row path relied on the previous insert
        // being committed & findable by name; here we dedup in memory instead).
        val generatedIdByName = HashMap<String, String>()
        mediaList.forEach { mediaMetadata ->
            mediaMetadata.artists.forEachIndexed { index, artist ->
                val artistId = artist.id
                    ?: generatedIdByName[artist.name]
                    ?: artistByName(artist.name)?.id
                    ?: ArtistEntity.generateArtistId().also { generatedIdByName[artist.name] = it }
                artistEntities.add(
                    ArtistEntity(
                        id = artistId,
                        name = artist.name,
                        channelId = artist.id,
                    )
                )
                songArtistMaps.add(
                    SongArtistMap(
                        songId = mediaMetadata.id,
                        artistId = artistId,
                        position = index,
                    )
                )
            }
        }
        insertArtists(artistEntities)
        insertSongArtistMaps(songArtistMaps)
    }

    @Transaction
    fun insert(
        mediaMetadata: MediaMetadata,
        block: (SongEntity) -> SongEntity = { it },
    ) {
        if (insert(mediaMetadata.toSongEntity().let(block)) == -1L) return

        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()

            insert(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                )
            )

            insert(
                SongArtistMap(
                    songId = mediaMetadata.id,
                    artistId = artistId,
                    position = index,
                )
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        if (insert(
                AlbumEntity(
                    id = albumPage.album.browseId,
                    playlistId = albumPage.album.playlistId,
                    title = albumPage.album.title,
                    year = albumPage.album.year,
                    thumbnailUrl = albumPage.album.thumbnail,
                    songCount = albumPage.songs.size,
                    duration = albumPage.songs.sumOf { it.duration ?: 0 },
                    explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
                    description = albumPage.description,
                ),
            ) == -1L
        ) {
            return
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlocking(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id
                    ?: ArtistEntity.generateArtistId(),
                    name = artist.name,
                )
            }?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index,
                )
            }?.forEach(::insert)
    }

    @Transaction
    fun update(
        song: Song,
        mediaMetadata: MediaMetadata,
    ) {
        update(
            song.song.copy(
                title = mediaMetadata.title,
                duration = mediaMetadata.duration,
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumId = mediaMetadata.album?.id,
                albumName = mediaMetadata.album?.title,
                libraryAddToken = mediaMetadata.libraryAddToken,
                libraryRemoveToken = mediaMetadata.libraryRemoveToken
            ),
        )
        songArtistMap(song.id).forEach(::delete)
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()

            insert(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                ),
            )
            insert(
                SongArtistMap(
                    songId = song.id,
                    artistId = artistId,
                    position = index,
                ),
            )
        }
    }

    @Update
    fun update(song: SongEntity)

    // Batch updates (sync path): one Room invalidation per block instead of per row.
    @Update
    fun updateSongs(songs: List<SongEntity>)

    @Update
    fun updateArtists(artists: List<ArtistEntity>)

    @Update
    fun update(artist: ArtistEntity)

    /** Followed artists with no cover image yet (e.g. artists that came in only via synced songs) —
     *  used to fetch and fill their picture in the background. */
    @Query("SELECT * FROM artist WHERE bookmarkedAt IS NOT NULL AND (thumbnailUrl IS NULL OR thumbnailUrl = '') LIMIT :limit")
    suspend fun bookmarkedArtistsMissingImage(limit: Int): List<ArtistEntity>

    @Update
    fun update(album: AlbumEntity)

    @Update
    fun update(playlist: PlaylistEntity)

    @Update
    fun update(map: PlaylistSongMap)

    @Transaction
    fun update(
        artist: ArtistEntity,
        artistPage: ArtistPage
    ) {
        update(
            artist.copy(
                name = artistPage.artist.title,
                thumbnailUrl = artistPage.artist.thumbnail?.resize(544, 544),
                lastUpdateTime = LocalDateTime.now()
            )
        )
    }

    @Transaction
    fun update(
        album: AlbumEntity,
        albumPage: AlbumPage,
        artists: List<ArtistEntity>? = emptyList(),
    ) {
        update(
            album.copy(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { it.duration ?: 0 },
                explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
                description = albumPage.description ?: album.description,
            ),
        )
        if (artists?.size != albumPage.album.artists?.size) {
            artists?.forEach(::delete)
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlocking(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsert)

        albumPage.album.artists?.let { artists ->
            
            albumArtistMaps(album.id).forEach(::delete)
            artists
                .map { artist ->
                    ArtistEntity(
                        id = artist.id ?: artistByName(artist.name)?.id
                        ?: ArtistEntity.generateArtistId(),
                        name = artist.name,
                    )
                }.onEach(::insert)
                .mapIndexed { index, artist ->
                    AlbumArtistMap(
                        albumId = albumPage.album.browseId,
                        artistId = artist.id,
                        order = index,
                    )
                }.forEach(::insert)
        }
    }

    @Update
    fun update(playlistEntity: PlaylistEntity, playlistItem: PlaylistItem) {
        update(
            playlistEntity.copy(
                name = playlistItem.title,
                browseId = playlistItem.id,
                thumbnailUrl = playlistItem.thumbnail,
                isEditable = playlistItem.isEditable,
                remoteSongCount = playlistItem.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                playEndpointParams = playlistItem.playEndpoint?.params,
                shuffleEndpointParams = playlistItem.shuffleEndpoint?.params,
                radioEndpointParams = playlistItem.radioEndpoint?.params
            )
        )
    }

    @Upsert
    fun upsert(map: SongAlbumMap)

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Upsert
    fun upsert(format: FormatEntity)

    @Upsert
    fun upsert(song: SongEntity)

    @Delete
    fun delete(song: SongEntity)

    @Delete
    fun delete(songArtistMap: SongArtistMap)

    @Delete
    fun delete(artist: ArtistEntity)

    @Delete
    fun delete(album: AlbumEntity)

    @Delete
    fun delete(albumArtistMap: AlbumArtistMap)

    @Delete
    fun delete(playlist: PlaylistEntity)

    @Delete
    fun delete(playlistSongMap: PlaylistSongMap)

    @Query("DELETE FROM playlist WHERE browseId = :browseId")
    fun deletePlaylistById(browseId: String)

    @Delete
    fun delete(lyrics: LyricsEntity)

    @Delete
    fun delete(searchHistory: SearchHistory)

    @Delete
    fun delete(event: Event)

    @Upsert
    suspend fun upsertReleases(items: List<ReleaseRadarItem>)

    /**
     * Insert only releases not already present (PK = artist|title dedupeKey). IGNORE means an
     * already-known release keeps its ORIGINAL [ReleaseRadarItem.fetchedAt] — i.e. fetchedAt marks
     * when a release was FIRST seen and is immutable across weekly re-fetches. This is what lets the
     * weekly window ([releasesSince]) and prune ([pruneReleasesBefore]) behave like Spotify's
     * Release Radar (only this week's drop is shown; older drops fall off).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewReleasesIgnore(items: List<ReleaseRadarItem>)

    @Query("SELECT * FROM release_radar ORDER BY releaseDate DESC")
    fun releasesByDateDesc(): Flow<List<ReleaseRadarItem>>

    /** Releases first seen on/after [since] (the current Friday weekly-window start), newest first. */
    @Query("SELECT * FROM release_radar WHERE fetchedAt >= :since ORDER BY releaseDate DESC")
    fun releasesSince(since: LocalDateTime): Flow<List<ReleaseRadarItem>>

    /** Drop releases first seen before [threshold] so previous weeks' drops disappear (Friday refresh). */
    @Query("DELETE FROM release_radar WHERE fetchedAt < :threshold")
    suspend fun pruneReleasesBefore(threshold: LocalDateTime)

    @Query("UPDATE release_radar SET seen = 1 WHERE seen = 0")
    suspend fun markAllReleasesSeen()

    @Query("DELETE FROM release_radar")
    suspend fun clearReleases()

    @Query("SELECT COUNT(*) FROM release_radar WHERE seen = 0")
    fun unseenReleaseCount(): Flow<Int>

    // ---- Enhanced Shuffle ("Aleatorio mejorado") persistent per-context no-repeat memory ----

    /** Song ids already played this cycle for [contextId] (persists across restarts / days). */
    @Query("SELECT songId FROM enhanced_shuffle_played WHERE contextId = :contextId")
    suspend fun playedSongIdsForContext(contextId: String): List<String>

    /** How many songs of [contextId] have already played this cycle. */
    @Query("SELECT COUNT(*) FROM enhanced_shuffle_played WHERE contextId = :contextId")
    suspend fun countPlayedForContext(contextId: String): Int

    /**
     * Reactive played-set for [contextId] (UI: per-song "ya reproducida" dim/check + the X/Y counter chip).
     * Emits a fresh list on every insert (song played) or clear (cycle reset), so the playlist screen updates
     * live as songs sound. Empty list = nothing played yet / context has no memory.
     */
    @Query("SELECT songId FROM enhanced_shuffle_played WHERE contextId = :contextId")
    fun playedSongIdsForContextFlow(contextId: String): Flow<List<String>>

    /** Record a song as played for a context. IGNORE = a re-play never rewrites the first-played row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEnhancedPlayed(entity: EnhancedShufflePlayedEntity)

    /** Wipe a context's played-set so the next cycle starts fresh (called on cycle completion). */
    @Query("DELETE FROM enhanced_shuffle_played WHERE contextId = :contextId")
    suspend fun clearEnhancedContext(contextId: String)

    @Query("SELECT * FROM enhanced_shuffle_context WHERE contextId = :contextId")
    suspend fun getEnhancedContext(contextId: String): EnhancedShuffleContextEntity?

    @Upsert
    suspend fun upsertEnhancedContext(entity: EnhancedShuffleContextEntity)

    /** Ensure a context cursor row exists without clobbering an existing one (IGNORE on conflict). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEnhancedContextIgnore(entity: EnhancedShuffleContextEntity)

    /** Update just the resume cursor (never touches cycleCount). */
    @Query("UPDATE enhanced_shuffle_context SET lastSongId = :lastSongId, lastPositionMs = :positionMs, updatedAt = :updatedAt WHERE contextId = :contextId")
    suspend fun updateEnhancedContextCursor(contextId: String, lastSongId: String?, positionMs: Long, updatedAt: Long)

    /** Cycle completed for a context: bump the counter (played-set cleared separately). */
    @Query("UPDATE enhanced_shuffle_context SET cycleCount = cycleCount + 1, updatedAt = :updatedAt WHERE contextId = :contextId")
    suspend fun incrementEnhancedCycle(contextId: String, updatedAt: Long)

    /**
     * Orphan prune: drop enhanced-shuffle memory for "PL:<id>" contexts whose playlist no longer exists
     * (deleted playlists leave no FK to cascade — these tables are intentionally FK-less). Run once at
     * startup. "LIB:<tab>" contexts are bounded (a handful of tabs, reset each full cycle) and never orphan.
     */
    @Query("DELETE FROM enhanced_shuffle_played WHERE contextId LIKE 'PL:%' AND substr(contextId, 4) NOT IN (SELECT id FROM playlist)")
    suspend fun pruneOrphanEnhancedPlayed()

    @Query("DELETE FROM enhanced_shuffle_context WHERE contextId LIKE 'PL:%' AND substr(contextId, 4) NOT IN (SELECT id FROM playlist)")
    suspend fun pruneOrphanEnhancedContext()

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE songId = :songId")
    fun playlistSongMaps(songId: String): List<PlaylistSongMap>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId AND position >= :from ORDER BY position")
    fun playlistSongMaps(
        playlistId: String,
        from: Int,
    ): List<PlaylistSongMap>

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }

    @Transaction
    @Query("SELECT * FROM song WHERE isLocal = 1 ORDER BY title COLLATE NOCASE, id")
    fun localSongs(): Flow<List<Song>>

    @Query("SELECT id FROM song WHERE isLocal = 1")
    suspend fun localSongIds(): List<String>

    @Query("DELETE FROM song WHERE isLocal = 1")
    fun clearLocalSongs()

    @Query("DELETE FROM album WHERE isLocal = 1 AND id NOT IN (SELECT DISTINCT albumId FROM song WHERE isLocal = 1 AND albumId IS NOT NULL)")
    fun pruneLocalAlbums()

    @Query("DELETE FROM artist WHERE isLocal = 1 AND id NOT IN (SELECT DISTINCT song_artist_map.artistId FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE song.isLocal = 1)")
    fun pruneLocalArtists()

    @Query("SELECT * FROM artist WHERE id IN (:ids)")
    suspend fun getArtistEntitiesByIds(ids: List<String>): List<ArtistEntity>

    @Query("SELECT * FROM album WHERE id IN (:ids)")
    suspend fun getAlbumEntitiesByIds(ids: List<String>): List<AlbumEntity>

    @Query("DELETE FROM song_album_map WHERE songId = :songId")
    fun deleteSongAlbumMaps(songId: String)

    @Query("DELETE FROM format WHERE id NOT IN (SELECT id FROM song)")
    fun pruneFormats()

    /** Refetch: drop a single stale format row so the next resolve re-stores the real itag/bitrate/loudness
     *  instead of the container the previous stream happened to have. */
    @Query("DELETE FROM format WHERE id = :id")
    fun deleteFormat(id: String)

    @Query("DELETE FROM playCount WHERE song NOT IN (SELECT id FROM song)")
    fun prunePlayCounts()

    @Query("DELETE FROM song WHERE id IN (:songIds)")
    fun deleteSongsByIds(songIds: List<String>)

    @Query("DELETE FROM song_artist_map WHERE songId = :songId")
    fun deleteSongArtistMaps(songId: String)

    @Query("DELETE FROM album_artist_map WHERE albumId IN (:albumIds)")
    fun deleteAlbumArtistMapsByAlbumIds(albumIds: List<String>)
}
