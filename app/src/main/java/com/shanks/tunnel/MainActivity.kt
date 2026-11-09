package com.shanks.tunnel

import android.content.ClipboardManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var pendingText: String? = null
    private var chooserCb: ValueCallback<Array<Uri>>? = null

    // Enregistrement d'un fichier (.shs) via le sélecteur Android
    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val text = pendingText
        pendingText = null
        if (uri == null || text == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            toast("Fichier enregistré")
        } catch (e: Exception) {
            toast("Enregistrement impossible : ${e.message}")
        }
    }

    // Choix d'un fichier à importer
    private val chooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val res = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
        chooserCb?.onReceiveValue(res)
        chooserCb = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams
            ): Boolean {
                chooserCb?.onReceiveValue(null)
                chooserCb = cb
                val i = Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                return try {
                    chooser.launch(i); true
                } catch (e: Exception) {
                    chooserCb = null; cb.onReceiveValue(null); false
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "AndroidVpn")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun toast(m: String) = runOnUiThread {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    }

    /** Pont JavaScript -> Android (nom côté page : window.AndroidVpn) */
    inner class Bridge {
        private val prefs get() = getSharedPreferences("shanks", MODE_PRIVATE)

        // Aucun moteur n'est encore intégré (étapes 3 et 4)
        @JavascriptInterface fun getEngines(): String = """{"v2ray":false,"dnstt":false}"""

        @JavascriptInterface fun getDeviceId(): String {
            val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            val h = MessageDigest.getInstance("SHA-256").digest("shanks:$id".toByteArray())
            return "ID-" + h.take(4).joinToString("") { "%02X".format(it) }
        }

        // TODO : à sécuriser plus tard (réservé à l'administrateur)
        @JavascriptInterface fun isAdmin(): Boolean = true

        @JavascriptInterface fun saveConfig(json: String) {
            prefs.edit().putString("cfg", json).apply()
        }

        @JavascriptInterface fun saveFile(name: String, text: String) {
            runOnUiThread { pendingText = text; createDoc.launch(name) }
        }

        @JavascriptInterface fun readClipboard(): String {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            return clip.getItemAt(0).coerceToText(this@MainActivity)?.toString() ?: ""
        }

        @JavascriptInterface fun getInstalledApps(): String {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val arr = JSONArray()
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != packageName }
                .sortedBy { it.second.lowercase() }
                .forEach { arr.put(JSONObject().put("pkg", it.first).put("name", it.second)) }
            return arr.toString()
        }

        @JavascriptInterface fun connect(json: String) {
            toast("Moteur VPN pas encore intégré (prochaines étapes)")
        }
        // Stats du VPN (le service VPN les fournira à l'étape suivante)
        @JavascriptInterface fun getStats(): String = """{"state":"off"}"""
        @JavascriptInterface fun isRooted(): Boolean {
            val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk", "/data/local/bin/su", "/data/local/xbin/su")
            return paths.any { java.io.File(it).exists() } || (android.os.Build.TAGS?.contains("test-keys") == true)
        }
        @JavascriptInterface fun getNetType(): String {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
            return when {
                c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
                else -> "other"
            }
        }
        @JavascriptInterface fun openUrl(u: String) {
            if (u.startsWith("https://")) runOnUiThread { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
        }
        @JavascriptInterface fun shareFile(name: String, text: String) {
            runOnUiThread {
                try {
                    val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val f = java.io.File(cacheDir, safe).apply { writeText(text) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                    val i = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(i, "Partager"))
                } catch (e: Exception) { toast("Partage impossible : ${e.message}") }
            }
        }
        @JavascriptInterface fun disconnect() {}
        @JavascriptInterface fun validateCode(code: String): String =
            "Validation serveur pas encore disponible"
    }
}
