package com.prodm.app.core

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.prodm.app.MainActivity
import com.prodm.app.R
import com.prodm.app.data.DownloadDao
import com.prodm.app.data.DownloadItem
import com.prodm.app.data.SegmentRow
import com.prodm.app.data.SettingsRepo
import com.prodm.app.data.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class Live(val downloaded: Long, val total: Long, val speed: Long)

private class RtSeg(val idx: Int, val start: Long, val end: Long, val done: AtomicLong)

private class Probe(val total: Long, val ranges: Boolean, val mime: String?, val fileName: String?)

/** Global speed limiter shared by all connections. */
class Limiter(private val limit: () -> Long) {
    private var next = 0L
    private val lock = Any()

    suspend fun acquire(n: Int) {
        val l = limit()
        if (l <= 0) return
        val wait = synchronized(lock) {
            val now = System.nanoTime()
            if (next < now) next = now
            val w = next - now
            next += n * 1_000_000_000L / l
            w
        }
        if (wait > 1_000_000L) delay(wait / 1_000_000L)
    }
}

class DownloadCore(
    private val ctx: Context,
    private val dao: DownloadDao,
    private val settings: SettingsRepo
) {
    companion object {
        const val CH_PROGRESS = "progress"
        const val CH_DONE = "done"
        const val MIN_SEG = 1L shl 20
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val pumpMutex = Mutex()

    private val _live = MutableStateFlow<Map<Long, Live>>(emptyMap())
    val live: StateFlow<Map<Long, Live>> = _live.asStateFlow()

    private val _active = MutableStateFlow<Set<Long>>(emptySet())
    val active: StateFlow<Set<Long>> = _active.asStateFlow()

    private val limiter = Limiter { settings.current.value.speedLimitKb * 1024L }
    private val cm: ConnectivityManager = ctx.getSystemService(ConnectivityManager::class.java)
    private val nm = NotificationManagerCompat.from(ctx)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun init() {
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CH_PROGRESS, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(ctx.getString(R.string.channel_progress)).build()
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CH_DONE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(ctx.getString(R.string.channel_done)).build()
        )
        registerNetworkCallback()
        scope.launch {
            dao.setStatusWhere(listOf(Status.DOWNLOADING, Status.FINALIZING), Status.QUEUED)
            pumpInternal()
        }
    }

    // ---------------------------------------------------------------- public API

    suspend fun add(
        url: String,
        name: String?,
        scheduled: Boolean,
        referer: String?,
        ua: String?,
        cookie: String?
    ): Long {
        val clean = url.trim()
        dao.openByUrl(clean)?.let { existing ->
            if (!scheduled) resume(existing.id)
            return existing.id
        }
        val fname = sanitize(if (name.isNullOrBlank()) guessFileName(clean) else name)
        val id = dao.insert(
            DownloadItem(
                url = clean,
                fileName = fname,
                autoName = name.isNullOrBlank(),
                category = categoryFor(fname, null),
                status = if (scheduled) Status.SCHEDULED else Status.QUEUED,
                referer = referer,
                userAgent = ua,
                cookie = cookie
            )
        )
        if (!scheduled) pumpInternal()
        return id
    }

    fun pump() {
        scope.launch { pumpInternal() }
    }

    fun resume(id: Long) {
        scope.launch {
            val it = dao.get(id) ?: return@launch
            if (it.status != Status.COMPLETED && it.status != Status.DOWNLOADING && it.status != Status.FINALIZING) {
                dao.setStatus(id, Status.QUEUED, null)
                pumpInternal()
            }
        }
    }

    fun pause(id: Long) {
        scope.launch { stopOne(id, Status.PAUSED) }
    }

    fun pauseAll() {
        scope.launch {
            dao.byStatus(listOf(Status.QUEUED, Status.DOWNLOADING)).forEach { stopOne(it.id, Status.PAUSED) }
        }
    }

    fun resumeAll() {
        scope.launch {
            dao.setStatusWhere(listOf(Status.PAUSED, Status.FAILED, Status.SCHEDULED), Status.QUEUED)
            pumpInternal()
        }
    }

    suspend fun startScheduledNow() {
        dao.setStatusWhere(listOf(Status.SCHEDULED), Status.QUEUED)
        pumpInternal()
    }

    fun redownload(id: Long) {
        scope.launch {
            jobs[id]?.cancelAndJoin()
            tempFile(id).delete()
            dao.clearSegments(id)
            dao.resetForRedownload(id)
            pumpInternal()
        }
    }

    fun delete(id: Long, withFile: Boolean) {
        scope.launch {
            jobs[id]?.cancelAndJoin()
            val it = dao.get(id)
            dao.delete(id)
            dao.clearSegments(id)
            tempFile(id).delete()
            if (withFile) it?.finalUri?.let { u ->
                runCatching { ctx.contentResolver.delete(Uri.parse(u), null, null) }
            }
        }
    }

    fun clearCompleted() {
        scope.launch {
            dao.completed().forEach { dao.delete(it.id) }
        }
    }

    // ---------------------------------------------------------------- queue

    private suspend fun stopOne(id: Long, to: String) {
        val cur = dao.get(id) ?: return
        if (cur.status == Status.DOWNLOADING) {
            dao.setStatus(id, to, null)
            jobs[id]?.cancel()
        } else if (cur.status == Status.QUEUED && to == Status.PAUSED) {
            dao.setStatus(id, to, null)
        }
    }

    private suspend fun pumpInternal() = pumpMutex.withLock {
        val s = settings.snapshot()
        if (!online()) return@withLock
        if (s.wifiOnly && !unmetered()) return@withLock
        val free = s.maxConcurrent - jobs.size
        if (free <= 0) return@withLock
        val queued = dao.byStatus(listOf(Status.QUEUED))
            .sortedBy { it.createdAt }
            .filter { !jobs.containsKey(it.id) }
            .take(free)
        for (q in queued) launchJob(q.id)
    }

    private fun launchJob(id: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                runJob(id)
            } finally {
                jobs.remove(id)
                _active.update { it - id }
                _live.update { it - id }
                pump()
            }
        }
        if (jobs.putIfAbsent(id, job) != null) {
            job.cancel(); return
        }
        _active.update { it + id }
        startService()
        job.start()
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        } catch (e: Exception) {
            // background start restrictions: downloads continue while process lives
        }
    }

    private fun caps(): NetworkCapabilities? = cm.getNetworkCapabilities(cm.activeNetwork)
    private fun online() = caps()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    private fun unmetered() = caps()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

    private fun registerNetworkCallback() {
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        dao.requeueAutoRetry()
                        pumpInternal()
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    scope.launch {
                        val s = settings.snapshot()
                        val metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        if (s.wifiOnly && metered) {
                            jobs.keys.toList().forEach { stopOne(it, Status.QUEUED) }
                        } else {
                            pumpInternal()
                        }
                    }
                }
            })
        } catch (e: Exception) {
        }
    }

    // ---------------------------------------------------------------- download

    private fun tempFile(id: Long): File {
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "parts")
        return File(dir, "$id.part")
    }

    private suspend fun runJob(id: Long) {
        dao.setStatus(id, Status.DOWNLOADING, null)
        var attempt = 0
        while (true) {
            try {
                download(id)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (!currentCoroutineContext().isActive) throw CancellationException()
                attempt++
                if (attempt > 6) {
                    fail(id, e.message ?: "Network error", true)
                    return
                }
                delay(1500L * attempt)
            } catch (e: Exception) {
                fail(id, e.message ?: e.javaClass.simpleName, false)
                return
            }
        }
    }

    private suspend fun fail(id: Long, msg: String, auto: Boolean) {
        dao.setFailed(id, msg, auto)
        dao.get(id)?.let { notifyDone(it, false) }
    }

    private fun statusError(code: Int): Exception =
        if (code >= 500 || code == 429 || code == 408) IOException("HTTP $code")
        else IllegalStateException("HTTP $code")

    private fun baseRequest(item: DownloadItem): Request.Builder {
        val rb = Request.Builder().url(item.url).header("Accept-Encoding", "identity")
        rb.header("User-Agent", item.userAgent ?: DEFAULT_UA)
        item.referer?.let { rb.header("Referer", it) }
        item.cookie?.let { rb.header("Cookie", it) }
        return rb
    }

    private fun probe(item: DownloadItem): Probe {
        val req = baseRequest(item).header("Range", "bytes=0-0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw statusError(resp.code)
            val mime = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
            val name = parseContentDisposition(resp.header("Content-Disposition"))
                ?: resp.request.url.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }
            if (resp.code == 206) {
                val total = resp.header("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull() ?: -1L
                return Probe(total, total > 0, mime, name)
            }
            val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            return Probe(len, false, mime, name)
        }
    }

    private suspend fun download(id: Long) {
        var item = dao.get(id) ?: return
        val tmp = tempFile(id)
        var rows = dao.segments(id)

        if (rows.isEmpty() || !tmp.exists()) {
            dao.clearSegments(id)
            tmp.parentFile?.mkdirs()
            if (tmp.exists()) tmp.delete()
            val info = probe(item)

            var name = item.fileName
            if (item.autoName) {
                info.fileName?.takeIf { it.isNotBlank() }?.let { name = sanitize(it) }
                if (!name.contains('.')) {
                    val ext = info.mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                    if (ext != null) name = "$name.$ext"
                }
            }
            dao.updateProbe(id, info.total, info.ranges, info.mime, name, categoryFor(name, info.mime))
            item = dao.get(id) ?: return

            val conns = settings.snapshot().connections.coerceIn(1, 16)
            val n = if (info.ranges && info.total > 0)
                minOf(conns.toLong(), maxOf(1L, info.total / MIN_SEG)).toInt() else 1
            rows = buildSegments(id, info.total, n)
            RandomAccessFile(tmp, "rw").use { if (info.total > 0) it.setLength(info.total) }
            dao.upsertSegments(rows)
            dao.setProgress(id, 0)
        }

        if (!item.acceptRanges && rows.any { it.downloaded > 0 }) {
            rows = rows.map { it.copy(downloaded = 0) }
            RandomAccessFile(tmp, "rw").use { it.setLength(0) }
        }

        val total = item.totalBytes
        val segs = rows.map { RtSeg(it.idx, it.startByte, it.endByte, AtomicLong(it.downloaded)) }

        suspend fun persist() {
            dao.setProgress(id, segs.sumOf { it.done.get() })
            dao.upsertSegments(segs.map { SegmentRow(id, it.idx, it.start, it.end, it.done.get()) })
        }

        try {
            coroutineScope {
                var lastBytes = segs.sumOf { it.done.get() }
                var lastTime = System.nanoTime()
                var speed = 0L
                var tick = 0
                val reporter = launch {
                    while (isActive) {
                        delay(500)
                        val now = System.nanoTime()
                        val bytes = segs.sumOf { it.done.get() }
                        val inst = (bytes - lastBytes) * 1_000_000_000L / maxOf(1L, now - lastTime)
                        speed = if (speed == 0L) inst else (speed * 0.6 + inst * 0.4).toLong()
                        lastBytes = bytes
                        lastTime = now
                        _live.update { it + (id to Live(bytes, total, speed)) }
                        tick++
                        if (tick % 2 == 0) persist()
                    }
                }
                try {
                    segs.filter { s -> !(s.end >= 0 && s.done.get() >= s.end - s.start + 1) }
                        .map { s -> async { fetchSegment(item, tmp, s) } }
                        .awaitAll()
                } finally {
                    reporter.cancel()
                }
            }
        } finally {
            withContext(NonCancellable) { persist() }
        }

        val finalBytes = segs.sumOf { it.done.get() }
        if (total > 0 && finalBytes < total) throw IOException("Incomplete download")

        dao.setStatus(id, Status.FINALIZING, null)
        _live.update { it + (id to Live(finalBytes, if (total > 0) total else finalBytes, 0)) }
        val finalItem = dao.get(id) ?: return
        val uri = publish(finalItem, tmp)
        dao.complete(id, uri.toString(), System.currentTimeMillis(), finalBytes)
        tmp.delete()
        dao.clearSegments(id)
        dao.get(id)?.let { notifyDone(it, true) }
    }

    private fun buildSegments(id: Long, total: Long, n: Int): List<SegmentRow> {
        if (total <= 0) return listOf(SegmentRow(id, 0, 0, -1, 0))
        val chunk = total / n
        return (0 until n).map { i ->
            val start = i * chunk
            val end = if (i == n - 1) total - 1 else start + chunk - 1
            SegmentRow(id, i, start, end, 0)
        }
    }

    private suspend fun fetchSegment(item: DownloadItem, tmp: File, seg: RtSeg) {
        val from = seg.start + seg.done.get()
        val rb = baseRequest(item)
        if (item.acceptRanges) {
            rb.header("Range", "bytes=$from-${if (seg.end >= 0) seg.end.toString() else ""}")
        }
        val call = client.newCall(rb.build())
        val handle = currentCoroutineContext()[Job]!!.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw statusError(resp.code)
                if (item.acceptRanges && resp.code != 206) throw IOException("Server did not honor Range")
                val body = resp.body ?: throw IOException("Empty response")
                RandomAccessFile(tmp, "rw").use { raf ->
                    raf.seek(from)
                    val input = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val want = if (seg.end >= 0)
                            minOf(buf.size.toLong(), seg.end - (seg.start + seg.done.get()) + 1).toInt()
                        else buf.size
                        if (want <= 0) break
                        val n = input.read(buf, 0, want)
                        if (n < 0) break
                        limiter.acquire(n)
                        raf.write(buf, 0, n)
                        seg.done.addAndGet(n.toLong())
                    }
                }
            }
            if (seg.end >= 0 && seg.done.get() < seg.end - seg.start + 1) {
                throw IOException("Connection closed early")
            }
        } finally {
            handle.dispose()
        }
    }

    private fun publish(item: DownloadItem, tmp: File): Uri {
        val resolver = ctx.contentResolver
        val mime = guessMime(item.fileName) ?: item.mimeType ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, item.fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/ProDM/${folderFor(item.category)}"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create output file")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open output")
            out.use { o -> tmp.inputStream().use { it.copyTo(o, 256 * 1024) } }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    @SuppressLint("MissingPermission")
    private fun notifyDone(item: DownloadItem, ok: Boolean) {
        if (!nm.areNotificationsEnabled()) return
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, item.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(item.fileName)
            .setContentText(ctx.getString(if (ok) R.string.notif_done else R.string.notif_failed))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(1000 + item.id.toInt(), n) }
    }
}
