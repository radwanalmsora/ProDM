package com.prodm.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prodm.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val b = vm.browser
    val url by b.currentUrl.collectAsStateWithLifecycle()
    val progress by b.progress.collectAsStateWithLifecycle()
    val canBack by b.canBack.collectAsStateWithLifecycle()
    val canForward by b.canForward.collectAsStateWithLifecycle()
    val media by b.media.collectAsStateWithLifecycle()
    var text by remember(url) { mutableStateOf(url) }
    var sheet by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    BackHandler(enabled = canBack) { b.webView.goBack() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { b.webView.goBack() }, enabled = canBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
            }
            IconButton(onClick = { b.webView.goForward() }, enabled = canForward) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text(stringResource(R.string.browser_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { b.navigate(text); focus.clearFocus() })
            )
            IconButton(onClick = { if (progress in 1..99) b.webView.stopLoading() else b.webView.reload() }) {
                Icon(if (progress in 1..99) Icons.Rounded.Close else Icons.Rounded.Refresh, null)
            }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    b.attach(ctx)
                    (b.webView.parent as? android.view.ViewGroup)?.removeView(b.webView)
                    b.webView
                },
                onRelease = { b.detach() }
            )
            if (media.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { sheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    icon = { Icon(Icons.Rounded.Download, null) },
                    text = { Text(stringResource(R.string.media_found, media.size)) }
                )
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.media_sheet_title), style = MaterialTheme.typography.titleLarge)
                media.take(20).forEach { u ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.fileLabel(u), modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = {
                            vm.openAdd(b.requestFor(u))
                            sheet = false
                        }) { Icon(Icons.Rounded.Download, null) }
                    }
                }
            }
        }
    }
}
