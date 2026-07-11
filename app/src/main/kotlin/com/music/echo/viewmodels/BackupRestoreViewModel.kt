

package iad1tya.echo.music.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import iad1tya.echo.music.MainActivity
import iad1tya.echo.music.R
import iad1tya.echo.music.db.InternalDatabase
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.extensions.div
import iad1tya.echo.music.extensions.tryOrNull
import iad1tya.echo.music.extensions.zipInputStream
import iad1tya.echo.music.extensions.zipOutputStream
import iad1tya.echo.music.playback.MusicService
import iad1tya.echo.music.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import iad1tya.echo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import androidx.lifecycle.viewModelScope
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.system.exitProcess

data class CsvImportState(
    val previewRows: List<List<String>> = emptyList(),
    val artistColumnIndex: Int = 0,
    val titleColumnIndex: Int = 1,
    val urlColumnIndex: Int = -1,
    val hasHeader: Boolean = true,
)

data class ConvertedSongLog(
    val title: String,
    val artists: String,
)

/**
 * Distinct restore failures so the UI can show an ACCURATE reason instead of the old catch-all
 * "new architecture" message. Thrown only during extraction/validation — i.e. BEFORE any live data
 * is touched — so hitting one leaves the app fully usable.
 */
private sealed class RestoreException(message: String) : Exception(message) {
    /** The backup DB schema is newer than this app can open (byte-60 user_version > live version). */
    class Incompatible(val backupVersion: Int, val currentVersion: Int) :
        RestoreException("Backup DB v$backupVersion > app DB v$currentVersion")

    /** The backup file/DB is corrupt or truncated (failed integrity_check or won't open as a DB). */
    class Corrupt(detail: String) : RestoreException("Corrupt backup: $detail")

