package com.ktma.bothnow

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.WindowManager
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var pending: PermissionRequest? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res.values.all { it }
        pending?.let { if (ok) it.grant(it.resources) else it.deny() }
        pending = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Request camera/microphone permissions only when the WebView actually
        // requests media access. Requesting them here races the page startup.

        // Serving from https://appassets... makes the page a secure context (needed for the camera),
        // and it works fully offline because index.html lives inside the app.
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(r.url)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (hasPerms()) request.grant(request.resources)
                        else { pending = request; askPermissions() }
                    }
                }
            }
            addJavascriptInterface(GalleryBridge(), "BothNowNative")
        }
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun hasPerms() = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun askPermissions() {
        if (!hasPerms()) permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Keep the app open on back; the page has its own Back buttons.
        moveTaskToBack(true)
    }

    /** Saves videos straight into the phone gallery (Movies/BothNow). No storage permission needed on Android 10+. */
    inner class GalleryBridge {
        private var uri: Uri? = null
        private var out: OutputStream? = null

        @JavascriptInterface
        fun begin(name: String, mime: String): Boolean = try {
            abort()
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, mime)
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BothNow")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            out = uri?.let { contentResolver.openOutputStream(it) }
            out != null
        } catch (e: Exception) { false }

        @JavascriptInterface
        fun chunk(b64: String): Boolean = try {
            out!!.write(Base64.decode(b64, Base64.DEFAULT)); true
        } catch (e: Exception) { false }

        @JavascriptInterface
        fun finish(): Boolean = try {
            out?.flush(); out?.close(); out = null
            val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            contentResolver.update(uri!!, v, null, null)
            uri = null; true
        } catch (e: Exception) { false }

        @JavascriptInterface
        fun abort() {
            try { out?.close() } catch (_: Exception) {}
            out = null
            uri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            uri = null
        }
    }
}
