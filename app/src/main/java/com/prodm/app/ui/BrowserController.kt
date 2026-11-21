package com.prodm.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.prodm.app.core.URL_REGEX
import com.prodm.app.core.parseContentDisposition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.net.URLEncoder

data class NewDownload(
    val url: String = "",
    val fileName: String = "",
    val referer: String? = null,
    val userAgent: String? = null,
    val cookie: String? = null
)

private val MEDIA_REGEX = Regex(
    "\\.(mp4|webm|mkv|mp3|m4a|aac|flac|wav|ogg|avi|mov|3gp)(\\?|$)",
    RegexOption.IGNORE_CASE
)

@SuppressLint("SetJavaScriptEnabled")
class BrowserController(
    private val app: Context,
    private val onDownload: (NewDownload) -> Unit
) {
    private val wrapper = MutableContextWrapper(app)
    val webView: WebView = WebView(wrapper)

    val currentUrl = MutableStateFlow("https://www.google.com")
    val progress = MutableStateFlow(0)
    val canBack = MutableStateFlow(false)
    val canForward = MutableStateFlow(false)
    val media = MutableStateFlow<List<String>>(emptyList())

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                currentUrl.value = url ?: ""
                media.value = emptyList()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                currentUrl.value = url ?: currentUrl.value
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                canBack.value = view?.canGoBack() == true
                canForward.value = view?.canGoForward() == true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val u = request?.url?.toString()
                if (u != null && MEDIA_REGEX.containsMatchIn(u)) {
                    media.update { if (u in it) it else it + u }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val s = request?.url?.scheme
                return s != "http" && s != "https"
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.value = newProgress
            }
        }

        webView.setDownloadListener(DownloadListener { url, ua, cd, mime, _ ->
            val name = parseContentDisposition(cd) ?: URLUtil.guessFileName(url, cd, mime)
            onDownload(
                NewDownload(
                    url = url,
                    fileName = name,
                    referer = currentUrl.value,
                    userAgent = ua,
                    cookie = CookieManager.getInstance().getCookie(url)
                )
            )
        })

        webView.loadUrl("https://www.google.com")
    }

    fun attach(ctx: Context) {
        wrapper.baseContext = ctx
        webView.onResume()
    }

    fun detach() {
        webView.onPause()
        wrapper.baseContext = app
    }

    fun navigate(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val target = when {
            URL_REGEX.matchEntire(t) != null -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
        }
        webView.loadUrl(target)
    }

    fun requestFor(url: String): NewDownload = NewDownload(
        url = url,
        referer = currentUrl.value,
        userAgent = webView.settings.userAgentString,
        cookie = CookieManager.getInstance().getCookie(url)
    )

    fun fileLabel(url: String): String =
        runCatching { Uri.parse(url).lastPathSegment }.getOrNull() ?: url
}
