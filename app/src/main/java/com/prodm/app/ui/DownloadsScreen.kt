package com.prodm.app.ui

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prodm.app.R
import com.prodm.app.core.Cat
import com.prodm.app.core.Live
import com.prodm.app.core.fmtBytes
import com.prodm.app.core.fmtEta
import com.prodm.app.core.fmtSpeed
import com.prodm.app.data.DownloadItem
import com.prodm.app.data.Status

class DownloadActions(
    val pause: (Long) -> Unit,
    val resume: (Long) -> Unit,
    val open: (DownloadItem) -> Unit,
    val copy: (DownloadItem) -> Unit,
    val delete: (DownloadItem) -> Unit,
    val redownload: (Long) -> Unit
)

fun catIcon(c: String): ImageVector = when (c) {
    Cat.VIDEO -> Icons.Rounded.Videocam
    Cat.AUDIO -> Icons.Rounded.MusicNote
    Cat.IMAGE -> Icons.Rounded.Image
    Cat.DOC -> Icons.Rounded.Description
    Cat.ARCHIVE -> Icons.Rounded.Archive
    Cat.APP -> Icons.Rounded.Android
    else -> Icons.Rounded.AttachFile
}

fun catColor(c: String): Color = when (c) {
    Cat.VIDEO -> Color(0xFFEF4444)
    Cat.AUDIO -> Color(0xFFF59E0B)
    Cat.IMAGE -> Color(0xFF10B981)
    Cat.DOC -> Color(0xFF3B82F6)
    Cat.ARCHIVE -> Color(0xFFA855F7)
    Cat.APP -> Color(0xFF22C55E)
    else -> Color(0xFF6B7280)
}

private val ACTIVE_SET = setOf(Status.DOWNLOADING, Status.QUEUED, Status.FINALIZING)
private val PAUSED_SET = setOf(Status.PAUSED, Status.SCHEDULED)

@Composable
fun DownloadsScreen(
    items: List<DownloadItem>,
    live: Map<Long, Live>,
    filter: Int,
    onFilter: (Int) -> Unit,
    query: String,
    actions: DownloadActions,
    modifier: Modifier = Modifier
) {
    val filtered = items.filter {
        val q = query.isBlank() || it.fileName.contains(query, true) || it.url.contains(query, true)
        q && when (filter) {
            1 -> it.status in ACTIVE_SET
            2 -> it.status in PAUSED_SET
            3 -> it.status == Status.COMPLETED
            4 -> it.status == Status.FAILED
            else -> true
        }
    }
    val speed = live.values.sumOf { it.speed }
    val active = items.count { it.status == Status.DOWNLOADING || it.status == Status.FINALIZING }
    val waiting = items.count { it.status in setOf(Status.QUEUED, Status.PAUSED, Status.SCHEDULED) }
    val done = items.count { it.status == Status.COMPLETED }
    val labels = listOf(
        R.string.filter_all, R.string.filter_active, R.string.filter_paused,
        R.string.filter_completed, R.string.filter_failed
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "summary") { SummaryCard(speed, active, waiting, done) }
        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(labels) { i, l ->
                    FilterChip(selected = filter == i, onClick = { onFilter(i) }, label = { Text(stringResource(l)) })
                }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty") { EmptyState() }
        }
        items(filtered, key = { it.id }) { d ->
            DownloadCard(d, live[d.id], actions)
        }
    }
}

@Composable
private fun SummaryCard(speed: Long, active: Int, waiting: Int, done: Int) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
            .padding(22.dp)
    ) {
        Column {
            Text(
                stringResource(R.string.total_speed),
                color = cs.onPrimary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(fmtSpeed(speed), color = cs.onPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(active, R.string.stat_active)
                Stat(waiting, R.string.stat_waiting)
                Stat(done, R.string.stat_done)
            }
        }
    }
}

