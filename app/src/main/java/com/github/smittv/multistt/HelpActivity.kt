package com.github.smittv.multistt

import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import java.util.Locale

class HelpActivity : AppCompatActivity() {

    fun isAssetFolderExist(folder: String): Boolean {
        return  try {
            this.assets.list(folder) != null
        } catch (e: Throwable) {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.help)
            setDisplayHomeAsUpEnabled(true)
        }?.show()
        val webView = findViewById<WebView>(R.id.webview)
        val language = Locale.getDefault().language
        val path = if (isAssetFolderExist(language)) language else "en"
        webView.loadUrl("file:///android_asset/${path}/help.html")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            else -> return false
        }
    }
}