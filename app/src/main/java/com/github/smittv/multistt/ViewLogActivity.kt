package com.github.smittv.multistt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

class ViewLogActivity : AppCompatActivity() {
    companion object {
        const val TAG = "ViewLogActivity"
    }
    private lateinit var listView: ListView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_log)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.view_log)
            setDisplayHomeAsUpEnabled(true)
        }?.show()
        initListView()
    }

    fun initListView() {
        Thread {
            val logs = getLogs()
            runOnUiThread {
                listView = findViewById<ListView>(R.id.content)
                listView.adapter =
                    ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_list_item_1,
                        logs.split("\n")
                    )
            }
    }.start()
    }

    fun getLogs(): String {
        return try {
            Runtime.getRuntime().exec("logcat -d -v raw").inputStream.bufferedReader().readText()
        } catch (e: Throwable) {
            e.message ?: "fail to get logs"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_export -> {
                exportLogs()
                return  true
            }
            R.id.action_clear -> {
                clearLogs()
                return true
            }
            else -> return false
        }
    }

    fun clearLogs() {
        try {
            Runtime.getRuntime().exec("logcat -c")
        } catch (e: Throwable) {
            Log.e(TAG, "Fail to clear logs", e)
        }
        initListView()
    }

    fun exportLogs() {
        val logFile = File(filesDir, "/logs.txt")
        if (!logFile.exists()) {
            logFile.createNewFile()
        }

        Thread {
            val logs = getLogs()
            logFile.writeText(logs)
            Utils.sendFile(this, logFile, "logs.txt")
        }.start()
    }



    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.view_log_menu, menu)
        return true
    }
    }