@Composable
private fun Stat(n: Int, label: Int) {
    val c = MaterialTheme.colorScheme.onPrimary
    Column {
        Text("$n", color = c, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(label), color = c.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.CloudDownload, null,
            Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.empty_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DownloadCard(item: DownloadItem, live: Live?, a: DownloadActions) {
    val cs = MaterialTheme.colorScheme
    val st = item.status
    val done = live?.downloaded ?: item.downloadedBytes
    val total = item.totalBytes
    val frac = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val running = st == Status.DOWNLOADING
    var menu by remember { mutableStateOf(false) }
    val tint = catColor(item.category)

    val statusLabel = stringResource(
        when (st) {
            Status.DOWNLOADING -> R.string.status_downloading
            Status.QUEUED -> R.string.status_queued
            Status.PAUSED -> R.string.status_paused
            Status.COMPLETED -> R.string.status_completed
            Status.FAILED -> R.string.status_failed
            Status.SCHEDULED -> R.string.status_scheduled
            else -> R.string.status_finalizing
        }
    )
    val statusColor = when (st) {
        Status.COMPLETED -> Color(0xFF16A34A)
        Status.FAILED -> cs.error
        Status.DOWNLOADING, Status.FINALIZING -> cs.primary
        else -> cs.onSurfaceVariant
    }
    val speed = live?.speed ?: 0L
    val eta = if (running && speed > 0 && total > 0) fmtEta((total - done) / speed) else null
    val etaText = if (eta != null) stringResource(R.string.eta, eta) else null

    val line1 = when {
        running -> fmtSpeed(speed) + (if (etaText != null) "  ·  $etaText" else "")
        st == Status.FAILED && !item.error.isNullOrBlank() -> "$statusLabel · ${item.error}"
        else -> statusLabel
    }
    val line2 = when {
        st == Status.COMPLETED -> fmtBytes(item.totalBytes)
        total > 0 -> "${fmtBytes(done)} / ${fmtBytes(total)}  (${(frac * 100).toInt()}%)"
        else -> fmtBytes(done)
    }

    Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clickable { if (st == Status.COMPLETED) a.open(item) }
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(catIcon(item.category), null, tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                )
                Text(
                    line1, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = statusColor
                )
                if (st != Status.COMPLETED) {
                    Spacer(Modifier.height(8.dp))
                    val m = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                    if (running && total <= 0) {
                        LinearProgressIndicator(modifier = m)
                    } else {
                        LinearProgressIndicator(
                            progress = { frac }, modifier = m,
                            trackColor = cs.surfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    line2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
                )
            }
            when (st) {
                Status.DOWNLOADING, Status.QUEUED -> IconButton(onClick = { a.pause(item.id) }) {
                    Icon(Icons.Rounded.Pause, stringResource(R.string.action_pause))
                }
                Status.PAUSED, Status.FAILED, Status.SCHEDULED -> IconButton(onClick = { a.resume(item.id) }) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.action_resume))
                }
                Status.COMPLETED -> IconButton(onClick = { a.open(item) }) {
                    Icon(Icons.Rounded.OpenInNew, stringResource(R.string.action_open))
                }
                else -> Spacer(Modifier.width(8.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_link)) },
                        onClick = { menu = false; a.copy(item) })
                    if (st == Status.COMPLETED || st == Status.FAILED) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_redownload)) },
                            onClick = { menu = false; a.redownload(item.id) })
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menu = false; a.delete(item) })
                }
            }
        }
    }
}

@Composable
fun AddDownloadDialog(
    req: NewDownload,
    onDismiss: () -> Unit,
    onSubmit: (text: String, name: String, later: Boolean) -> Boolean
) {
    var text by remember { mutableStateOf(req.url) }
    var name by remember { mutableStateOf(req.fileName) }
    var later by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    label = { Text(stringResource(R.string.add_hint)) },
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.invalid_link)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        IconButton(onClick = {
                            val cm = ctx.getSystemService(ClipboardManager::class.java)
                            val clip = cm?.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                clip.getItemAt(0).coerceToText(ctx)?.toString()?.let { text = it; error = false }
                            }
                        }) { Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste)) }
                    }
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.add_filename)) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = later, onCheckedChange = { later = it })
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.add_later), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onSubmit(text, name, later)) onDismiss() else error = true }) {
                Text(stringResource(R.string.add_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun DeleteDialog(item: DownloadItem, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var withFile by remember { mutableStateOf(item.status == Status.COMPLETED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Column {
                Text(item.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.status == Status.COMPLETED) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { withFile = !withFile },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = withFile, onCheckedChange = { withFile = it })
                        Text(stringResource(R.string.delete_with_file), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(withFile) }) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
