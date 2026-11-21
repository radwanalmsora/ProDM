package com.prodm.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prodm.app.R
import com.prodm.app.core.copyText
import com.prodm.app.core.openFile
import com.prodm.app.data.DownloadItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }

    val items by vm.items.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val addReq by vm.addDialog.collectAsStateWithLifecycle()
    val clip by vm.clipboardUrl.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    val msgClip = stringResource(R.string.clipboard_found)
    val actClip = stringResource(R.string.clipboard_action)
    LaunchedEffect(clip) {
        clip?.let { u ->
            val r = snack.showSnackbar(msgClip, actClip, withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) vm.openAdd(NewDownload(url = u))
            vm.clipboardUrl.value = null
        }
    }

    val linkCopied = stringResource(R.string.link_copied)
    val noApp = stringResource(R.string.no_app_open)
    val actions = DownloadActions(
        pause = { vm.pause(it) },
        resume = { vm.resume(it) },
        open = { d -> if (!openFile(ctx, d)) Toast.makeText(ctx, noApp, Toast.LENGTH_SHORT).show() },
        copy = { d -> copyText(ctx, d.url); Toast.makeText(ctx, linkCopied, Toast.LENGTH_SHORT).show() },
        delete = { confirmDelete = it },
        redownload = { vm.redownload(it) }
    )

    Scaffold(
        topBar = {
            when (tab) {
                0 -> TopAppBar(
                    title = {
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, null)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_pause_all)) },
                                    onClick = { menu = false; vm.pauseAll() })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_resume_all)) },
                                    onClick = { menu = false; vm.resumeAll() })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_clear_completed)) },
                                    onClick = { menu = false; vm.clearCompleted() })
                            }
                        }
                    }
                )
                2 -> TopAppBar(
                    title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) }
                )
                else -> {}
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.CloudDownload, null) },
                    label = { Text(stringResource(R.string.tab_downloads)) })
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Public, null) },
                    label = { Text(stringResource(R.string.tab_browser)) })
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text(stringResource(R.string.tab_settings)) })
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { vm.openAdd(NewDownload()) }) { Icon(Icons.Rounded.Add, null) }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> DownloadsScreen(items, live, filter, { filter = it }, query, actions)
                1 -> BrowserScreen(vm)
                else -> SettingsScreen(settings, vm)
            }
        }
    }

    addReq?.let { req ->
        AddDownloadDialog(
            req = req,
            onDismiss = { vm.addDialog.value = null },
            onSubmit = { text, name, later ->
                val n = vm.submit(req, text, name, later)
                if (n > 0) {
                    Toast.makeText(ctx, ctx.getString(R.string.added_n, n), Toast.LENGTH_SHORT).show()
                    tab = 0
                }
                n > 0
            }
        )
    }

    confirmDelete?.let { d ->
        DeleteDialog(
            item = d,
            onDismiss = { confirmDelete = null },
            onConfirm = { withFile -> vm.delete(d.id, withFile); confirmDelete = null }
        )
    }
}
