package io.github.mohamtur1.fourcbon2

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Division 24/25 (Android bindings): Custom Tabs launching and WebView hardening.
 *
 * Split out of `BrowserSurfaces.kt` in v0.3 (Division 43) so URL validation, navigation policy, and
 * reader snapshots stay pure Kotlin. [configureIsolatedWebView] is now a top-level extension on
 * [ControlledBrowserWorkspace]; same-package call sites resolve it unchanged.
 */
class AndroidCustomTabPlatformDriver(private val context: Context) : CustomTabPlatformDriver {
    override fun openHttpsCustomTab(url: String, preferChromePackage: Boolean): BrowserOpenMode? {
        val parsed = Uri.parse(url)
        val customTabIntent = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val extras = Bundle().apply {
                putBinder(EXTRA_CUSTOM_TABS_SESSION, null)
            }
            putExtras(extras)
            putExtra(EXTRA_TITLE_VISIBILITY_STATE, SHOW_PAGE_TITLE)
        }

        if (preferChromePackage) {
            val chromeIntent = Intent(customTabIntent).apply { `package` = CHROME_PACKAGE }
            val resolvedChrome = context.packageManager.resolveActivity(chromeIntent, 0) != null
            if (resolvedChrome) {
                try {
                    context.startActivity(chromeIntent)
                    return BrowserOpenMode.CHROME_CUSTOM_TAB
                } catch (_: ActivityNotFoundException) {
                    // Fall through to generic browser fallback
                }
            }
        }

        return try {
            context.startActivity(customTabIntent)
            BrowserOpenMode.DEFAULT_BROWSER_FALLBACK
        } catch (_: ActivityNotFoundException) {
            null
        }
    }

    private companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
        const val EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"
        const val EXTRA_TITLE_VISIBILITY_STATE = "android.support.customtabs.extra.TITLE_VISIBILITY"
        const val SHOW_PAGE_TITLE = 1
    }
}

/**
 * Applies strict security hardening to an Android [WebView] instance when rendered on-device:
 * NEVER registers `addJavascriptInterface`, disables file/content access, disables third-party
 * cookies, and enforces [ControlledBrowserWorkspace.checkNavigationPolicy] on every navigation.
 */
@SuppressLint("SetJavaScriptEnabled")
fun ControlledBrowserWorkspace.configureIsolatedWebView(
    webView: WebView,
    onRedirectToCustomTab: (String) -> Unit,
) {
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    webView.settings.apply {
        javaScriptEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = false
        databaseEnabled = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(false)
    }
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val target = request?.url?.toString().orEmpty()
            return when (val decision = checkNavigationPolicy(target)) {
                is ControlledNavigationDecision.AllowInWorkspace -> false
                is ControlledNavigationDecision.UseCustomTabInstead -> {
                    onRedirectToCustomTab(decision.url)
                    true
                }
                is ControlledNavigationDecision.Block -> true
            }
        }
    }
}