    /** The archive contained none of the expected entries (settings / db / EQ prefs). */
    object Empty : RestoreException("Archive had no restorable entries")
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
    private val eqProfileRepository: iad1tya.echo.music.eq.data.EQProfileRepository,
) : ViewModel() {

    /** Local playlists for the selective-export picker. */
    val playlists: StateFlow<List<iad1tya.echo.music.db.entities.Playlist>> =
        database.playlists(iad1tya.echo.music.constants.PlaylistSortType.NAME, false)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    fun backup(context: Context, uri: Uri) {
        // Run the file copy/zip off the main thread so backing up a large library doesn't freeze the UI.
        viewModelScope.launch(Dispatchers.IO) {
        var dbSnapshot: java.io.File? = null
        runCatching {
            context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    // 1) settings.preferences_pb — kept RAW (unchanged behavior). We deliberately do NOT
                    //    strip the cookie/session here: that is a separate decision and the playback-cookie
                    //    issue is already fixed at validateStatus, so restore preserves the user's login.
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered()
                        .use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                            inputStream.copyTo(outputStream)
                        }
                    // 2) song.db — a CONSISTENT snapshot (VACUUM INTO on API 30+, checkpoint(TRUNCATE)+copy
                    //    fallback on API 26-29) so a write racing mid-backup can't produce a torn file.
                    dbSnapshot = snapshotDatabase(context)
                    FileInputStream(dbSnapshot!!.path).use { inputStream ->
                        outputStream.putNextEntry(ZipEntry(InternalDatabase.DB_NAME))
                        inputStream.copyTo(outputStream)
                    }
                    // 3) EQ / appearance SharedPreferences the classic backup used to drop, so EQ presets
                    //    survive a restore. NEVER the license (jr_license is not in the whitelist).
                    val sharedPrefsDir = java.io.File(context.dataDir, "shared_prefs")
                    EQ_APPEARANCE_PREFS.forEach { name ->
                        val f = java.io.File(sharedPrefsDir, "$name.xml")
                        if (f.exists()) {
                            outputStream.putNextEntry(ZipEntry("shared_prefs/$name.xml"))
                            f.inputStream().use { it.copyTo(outputStream) }
                        }
                    }
                }
            }
        }.also {
            // Always clean up the private snapshot (+ any journal siblings), success or fail.
            dbSnapshot?.let { s ->
                s.delete()
                java.io.File(s.path + "-wal").delete()
                java.io.File(s.path + "-shm").delete()
            }
        }.onSuccess {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.backup_create_success, Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            reportException(it)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
        }
    }

    /**
     * Consistent copy of song.db into a private cache file. Primary path: `VACUUM INTO` (a
     * transactionally-consistent snapshot; needs SQLite >= 3.27 = API 30+). Fallback for the older
     * bundled SQLite on API 26-29: a TRUNCATE checkpoint folds the WAL into the main file so it is a
     * complete, internally-consistent database, which we then copy. Never corrupts.
     */
    private fun snapshotDatabase(context: Context): java.io.File {
        val target = java.io.File(context.cacheDir, "song_snapshot_${System.nanoTime()}.db")
        // VACUUM INTO refuses to write if the target (or a journal sibling) already exists.
        target.delete()
        java.io.File(target.path + "-wal").delete()
        java.io.File(target.path + "-shm").delete()

        // Fold committed WAL frames into the main db first.
        runCatching { runBlocking(Dispatchers.IO) { database.checkpoint() } }

        val db = database.openHelper.writableDatabase
        val safePath = target.absolutePath.replace("'", "''")
        return try {
            db.execSQL("VACUUM INTO '$safePath'")
            Timber.tag("BACKUP").i("DB snapshot via VACUUM INTO")
            target
        } catch (e: Exception) {
            Timber.tag("BACKUP").w(e, "VACUUM INTO unavailable; falling back to checkpoint(TRUNCATE)+copy")
            target.delete()
            runCatching { db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
            java.io.File(db.path!!).copyTo(target, overwrite = true)
            target
        }
    }

    fun restore(context: Context, uri: Uri) {
        // Runs entirely off the main thread (zip decompress + validation + full-DB copy + restart) so a
        // large restore never ANRs. Strategy that "never corrupts / never silently fails":
        //   1. EXTRACT every entry to a private TEMP file — nothing live is touched yet.
        //   2. VALIDATE the backup DB (integrity_check + real schema open + byte-60 version gate) BEFORE
        //      going near the live song.db. A corrupt/incompatible backup is REJECTED here, with the live
        //      DB still open and the app fully usable.
        //   3. APPLY: snapshot the current db for ROLLBACK, then overwrite; on copy failure restore the
        //      old db and restart. Once the live DB is closed for overwrite we ALWAYS reach the restart
        //      (exitProcess) — never leaving the process alive with a closed/unusable DB.
        viewModelScope.launch(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        var dbTemp: java.io.File? = null
        var settingsTemp: java.io.File? = null
        val prefsTemps = linkedMapOf<String, java.io.File>() // "<name>.xml" -> extracted temp file
        // Hoisted so the outer onFailure can tell whether the live DB was already CLOSED for the swap.
        // If a throw escapes AFTER close() (e.g. restartApp itself), onFailure MUST still restart so the
        // process never keeps running with a closed, unusable database.
        var dbClosedForRestore = false
        runCatching {
            Timber.tag("RESTORE").i("Starting restore from URI: $uri")

            // ---- 1. EXTRACT every entry to a private temp file (nothing live is touched yet) ----
            context.applicationContext.contentResolver.openInputStream(uri)?.use { raw ->
                raw.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry }
                    while (entry != null) {
                        val name = entry.name
                        Timber.tag("RESTORE").i("Found zip entry: $name")
                        when {
                            name == SETTINGS_FILENAME -> {
                                settingsTemp = java.io.File(cacheDir, "restore_settings.tmp").also { t ->
                                    t.delete()
                                    java.io.FileOutputStream(t).use { inputStream.copyTo(it) }
                                }
                            }
                            name == InternalDatabase.DB_NAME -> {
                                dbTemp = java.io.File(cacheDir, "restore_song_db.tmp").also { t ->
                                    t.delete()
                                    java.io.FileOutputStream(t).use { inputStream.copyTo(it) }
                                }
                            }
                            name.startsWith("shared_prefs/") && name.endsWith(".xml") -> {
                                // Sanitised basename (blocks zip path traversal) + whitelist so we only
                                // ever write our own EQ/appearance prefs, never arbitrary files.
                                val base = name.substringAfterLast('/')
                                val prefName = base.removeSuffix(".xml")
                                if (prefName in EQ_APPEARANCE_PREFS) {
                                    prefsTemps[base] = java.io.File(cacheDir, "restore_pref_$prefName.tmp").also { f ->
                                        f.delete()
                                        java.io.FileOutputStream(f).use { inputStream.copyTo(it) }
                                    }
                                } else {
                                    Timber.tag("RESTORE").i("Skipping non-whitelisted pref: $name")
                                }
                            }
                            else -> Timber.tag("RESTORE").i("Skipping unexpected entry: $name")
                        }
                        entry = tryOrNull { inputStream.nextEntry }
                    }
                }
            } ?: throw java.io.IOException("Could not open the backup file (input stream was null)")

            if (dbTemp == null && settingsTemp == null && prefsTemps.isEmpty()) {
                throw RestoreException.Empty
            }

            // ---- 2. VALIDATE the DB BEFORE overwriting anything live ----
            dbTemp?.let { temp ->
                // 2a. header user_version gate (byte 60) vs the live schema version. A backup from a
                //     newer app (higher version) can't be opened by this build → reject.
                var backupVersion = 0
                runCatching {
                    java.io.RandomAccessFile(temp, "r").use { raf ->
                        raf.seek(60)
                        backupVersion = raf.readInt()
                    }
                }
                val currentDbVersion = runCatching {
                    database.openHelper.readableDatabase.version
                }.getOrDefault(Int.MAX_VALUE)
                if (!canRestoreDbVersion(backupVersion, currentDbVersion)) {
                    Timber.tag("RESTORE").e("Backup version ($backupVersion) > current ($currentDbVersion)")
                    throw RestoreException.Incompatible(backupVersion, currentDbVersion)
                }
                // 2b. Prove it is a usable DB (integrity_check + our schema) and clear the stale format
                //     cache. Throws RestoreException.Corrupt on a truncated/damaged file.
                validateAndPrepareRestoredDb(temp)
            }

            // ---- 3. APPLY. DB first (with rollback); once it's closed we ALWAYS restart. ----
            dbTemp?.let { temp ->
                val dbPath = database.openHelper.writableDatabase.path
                val dbFile = java.io.File(dbPath)
                val walFile = java.io.File("$dbPath-wal")
                val shmFile = java.io.File("$dbPath-shm")
                val bakFile = java.io.File("$dbPath.restore_bak")

                // Snapshot the CURRENT db (post-checkpoint, so it is complete) for rollback.
                bakFile.delete()
                runBlocking(Dispatchers.IO) { database.checkpoint() }
                if (dbFile.exists()) dbFile.copyTo(bakFile, overwrite = true)

                Timber.tag("RESTORE").i("Overwriting DB at path: $dbPath")
                database.close()
                dbClosedForRestore = true
                walFile.delete()
                shmFile.delete()

                try {
                    temp.copyTo(dbFile, overwrite = true)
                    // Guard against a TORN copy (e.g. disk filled mid-write): verify the just-written db
                    // is intact BEFORE dropping the rollback copy. If it's damaged, treat it exactly like a
                    // copy failure → roll back to the previous DB, so a partial/corrupt song.db can never
                    // survive (which would crash Room on next open until clear-data).
                    if (!dbFileIntegrityOk(dbFile)) {
                        throw java.io.IOException("Restored DB failed post-copy integrity check")
                    }
                    bakFile.delete() // success — drop the rollback copy
                    Timber.tag("RESTORE").i("DB overwrite complete")
                } catch (copyError: Exception) {
                    // ROLLBACK: restore the original db so we never leave a half-written/corrupt DB.
                    reportException(copyError)
                    Timber.tag("RESTORE").e(copyError, "DB overwrite failed — rolling back to previous DB")
                    runCatching { if (bakFile.exists()) bakFile.copyTo(dbFile, overwrite = true) }
                    walFile.delete(); shmFile.delete(); bakFile.delete()
                    kotlinx.coroutines.runBlocking(Dispatchers.Main) {
                        Toast.makeText(
                            context, context.getString(R.string.restore_failed_io), Toast.LENGTH_LONG,
                        ).show()
                    }
                    // The DB is CLOSED here. We MUST restart so Room reopens the (rolled-back) DB —
                    // otherwise the process would keep running with a closed, unusable database.
                    cleanupRestoreTemps(dbTemp, settingsTemp, prefsTemps)
                    restartApp(context) // -> exitProcess(0); guaranteed to run on this failure path
                }
            }

            // ---- 3b. settings + shared_prefs. If the DB was already closed these must NOT abort the
            //          restart, so each is best-effort. ----
            settingsTemp?.let { st ->
                runCatching {
                    // Unchanged behavior: overwrite settings.preferences_pb with the backup's copy. This
                    // PRESERVES the user's session exactly as before (no forced re-login); the stale
                    // visitorData + any restored proxy are corrected on next launch by
                    // App.reseedAfterRestoreIfNeeded, which keeps InnerTubeCookie/DataSyncId/account.
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).outputStream().use { os ->
                        st.inputStream().use { it.copyTo(os) }
                    }
                }.onFailure { Timber.tag("RESTORE").w(it, "settings restore failed (non-fatal)") }
            }
            if (prefsTemps.isNotEmpty()) {
                runCatching {
                    // EQ presets etc. live at <dataDir>/shared_prefs/<name>.xml. Overwriting the XML is
                    // picked up on the next launch (the process restarts below).
                    val spDir = java.io.File(context.dataDir, "shared_prefs").apply { mkdirs() }
                    prefsTemps.forEach { (fileName, temp) ->
                        java.io.File(spDir, fileName).outputStream().use { os ->
                            temp.inputStream().use { it.copyTo(os) }
                        }
                    }
                    Timber.tag("RESTORE").i("Restored ${prefsTemps.size} EQ/appearance pref file(s)")
                }.onFailure { Timber.tag("RESTORE").w(it, "shared_prefs restore failed (non-fatal)") }
            }

            cleanupRestoreTemps(dbTemp, settingsTemp, prefsTemps)

            // Force this version's one-time setup (App.initializeSettings) to re-run on next launch.
            // The restored settings file carries the old profile's init guards, which would otherwise
            // suppress newly seeded defaults/features ("new features don't appear after restore").
            runCatching { java.io.File(context.filesDir, POST_RESTORE_REINIT_FLAG).createNewFile() }

            Timber.tag("RESTORE").i("Restore complete — restarting (dbReplaced=$dbClosedForRestore)")
            restartApp(context)
        }.onFailure { e ->
            cleanupRestoreTemps(dbTemp, settingsTemp, prefsTemps)
            reportException(e)
            // If the DB was ALREADY closed for the swap, a throw that reached here (e.g. restartApp itself)
            // must NOT leave the process alive with a closed DB — force the restart so Room reopens the
            // restored/rolled-back file. The rollback path already handled disk state before this.
            if (dbClosedForRestore) {
                Timber.tag("RESTORE").w(e, "Failure after DB close — forcing restart so the DB reopens")
                restartApp(context)
            }
            // Otherwise it was thrown DURING extraction/validation — BEFORE the live DB was ever closed —
            // so the app is still fully usable. Clean up temps + show a CLEAR reason.
            Timber.tag("RESTORE").e(e, "Restore rejected before any live data was changed")
            val msg = when (e) {
                is RestoreException.Incompatible -> context.getString(R.string.restore_failed_incompatible)
                is RestoreException.Corrupt -> context.getString(R.string.restore_failed_corrupt)
                is RestoreException.Empty -> context.getString(R.string.restore_failed_invalid)
                else -> context.getString(R.string.restore_failed_io)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
        }
    }

    /**
     * Opens the extracted backup DB as a throwaway SQLite handle and asserts it is USABLE before we let
     * it replace the live song.db: `PRAGMA integrity_check` must return "ok" AND it must actually be an
     * Aura DB (the `song` table exists — the byte-60 version gate is checked separately by the caller).
     * Also best-effort clears the cached `format` rows so the first play after restore can't throw
     * ERROR_CODE_PARSING_CONTAINER_MALFORMED on a stale container (the stream is re-resolved anyway).
     * Throws [RestoreException.Corrupt] if the file is truncated/damaged/not a database.
     */
    /** Best-effort "is this a valid, non-truncated SQLite db" check. Returns false on ANY problem
     *  (open failure, damaged, integrity != ok) — never throws. Used to catch a torn post-copy db. */
    private fun dbFileIntegrityOk(file: java.io.File): Boolean = runCatching {
        android.database.sqlite.SQLiteDatabase.openDatabase(
            file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { handle ->
            handle.rawQuery("PRAGMA integrity_check", null).use { c ->
                c.moveToFirst() && c.getString(0) == "ok"
            }
        }
    }.getOrDefault(false)

    private fun validateAndPrepareRestoredDb(file: java.io.File) {
        val db = try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
            )
        } catch (e: android.database.sqlite.SQLiteException) {
            throw RestoreException.Corrupt("open failed: ${e.message}")
        }
        db.use { handle ->
            val integrity = try {
                handle.rawQuery("PRAGMA integrity_check", null).use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (e: android.database.sqlite.SQLiteException) {
                throw RestoreException.Corrupt("integrity_check failed: ${e.message}")
            }
            if (integrity != "ok") throw RestoreException.Corrupt("integrity_check=$integrity")

            val isAuraDb = try {
                handle.rawQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='song'", null,
                ).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
            } catch (e: android.database.sqlite.SQLiteException) {
                throw RestoreException.Corrupt("schema read failed: ${e.message}")
            }
            if (!isAuraDb) throw RestoreException.Corrupt("not an Aura database (no 'song' table)")

            // Best-effort: drop stale cached formats so first playback re-resolves cleanly.
            runCatching { handle.execSQL("DELETE FROM format") }
        }
        // Remove any journal siblings the throwaway open may have created so only the clean .db is copied.
        java.io.File(file.path + "-wal").delete()
        java.io.File(file.path + "-shm").delete()
        java.io.File(file.path + "-journal").delete()
    }

    /** Deletes all restore temp files. Safe to call more than once. */
    private fun cleanupRestoreTemps(
        dbTemp: java.io.File?,
        settingsTemp: java.io.File?,
        prefsTemps: Map<String, java.io.File>,
    ) {
        runCatching { dbTemp?.delete() }
        runCatching { settingsTemp?.delete() }
        prefsTemps.values.forEach { runCatching { it.delete() } }
    }

    /**
     * Tears down the running app and relaunches it fresh (so Room reopens the DB from disk). Returns
     * [Nothing] — the process is killed by [exitProcess]. This is the ONLY safe way out of restore once
     * the live DB has been closed, so it is always the last thing run on both the success and the
     * rollback paths.
     */
    private fun restartApp(context: Context): Nothing {
        context.stopService(Intent(context, MusicService::class.java))
        context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
        val restartIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(restartIntent)
        exitProcess(0)
    }

    /**
     * Selective export: only [playlistIds], optionally all followed artists and all EQ presets, into a
     * single mergeable `.json`. Read-only on the library, so it can't corrupt anything.
     */
    fun exportSelective(
        context: Context,
        uri: Uri,
        playlistIds: List<String>,
        includeArtists: Boolean,
        includePresets: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val pls = playlistIds.mapNotNull { pid ->
                    val pl = database.playlist(pid).first() ?: return@mapNotNull null
                    val songs = database.playlistSongs(pid).first()
                    iad1tya.echo.music.playlistimport.JrPlaylistImporter.JrPlaylistFile(
                        name = pl.playlist.name,
                        tracks = songs.map { ps ->
                            iad1tya.echo.music.playlistimport.JrPlaylistImporter.JrTrack(
                                title = ps.song.song.title,
                                artist = ps.song.artists.joinToString(", ") { it.name },
                                youtubeVideoId = ps.song.song.id,
                                thumbnailUrl = ps.song.song.thumbnailUrl,
                            )
                        },
                    )
                }
                val artists = if (includeArtists) {
                    database.artists(iad1tya.echo.music.constants.ArtistSortType.NAME, false).first()
                        .filter { it.artist.bookmarkedAt != null }
                        .map {
                            iad1tya.echo.music.playlistimport.SelectiveBackup.ArtistRec(
                                id = it.artist.id,
                                name = it.artist.name,
                                thumbnailUrl = it.artist.thumbnailUrl,
                                channelId = it.artist.channelId,
                            )
                        }
                } else emptyList()
                val presets = if (includePresets) eqProfileRepository.profiles.first() else emptyList()
                val bundle = iad1tya.echo.music.playlistimport.SelectiveBackup(
                    playlists = pls, artists = artists, eqPresets = presets,
                )
                val text = Json.encodeToString(
                    iad1tya.echo.music.playlistimport.SelectiveBackup.serializer(), bundle,
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Exportación creada", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                reportException(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No se pudo exportar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Selective import. Strictly ADDITIVE: playlists go through the tested [JrPlaylistImporter] (creates
     * new playlists), artists/presets are inserted (followed artists kept). Nothing is ever deleted, so
     * the worst case is a duplicate — never data loss.
     */
    fun importSelective(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().decodeToString()
                } ?: return@runCatching null
                val bundle = Json { ignoreUnknownKeys = true }.decodeFromString(
                    iad1tya.echo.music.playlistimport.SelectiveBackup.serializer(), text,
                )
                // Playlists: DIRECT import (no network re-resolution) so tracks actually land, offline too.
                bundle.playlists.forEach {
                    iad1tya.echo.music.playlistimport.JrPlaylistImporter.importDirect(database, it)
                }
                bundle.artists.forEach { a ->
                    val entity = ArtistEntity(
                        id = a.id,
                        name = a.name,
                        thumbnailUrl = a.thumbnailUrl,
                        channelId = a.channelId,
                        bookmarkedAt = java.time.LocalDateTime.now(),
                    )
                    database.query {
                        // insert() is IGNORE-on-conflict, so an artist row that ALREADY exists (e.g. you've
                        // played their songs, bookmarkedAt = null) would be skipped and never followed. update()
                        // after it sets bookmarkedAt on that existing row → the artist is actually re-followed.
                        insert(entity)
                        update(entity)
                    }
                }
                bundle.eqPresets.forEachIndexed { index, p ->
                    eqProfileRepository.saveProfile(
                        p.copy(
                            // Unique id per preset (index avoids same-millisecond collisions in this tight loop
                            // that would otherwise make one preset silently overwrite another).
                            id = "custom_${System.currentTimeMillis()}_${index}_${p.name.hashCode()}",
                            isActive = false,
                            isCustom = true,
                        ),
                    )
                }
                bundle
            }.onSuccess { b ->
                withContext(Dispatchers.Main) {
                    val msg = if (b == null) "No se pudo importar"
                    else "Importado: ${b.playlists.size} playlists, ${b.artists.size} artistas, ${b.eqPresets.size} presets"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                reportException(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No se pudo importar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // suspend + Dispatchers.IO so the whole-file SAF read (potentially a slow/cloud DocumentProvider)
    // never runs on the main thread. Callers invoke this from a coroutine and apply the result on Main.
    suspend fun previewCsvFile(context: Context, uri: Uri): CsvImportState = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                val rowsToPreview = lines.take(6).map { parseCsvLine(it) }
                val hasHeader = lines.isNotEmpty() && lines[0].contains(",")
                CsvImportState(
                    previewRows = rowsToPreview,
                    hasHeader = hasHeader,
                )
            }
        }.getOrElse {
            reportException(it)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No se pudo previsualizar el archivo CSV", Toast.LENGTH_SHORT).show()
            }
            null
        } ?: CsvImportState()
    }

    fun importPlaylistFromCsv(
        context: Context,
        uri: Uri,
        columnMapping: CsvImportState,
        onProgress: (Int) -> Unit = {},
        onLogUpdate: (List<ConvertedSongLog>) -> Unit = {},
    ): ArrayList<Song> {
        val songs = arrayListOf<Song>()
        val recentLogs = mutableListOf<ConvertedSongLog>()

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                val startIndex = if (columnMapping.hasHeader) 1 else 0
                val totalLines = lines.size - startIndex

                lines.drop(startIndex).forEachIndexed { index, line ->
                    val parts = parseCsvLine(line)

                    if (parts.isNotEmpty()) {
                        if (columnMapping.artistColumnIndex < parts.size && columnMapping.titleColumnIndex < parts.size) {
                            val title = parts[columnMapping.titleColumnIndex].trim()
                            val artistStr = parts[columnMapping.artistColumnIndex].trim()
                            val url = if (columnMapping.urlColumnIndex >= 0 && columnMapping.urlColumnIndex < parts.size) {
                                parts[columnMapping.urlColumnIndex].trim()
                            } else {
                                ""
                            }

                            if (title.isNotEmpty() && artistStr.isNotEmpty()) {
                                val artists = artistStr.split(";", ",").map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .map { ArtistEntity(id = "", name = it) }

                                val mockSong = Song(
                                    song = SongEntity(
                                        id = "",
                                        title = title,
                                    ),
                                    artists = artists,
                                )
                                songs.add(mockSong)

                                
                                val logEntry = ConvertedSongLog(
                                    title = title,
                                    artists = artists.joinToString(", ") { it.name },
                                )
                                recentLogs.add(0, logEntry)
                                if (recentLogs.size > 3) {
                                    recentLogs.removeAt(recentLogs.size - 1)
                                }
                                onLogUpdate(recentLogs.toList())
                            }
                        }
                    }

                    
                    val progress = ((index + 1) * 100) / totalLines
                    onProgress(progress.coerceIn(0, 99))
                }
            }
        }.onFailure {
            reportException(it)
            Timber.tag("CSV_IMPORT").e(it, "CSV import failed")
            Toast.makeText(
                context,
                "Failed to import CSV file",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return songs
    }

    fun importPlaylistFromCsv(context: Context, uri: Uri): ArrayList<Song> {
        
        return importPlaylistFromCsv(context, uri, CsvImportState())
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result.map { it.trim().trim('"') }
    }

    // suspend + Dispatchers.IO so the whole-file SAF read (potentially a slow/cloud DocumentProvider)
    // never runs on the main thread. Callers invoke this from a coroutine and apply the result on Main.
    suspend fun loadM3UOnline(
        context: Context,
        uri: Uri,
    ): ArrayList<Song> {
        val songs = ArrayList<Song>()

        withContext(Dispatchers.IO) {
            runCatching {
                context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val lines = stream.bufferedReader().readLines()
                    if (lines.first().startsWith("#EXTM3U")) {
                        lines.forEachIndexed { _, rawLine ->
                            if (rawLine.startsWith("#EXTINF:")) {

                                val artists =
                                    rawLine.substringAfter("#EXTINF:").substringAfter(',').substringBefore(" - ").split(';')
                                val title = rawLine.substringAfter("#EXTINF:").substringAfter(',').substringAfter(" - ")

                                val mockSong = Song(
                                    song = SongEntity(
                                        id = "",
                                        title = title,
                                    ),
                                    artists = artists.map { ArtistEntity("", it) },
                                )
                                songs.add(mockSong)

                            }
                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "No songs found. Invalid file, or perhaps no song matches were found.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        return songs
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"

        /**
         * EQ / appearance SharedPreferences (by file name) included in the backup ZIP under
         * `shared_prefs/<name>.xml` so EQ presets survive a restore (the classic backup dropped them).
         * NEVER includes `jr_license` — the license must never enter a backup.
         */
        private val EQ_APPEARANCE_PREFS = listOf(
            "nanosonic_eq_profiles", // EQProfileRepository — saved EQ presets
            "echo_eq_prefs",         // Axion EQ / MusicService — active EQ + favourites
            "eq_device_profiles",    // EqDeviceProfileStore — per-output-device EQ assignment
            "echomusic_settings",    // AppearanceSettings — appearance/UI prefs
        )

        /**
         * Marker file written after a successful restore. [iad1tya.echo.music.App.initializeSettings]
         * detects it on the next launch and clears the restored one-time init guards so this app
         * version's seeded defaults/features re-apply, then deletes it.
         */
        const val POST_RESTORE_REINIT_FLAG = "post_restore_reseed.flag"
    }
}
