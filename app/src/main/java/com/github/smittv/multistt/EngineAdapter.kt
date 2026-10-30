package com.github.smittv.multistt

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.github.smittv.multistt.engines.Engine
import com.github.smittv.multistt.engines.EngineConfigParser
import com.github.smittv.multistt.engines.ModelConfigParser
import com.google.android.material.card.MaterialCardView
import com.google.android.material.internal.ParcelableSparseIntArray
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EngineAdapter(
    val ctx: Context,
    val items: List<Pair<String, String>>,
) : ArrayAdapter<Pair<String, String>>(ctx, R.layout.engine_list_item, items) {
    companion object {
        const val TAG = "EngineAdapter"
    }

    private lateinit var layoutInflater: LayoutInflater
    private val engineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    init {
        layoutInflater = LayoutInflater.from(ctx)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = layoutInflater.inflate(R.layout.engine_list_item, null) as LinearLayout
        val itemData = items[position]
item.findViewById<TextView>(R.id.engine_name).setText(itemData.first)
        val export = item.findViewById<ImageButton>(R.id.action_export)
        val add = item.findViewById<ImageButton>(R.id.add)

        add.setOnClickListener { it ->
            addModel(itemData)
        }

        export.setOnClickListener { it ->
            export(itemData)
        }

        item.setOnLongClickListener {
            removeEngine(itemData)
            true
        }

        return item
    }

    fun removeEngine(item: Pair<String, String>) {
        val dialog = AlertDialog.Builder(context, R.style.dialog)
            .setTitle(R.string.confirm)
            .setMessage(context.getString(R.string.engine_will_be_removed, item.first))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                EngineConfigParser(context).removeEngine(item.first)
                remove(item)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
    }

    fun export(item: Pair<String, String>) {
        engineScope.launch {
            try {
                Toast.makeText(context, R.string.exporting_please_wait, Toast.LENGTH_SHORT).show()

                withContext(Dispatchers.IO) {
                    val engineFolder = File(context.getExternalFilesDir(null), item.second)
                    if (!engineFolder.exists()) {
                        engineFolder.mkdirs()
                    }

                    val engineConfig = EngineConfigParser(context).getEngineConfig(item.first)
                    val engineConfigFile = File(context.cacheDir, "engines.ini")

                    if (engineConfigFile.isDirectory) {
                        engineConfigFile.deleteRecursively()
                    } else if (engineConfigFile.isFile) {
                        engineConfigFile.delete()
                    }
                    if (!engineConfigFile.exists()) {
                        engineConfigFile.createNewFile()
                    }

                    engineConfig.store(engineConfigFile)

                    val modelsConfig = ModelConfigParser(context).getModelsConfig(item.first)
                    val modelsConfigFile = File(context.cacheDir, "models.ini")

                    if (modelsConfigFile.isFile) {
                        modelsConfigFile.delete()
                    } else if (modelsConfigFile.isDirectory) {
                        modelsConfigFile.deleteRecursively()
                    }

                    if (!modelsConfigFile.exists()) {
                        modelsConfigFile.createNewFile()
                    }

                    modelsConfig.store(modelsConfigFile)

                    val zipName = "${item.first}-X${modelsConfig.keys.size}.zip"
                    val zipFile = File(context.cacheDir, zipName)

                    if (zipFile.exists()) {
                        zipFile.delete()
                    }

                    ZipUtils.zipFiles(
                        arrayOf(
                            engineConfigFile, modelsConfigFile, engineFolder
                        ), zipFile
                    )
                        .onSuccess {
                            Utils.sendFile(context, it, zipName)
                            Log.i(TAG, "Successfuly exported $zipName")
                        }
                        .onFailure {
                            Log.e(TAG, "fail to export")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.fail_to_export, Toast.LENGTH_SHORT).show()
                                it.printStackTrace()
                            }
                        }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun addModel(item: Pair<String, String>) {
        val layout = layoutInflater.inflate(R.layout.engine_dialog_add, null) as MaterialCardView

        val name = layout.findViewById<TextInputEditText>(R.id.editName)
        val export = layout.findViewById<ImageButton>(R.id.btnExport)
        export?.visibility = ImageButton.GONE
        val offline = layout.findViewById<MaterialSwitch>(R.id.switchOffline)
        val locale = layout.findViewById<TextInputEditText>(R.id.editLocale)
        val folder = layout.findViewById<TextInputEditText>(R.id.editFolder)
        val parameters = layout.findViewById<TextInputEditText>(R.id.editParameters)

        val dialog = AlertDialog.Builder(context, R.style.dialog)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener { _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { _ ->
                val name = name.text.toString()
                val locale = locale.text.toString()
                val folder = folder.text.toString()
                val isOffline = offline.isChecked
                val parameters = parameters.text.toString()

                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.please_enter_name, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (folder.isEmpty()) {
                    Toast.makeText(context, R.string.please_enter_folder, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (locale.isEmpty()) {
                    Toast.makeText(context, R.string.please_enter_locale, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val result = ModelConfigParser(context).addModel(name, item.first, locale, folder, isOffline, parameters)

                when (result) {
                    ModelConfigParser.RESULT_MODEL_ALREADY_EXISTS -> {
                        Toast.makeText(context, R.string.model_already_exists, Toast.LENGTH_SHORT).show()
                    }

                    ModelConfigParser.RESULT_ERROR -> {
                        Toast.makeText(context, R.string.fail_to_add_model, Toast.LENGTH_SHORT).show()
                    }
                    else -> dialog.dismiss()
                }

            }
        }

        dialog.show()
    }
}