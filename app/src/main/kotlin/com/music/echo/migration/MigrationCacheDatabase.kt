package iad1tya.echo.music.migration

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aura.migration.db.MatchCacheDao
import com.aura.migration.db.MatchCacheEntity

/**
 * A SEPARATE, tiny Room database that owns ONLY the migration module's `match_cache` table.
 *
 * Deliberately NOT part of MusicDatabase (which is at v39 with strict, Hilt-injected migration rules and
 * a documented "migration not found" crash landmine). Keeping this standalone means the resolver cache can
 * never trigger a MusicDatabase migration, and a schema bump here is safe to drop destructively — it is a
 * pure performance cache (a match can always be recomputed by searching again), never user data.
 */
@Database(
    entities = [MatchCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MigrationCacheDatabase : RoomDatabase() {
    abstract fun matchCacheDao(): MatchCacheDao

    companion object {
        const val DB_NAME = "migration_match_cache.db"
    }
}
