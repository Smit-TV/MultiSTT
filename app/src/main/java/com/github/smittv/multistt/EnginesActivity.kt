package com.github.smittv.multistt

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.smittv.multistt.engines.EngineConfigParser
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class  EnginesActivity : AppCompatActivity() {
    companion object {
        const val TAG = "EnginesActivity"
    }

    private lateinit var enginesList: ListView
    private val engineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engines)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.engines)
            setDisplayHomeAsUpEnabled(true)
        }?.show()
        enginesList = findViewById<ListView>(R.id.list_view)
        initEnginesList()
    }

    fun initEnginesList() {
        val engineConfig = EngineConfigParser(this)
        enginesList.adapter =  EngineAdapter(this, engineConfig.getNamesAndPaths().toMutableList())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_export -> {
                exportAll()
                return true
            }
            R.id.add -> {
                addEngine()
                return true
            }
            else -> return  super.onOptionsItemSelected(item)
        }
    }

    fun addEngine() {
        val layout = layoutInflater.inflate(R.layout.engine_dialog, null) as MaterialCardView

        val name = layout.findViewById<TextInputEditText>(R.id.editName)
        val export = layout.findViewById<ImageButton>(R.id.btnExport)
        export?.visibility = ImageButton.GONE
        val offline = layout.findViewById<MaterialSwitch>(R.id.switchOffline)

        val dialog = AlertDialog.Builder(this, R.style.dialog)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener { _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { _ ->
                val text = name.text.toString()
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.please_enter_name, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val result = EngineConfigParser(this).addEngine(
                    text,
                    offline.isChecked
                )

                if (result == EngineConfigParser.RESULT_ERROR) {
                    Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                } else if (result == EngineConfigParser.RESULT_ENGINE_ALREADY_EXISTS) {
                    Toast.makeText(this, R.string.engine_already_exists, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                initEnginesList()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun exportAll() {
        engineScope.launch {
                try {
                    Toast.makeText(
                        this@EnginesActivity,
                        R.string.exporting_please_wait,
                        Toast.LENGTH_SHORT
                    ).show()
                    withContext(Dispatchers.IO) {
                        val zipFile = File(cacheDir, "all_engines.zip")

                        if (zipFile.exists()) {
                            zipFile.delete()
                        }

                        ZipUtils.zipFiles(getExternalFilesDir(null)!!.listFiles(), zipFile)
                        .onSuccess {
                            withContext(Dispatchers.Main) {
                                Utils.sendFile(this@EnginesActivity, it, "all_engines.zip")
                            }
                            Log.i(TAG, "Successfuly created a zip file")
                        }
                        .onFailure {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@EnginesActivity,
                                    R.string.fail_to_export,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            Log.e(TAG, "fail to create a zip file")
                            it.printStackTrace()
                        }
                }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.engines, menu)
        return true
    }
}