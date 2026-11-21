package com.prodm.app

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prodm.app.core.URL_REGEX
import com.prodm.app.ui.AppRoot
import com.prodm.app.ui.MainViewModel
import com.prodm.app.ui.NewDownload
import com.prodm.app.ui.ProDMTheme

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private var lastClip: String? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val s by vm.settings.collectAsStateWithLifecycle()
            ProDMTheme(theme = s.theme, amoled = s.amoled, dynamic = s.dynamic) {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }

    private fun handleIntent(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> i.dataString
            else -> null
        } ?: return
        URL_REGEX.find(text)?.value?.let { vm.openAdd(NewDownload(url = it)) }
    }

    private fun checkClipboard() {
        if (!vm.settings.value.clipboard) return
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: return
        if (text != lastClip && URL_REGEX.matchEntire(text) != null) {
            lastClip = text
            vm.clipboardUrl.value = text
        }
    }
}
