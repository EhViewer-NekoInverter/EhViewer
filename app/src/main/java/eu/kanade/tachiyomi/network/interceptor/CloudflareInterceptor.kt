package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.exception.CloudflareBypassException
import com.hippo.ehviewer.util.isWebViewOutdated
import com.hippo.util.launchIO
import com.hippo.util.launchUI
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.DelicateCoroutinesApi
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

class CloudflareInterceptor(val context: Context) : WebViewInterceptor(context) {
    private val executor = ContextCompat.getMainExecutor(context)

    override fun shouldIntercept(response: Response): Boolean {
        // Check if Cloudflare anti-bot is on
        return if (response.code in ERROR_CODES && response.header("Server") in SERVER_CHECK) {
            val document = Jsoup.parse(
                response.peekBody(Long.MAX_VALUE).string(),
                response.request.url.toString(),
            )

            // solve with webview only on captcha, not on geo block
            document.getElementById("challenge-error-title") != null ||
                document.getElementById("challenge-error-text") != null
        } else {
            false
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
    ): Response {
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        return runCatching {
            response.close()
            launchIO { EhCookieStore.deleteCookie(request.url, EhCookieStore.KEY_CLOUDFLARE) }
            resolveWithWebView(request)
            chain.proceed(request)
        }.getOrElse { throw IOException(it) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun resolveWithWebView(originalRequest: Request) {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webview: WebView? = null

        var challengeFound = false
        var cloudflareBypassed = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)
        EhCookieStore.loadForWebView(origRequestUrl) {
            it.name != EhCookieStore.KEY_CLOUDFLARE
        }

        executor.execute {
            webview = createWebView()

            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    cloudflareBypassed = EhCookieStore.saveFromWebView(origRequestUrl) {
                        it.name == EhCookieStore.KEY_CLOUDFLARE
                    }

                    if (cloudflareBypassed) {
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !challengeFound) {
                        // The first request didn't return the challenge, abort.
                        latch.countDown()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.statusCode in ERROR_CODES) {
                            // Found the Cloudflare challenge page.
                            challengeFound = true
                        } else {
                            // Unlock thread, the challenge wasn't found.
                            latch.countDown()
                        }
                    }
                }
            }

            webview.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            webview?.run {
                stopLoading()
                destroy()
            }
        }

        // Throw exception if we failed to bypass Cloudflare
        if (!cloudflareBypassed) {
            // Prompt user to update WebView if it seems too outdated
            if (isWebViewOutdated) {
                launchUI { Toast.makeText(context, R.string.information_webview_outdated, Toast.LENGTH_LONG).show() }
            }

            throw CloudflareBypassException()
        }
    }
}

private val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
