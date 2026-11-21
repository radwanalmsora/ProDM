package com.prodm.app.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import com.prodm.app.data.DownloadItem
import java.net.URLDecoder
import java.util.Locale

object Cat {
    const val VIDEO = "VIDEO"
    const val AUDIO = "AUDIO"
    const val IMAGE = "IMAGE"
    const val DOC = "DOC"
    const val ARCHIVE = "ARCHIVE"
    const val APP = "APP"
    const val OTHER = "OTHER"
}

const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

val URL_REGEX = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "3gp", "m4v", "ts", "wmv")
private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic")
private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv", "rtf")
private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso")
private val APP_EXT = setOf("apk", "xapk", "apks", "aab")

fun categoryFor(name: String, mime: String?): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext in VIDEO_EXT -> Cat.VIDEO
        ext in AUDIO_EXT -> Cat.AUDIO
        ext in IMAGE_EXT -> Cat.IMAGE
        ext in DOC_EXT -> Cat.DOC
        ext in ARCHIVE_EXT -> Cat.ARCHIVE
        ext in APP_EXT -> Cat.APP
        mime?.startsWith("video/") == true -> Cat.VIDEO
        mime?.startsWith("audio/") == true -> Cat.AUDIO
        mime?.startsWith("image/") == true -> Cat.IMAGE
        else -> Cat.OTHER
    }
}

fun folderFor(cat: String): String = when (cat) {
    Cat.VIDEO -> "Videos"
    Cat.AUDIO -> "Music"
    Cat.IMAGE -> "Images"
    Cat.DOC -> "Documents"
    Cat.ARCHIVE -> "Archives"
    Cat.APP -> "Apps"
    else -> "Other"
}

fun guessMime(name: String): String? =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

fun sanitize(name: String): String {
    val s = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim()
    return s.take(120).ifBlank { "download_${System.currentTimeMillis()}" }
}

fun guessFileName(url: String): String {
    val seg = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
    val dec = seg?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    return sanitize(if (dec.isNullOrBlank()) "download_${System.currentTimeMillis()}" else dec)
}

fun parseContentDisposition(h: String?): String? {
    if (h == null) return null
    Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(h)?.let { m ->
        val cs = m.groupValues[1].ifBlank { "UTF-8" }
        val v = m.groupValues[2].trim().trim('"')
        return runCatching { URLDecoder.decode(v, cs) }.getOrNull()
    }
    Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(h)?.let {
        return it.groupValues[1].trim()
    }
    return null
}

fun fmtBytes(b: Long): String {
    if (b < 0) return "—"
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024; i++
    }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

fun fmtSpeed(bps: Long): String = fmtBytes(bps.coerceAtLeast(0)) + "/s"

fun fmtEta(sec: Long): String {
    if (sec < 0) return "--"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun openFile(ctx: Context, item: DownloadItem): Boolean {
    val uri = item.finalUri?.let { Uri.parse(it) } ?: return false
    val mime = guessMime(item.fileName) ?: item.mimeType ?: "*/*"
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        ctx.startActivity(i); true
    } catch (e: Exception) {
        false
    }
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("link", text))
}
