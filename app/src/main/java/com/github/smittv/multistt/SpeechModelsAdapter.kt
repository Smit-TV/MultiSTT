package com.github.smittv.multistt

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.github.smittv.multistt.engines.EngineConfigParser
import com.github.smittv.multistt.engines.ModelConfigParser
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class SpeechModelsAdapter(
    val ctx: Context,
    val items: MutableList<ModelConfigParser.Model>,
    val onSelected: (ModelConfigParser.Model) -> Unit
) : ArrayAdapter<ModelConfigParser.Model>(ctx, R.layout.model_list_item, items) {
    private val layoutInflater = LayoutInflater.from(ctx)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    private val editor = prefs.edit()
    private val modelCoroutine = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = layoutInflater.inflate(R.layout.model_list_item, null) as MaterialCardView
        val itemData = items[position]
        val locale = view.findViewById<TextView>(R.id.locale)
        val name = view.findViewById<TextView>(R.id.name)
        val status = view.findViewById<Chip>(R.id.status)
        val selection = view.findViewById<CheckBox>(R.id.is_selected)

        locale.text = itemData.locale
        name.text = itemData.name
        status.setText(if (itemData.isOffline) R.string.offline else R.string.online)
        selection.isChecked = prefs.getBoolean(itemData.preferenceName, false)

//        in my upinion as a  user of screen readers it's better to make checkboxes not focusable to screen readers
        selection.isClickable = false
        selection.isFocusable = false

        view.setOnClickListener {
            selection.isChecked = !selection.isChecked
            val isSelected = selection.isChecked
            editor
                .putBoolean(itemData.preferenceName, isSelected)
                .apply()
            onSelected(itemData)
        }

        status.setOnLongClickListener {
            removeModel(itemData)
            true
        }

        locale.setOnLongClickListener {
            editModel(itemData, position)
            true
        }

        return view
    }

    fun exportModel(model: ModelConfigParser.Model) {
        Toast.makeText(context, R.string.exporting_please_wait, Toast.LENGTH_SHORT).show()
        modelCoroutine.launch {
            try {
                withContext(Dispatchers.IO) {
                    val zipFile =
                        File(context.cacheDir, "/${model.vendor}_${model.locale}-${model.name}.zip")

                    if (zipFile.exists()) {
                        zipFile.delete()
                    }

                    zipFile.createNewFile()

                    val modelConfig = ModelConfigParser(context).getModelConfig(model)
                    val modelFile = File(context.cacheDir, "/models.ini")

                    if (modelFile.exists()) {
                        modelFile.delete()
                    }
                    modelFile.createNewFile()
                    modelConfig.store(modelFile)

                    val engineConfig = EngineConfigParser(context).getEngineConfig(model.vendor)
                    val engineFile = File(context.cacheDir, "/engines.ini")

                    if (engineFile.exists()) {
                        engineFile.delete()
                    }
                    engineFile.createNewFile()
                    engineConfig.store(engineFile)

                    val modelFiles =
                        File(context.getExternalFilesDir(null), "/${model.vendor}/${model.folder}")

                    if (!modelFiles.exists()) {
                        modelFiles.mkdirs()
                    }

                    val vendorFolder = File(context.cacheDir, "/${model.vendor}")

                    if (vendorFolder.exists()) {
                        vendorFolder.deleteRecursively()
                    }
                    vendorFolder.mkdirs()

                    val copyModelFolder = File(vendorFolder, "/${model.folder}")
                    copyModelFolder.mkdirs()
                    modelFiles.copyRecursively(
                        copyModelFolder, true
                    )

                    ZipUtils.zipFiles(
                        arrayOf(
                            modelFile, engineFile, vendorFolder
                        ), zipFile
                    )
                        .onSuccess {
                            Utils.sendFile(context, it, zipFile.name)
                        }
                        .onFailure {
                            it.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.fail_to_export, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
        }

    }

    fun editModel(model: ModelConfigParser.Model, position: Int) {
        val layout = layoutInflater.inflate(R.layout.engine_dialog_add, null) as MaterialCardView
        val title = layout.findViewById<TextView>(R.id.title)
        title.setText(context.getString(R.string.edit_model, model.name))
        val folder = layout.findViewById<TextInputEditText>(R.id.editFolder)
        folder.setText(model.folder)
        val name = layout.findViewById<TextInputEditText>(R.id.editName)
        name.setText(model.name)
        val locale = layout.findViewById<TextInputEditText>(R.id.editLocale)
        locale.setText(model.locale)
        val offline = layout.findViewById<MaterialSwitch>(R.id.switchOffline)
        offline.isChecked = model.isOffline
        val export = layout.findViewById<ImageButton>(R.id.btnExport)
        export.setOnClickListener {
            exportModel(model)
        }
        val parameters = layout.findViewById<TextInputEditText>(R.id.editParameters)
        parameters.setText(model.parameters)

        val dialog = AlertDialog.Builder(context, R.style.dialog)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
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

                val result = ModelConfigParser(context).editModel(name, model.vendor, locale, folder, isOffline, model.modelSectionName, parameters)

                when (result) {
                    ModelConfigParser.RESULT_MODEL_ALREADY_EXISTS -> {
                        Toast.makeText(context, R.string.model_already_exists, Toast.LENGTH_SHORT).show()
                    }

                    ModelConfigParser.RESULT_ERROR -> {
                        Toast.makeText(context, R.string.fail_to_add_model, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        items.set(
                            position,
                            ModelConfigParser.Model(name, folder, locale, model.vendor, isOffline, parameters)
                            )
                        notifyDataSetChanged()
                        dialog.dismiss()
                    }
                }

            }
        }

        dialog.show()
    }

    fun removeModel(model: ModelConfigParser.Model)
    {
        val dialog = AlertDialog.Builder(context, R.style.dialog)
            .setTitle(R.string.confirm)
            .setMessage(context.getString(R.string.will_be_removed, model.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ModelConfigParser(context).removeModel(model)
                items.remove(model)
                notifyDataSetChanged()
            }
            .create()

        dialog.show()
    }

    fun setItems(newItems: MutableList<ModelConfigParser.Model>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}