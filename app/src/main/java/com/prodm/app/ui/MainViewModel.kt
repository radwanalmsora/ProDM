package com.prodm.app.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prodm.app.ProDMApp
import com.prodm.app.core.Live
import com.prodm.app.core.Scheduler
import com.prodm.app.core.URL_REGEX
import com.prodm.app.data.AppSettings
import com.prodm.app.data.DownloadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val papp = application as ProDMApp
    private val core = papp.core

    val items: StateFlow<List<DownloadItem>> =
        papp.db.dao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val live: StateFlow<Map<Long, Live>> = core.live
    val settings: StateFlow<AppSettings> = papp.settings.current

    val addDialog = MutableStateFlow<NewDownload?>(null)
    val clipboardUrl = MutableStateFlow<String?>(null)

    val browser: BrowserController by lazy {
        BrowserController(application) { req -> addDialog.value = req }
    }

    fun openAdd(req: NewDownload) {
        addDialog.value = req
    }

    fun submit(req: NewDownload, text: String, name: String, later: Boolean): Int {
        val urls = URL_REGEX.findAll(text).map { it.value }.distinct().toList()
        if (urls.isEmpty()) return 0
        viewModelScope.launch {
            urls.forEach {
                core.add(it, if (urls.size == 1) name else null, later, req.referer, req.userAgent, req.cookie)
            }
        }
        return urls.size
    }

    fun pause(id: Long) = core.pause(id)
    fun resume(id: Long) = core.resume(id)
    fun delete(id: Long, withFile: Boolean) = core.delete(id, withFile)
    fun redownload(id: Long) = core.redownload(id)
    fun pauseAll() = core.pauseAll()
    fun resumeAll() = core.resumeAll()
    fun clearCompleted() = core.clearCompleted()

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            papp.settings.update(block)
            Scheduler.apply(papp, papp.settings.snapshot())
            core.pump()
        }
    }

    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }
}
