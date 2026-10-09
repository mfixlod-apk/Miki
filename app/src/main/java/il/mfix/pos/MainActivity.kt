package il.mfix.pos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.File

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    @Volatile private var currentHost: String = ""

    companion object {
        const val SITE = "https://user.yeshinvoice.co.il"
        const val SITE_HOST = "user.yeshinvoice.co.il"
        const val RAWBT_PKG = "ru.a402d.rawbtprinter"
        const val REQ_FILE = 4401
        const val REQ_CAM = 4402
    }

    inner class Bridge {
        private val file get() = File(filesDir, "mfix_storage.json")
        @JavascriptInterface fun storageLoad(): String {
            if (currentHost != SITE_HOST) return "{}"
            return try { if (file.exists()) file.readText() else "{}" } catch (_: Exception) { "{}" }
        }
        @JavascriptInterface fun storageSave(json: String) {
            if (currentHost != SITE_HOST) return
            try {
                val tmp = File(filesDir, "mfix_storage.tmp")
                tmp.writeText(json)
                tmp.renameTo(file)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            userAgentString = userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.addJavascriptInterface(Bridge(), "MFIXNative")
        val bundle = assets.open("bundle.js").bufferedReader().use { it.readText() }
        val docStartOk = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStartOk) WebViewCompat.addDocumentStartJavaScript(web, bundle, setOf(SITE))
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                handleUrl(request.url.toString())
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if (u.host == SITE_HOST && (u.path ?: "").startsWith("/__mfix_ext__/"))
                    return WebResourceResponse("application/javascript", "utf-8", ByteArrayInputStream(ByteArray(0)))
                return null
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentHost = Uri.parse(url).host ?: ""
                if (!docStartOk && currentHost == SITE_HOST) view.evaluateJavascript(bundle, null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                        request.grant(request.resources)
                    else {
                        requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_CAM)
                        request.deny()
                    }
                }
            }
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                return try { startActivityForResult(params.createIntent(), REQ_FILE); true }
                catch (_: ActivityNotFoundException) { fileCallback = null; false }
            }
        }
        web.setDownloadListener { url, _, _, _, _ ->
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
        }
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl("$SITE/")
    }

    private fun handleUrl(url: String): Boolean {
        if (url.startsWith("intent:")) {
            try {
                val i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (i.`package` != RAWBT_PKG) return true
                i.addCategory(Intent.CATEGORY_BROWSABLE)
                i.selector = null
                startActivity(i)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "RawBT לא מותקן", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {}
            return true
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") ||
            url.startsWith("blob:") || url.startsWith("data:")) return false
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:") || url.startsWith("sms:"))
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        } else super.onActivityResult(requestCode, resultCode, data)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); web.saveState(outState) }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush() }
}