package com.karakept.app.ui.components

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android implementation of UrlRenderer using Android WebView.
 *
 * Security notes:
 * - JavaScript is enabled (needed to render most modern web pages)
 * - File access is disabled
 * - Links within the page are intercepted and reported via onLinkClick
 */
@Composable
actual fun UrlRenderer(
    url: String,
    modifier: Modifier,
    onPageTitleChanged: ((String) -> Unit)?,
    onLinkClick: ((String) -> Unit)?
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.title?.let { title ->
                            if (title.isNotBlank()) {
                                onPageTitleChanged?.invoke(title)
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val requestUrl = request?.url?.toString()
                        return if (requestUrl != null && onLinkClick != null) {
                            onLinkClick(requestUrl)
                            true
                        } else {
                            false
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        requestUrl: String?
                    ): Boolean {
                        return if (requestUrl != null && onLinkClick != null) {
                            onLinkClick(requestUrl)
                            true
                        } else {
                            false
                        }
                    }
                }

                // Store the URL we explicitly asked to load so the update block can
                // distinguish "url parameter changed" from "user navigated inside browser".
                tag = url
                loadUrl(url)
            }
        },
        update = { webView ->
            // Only reload when the *parameter* url changes, not when the WebView has
            // navigated to a different page (which would reset in-browser navigation).
            val lastExplicitUrl = webView.tag as? String
            if (lastExplicitUrl != url) {
                webView.tag = url
                webView.loadUrl(url)
            }
        }
    )
}
