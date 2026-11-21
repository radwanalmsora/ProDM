package com.prodm.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

object Status {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val SCHEDULED = "SCHEDULED"
    const val FINALIZING = "FINALIZING"
}

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val autoName: Boolean = true,
    val category: String = "OTHER",
    val mimeType: String? = null,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: String = Status.QUEUED,
    val error: String? = null,
    val autoRetry: Boolean = false,
    val acceptRanges: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val finalUri: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val cookie: String? = null
)

@Entity(tableName = "segments", primaryKeys = ["downloadId", "idx"])
data class SegmentRow(
    val downloadId: Long,
    val idx: Int,
    val startByte: Long,
    val endByte: Long,
    val downloaded: Long
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadItem?

    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    suspend fun byStatus(statuses: List<String>): List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE url = :url AND status != 'COMPLETED' LIMIT 1")
    suspend fun openByUrl(url: String): DownloadItem?

    @Insert
    suspend fun insert(item: DownloadItem): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String?)

    @Query("UPDATE downloads SET status = :to WHERE status IN (:from)")
    suspend fun setStatusWhere(from: List<String>, to: String)

    @Query("UPDATE downloads SET status = 'QUEUED', autoRetry = 0 WHERE status = 'FAILED' AND autoRetry = 1")
    suspend fun requeueAutoRetry()

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun setProgress(id: Long, bytes: Long)

    @Query("UPDATE downloads SET totalBytes = :total, acceptRanges = :ranges, mimeType = :mime, fileName = :name, category = :cat WHERE id = :id")
    suspend fun updateProbe(id: Long, total: Long, ranges: Boolean, mime: String?, name: String, cat: String)

    @Query("UPDATE downloads SET status = 'COMPLETED', finalUri = :uri, completedAt = :t, downloadedBytes = :bytes, totalBytes = :bytes, error = NULL WHERE id = :id")
    suspend fun complete(id: Long, uri: String, t: Long, bytes: Long)

    @Query("UPDATE downloads SET status = 'FAILED', error = :error, autoRetry = :auto WHERE id = :id")
    suspend fun setFailed(id: Long, error: String?, auto: Boolean)

    @Query("UPDATE downloads SET status = 'QUEUED', downloadedBytes = 0, finalUri = NULL, completedAt = 0, error = NULL, totalBytes = -1 WHERE id = :id")
    suspend fun resetForRedownload(id: Long)

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun completed(): List<DownloadItem>

    @Query("SELECT * FROM segments WHERE downloadId = :id ORDER BY idx")
    suspend fun segments(id: Long): List<SegmentRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegments(rows: List<SegmentRow>)

    @Query("DELETE FROM segments WHERE downloadId = :id")
    suspend fun clearSegments(id: Long)
}

@Database(entities = [DownloadItem::class, SegmentRow::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): DownloadDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "prodm.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
