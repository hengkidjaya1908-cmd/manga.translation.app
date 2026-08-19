package com.example.mangatranslator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var btnGo: Button
    private lateinit var btnTranslate: ExtendedFloatingActionButton
    private lateinit var progressBar: ProgressBar

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webViewManga)
        etUrl = findViewById(R.id.etUrl)
        btnGo = findViewById(R.id.btnGo)
        btnTranslate = findViewById(R.id.btnTranslate)
        progressBar = findViewById(R.id.progressBar)

        setupWebView()

        btnGo.setOnClickListener { loadInputUrl() }
        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadInputUrl()
                true
            } else false
        }

        btnTranslate.setOnClickListener {
            translateMangaOnScreen()
        }

        webView.loadUrl("https://mangadex.org")
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                etUrl.setText(url)
                progressBar.visibility = View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun loadInputUrl() {
        var url = etUrl.text.toString().trim()
        if (url.isNotEmpty()) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            webView.loadUrl(url)
        }
    }

    private fun translateMangaOnScreen() {
        Toast.makeText(this, "Menganalisis teks manga di layar...", Toast.LENGTH_SHORT).show()
        btnTranslate.isEnabled = false

        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        webView.draw(canvas)

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                btnTranslate.isEnabled = true
                val extractedText = visionText.text.trim()

                if (extractedText.isNotEmpty()) {
                    translateToIndonesian(extractedText)
                } else {
                    Toast.makeText(this, "Tidak ada teks yang terdeteksi pada tampilan saat ini.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                btnTranslate.isEnabled = true
                Toast.makeText(this, "Gagal membaca gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun translateToIndonesian(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=id&dt=t&q=$encoded"

                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (responseBody != null) {
                    val jsonArray = JSONArray(responseBody)
                    val sentences = jsonArray.getJSONArray(0)
                    val translatedBuilder = StringBuilder()

                    for (i in 0 until sentences.length()) {
                        translatedBuilder.append(sentences.getJSONArray(i).getString(0))
                    }

                    val resultText = translatedBuilder.toString()

                    withContext(Dispatchers.Main) {
                        showResultDialog(text, resultText)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Gagal menerjemahkan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showResultDialog(original: String, translated: String) {
        AlertDialog.Builder(this)
            .setTitle("📖 Terjemahan Manga")
            .setMessage("Teks Asli (EN):\n$original\n\nTerjemahan (ID):\n$translated")
            .setPositiveButton("Lanjut Baca", null)
            .show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
