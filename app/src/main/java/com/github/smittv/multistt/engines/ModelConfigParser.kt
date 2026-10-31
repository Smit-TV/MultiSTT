package com.github.smittv.multistt.engines

import android.R
import android.content.Context
import android.util.Log
import androidx.compose.ui.text.intl.Locale
import com.github.smittv.multistt.Utils
import org.ini4j.Ini
import java.io.File
import java.io.StringReader
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class ModelConfigParser(
    val context: Context
) {
    companion object {
        const val TAG = "ModelConfigParser"
        const val RESULT_OK = 0
        const val RESULT_ERROR = 1
        const val RESULT_MODEL_ALREADY_EXISTS = 2

        fun getModelSectionNameFor(model: Model): String = "${model.vendor}-${model.locale}-${model.name}"

        fun getModelSectionName(name: String, vendor: String, locale: String): String = "$vendor-$locale-$name"
    }

    private val config =  Ini()

    init {
        config.config.isEscape = false
//        config.config.isEscapeNewline = true
        config.config.isMultiOption = true

        init()
    }

    fun getConfigFile() = File(context.getExternalFilesDir(null), "/models.ini")

    fun init() {
        try {
            val configFile = getConfigFile()

            if (!configFile.exists()) {
                configFile.createNewFile()
            }

            config.load(configFile)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun getAllModels(): List<String> {
        val models = mutableListOf<String>()

        for (sectionName in config.keys) {
            models.add(sectionName)
        }

        return models
    }

    fun getModelsByVendor(vendor: String): List<String> {
        val models = mutableListOf<String>()
         for (model in getAllModels()) {
             val modelConfig = config[model] ?: continue
             if (modelConfig["vendor"] != vendor) {
                 continue
             }
             models.add(model)
         }

        return models
    }

    fun addModel(name: String, vendor: String, locale: String, folder: String, isOffline: Boolean, parameters: String): Int {
        try {
            val modelSection = "${vendor}-${locale}-$name"
            if (config.containsKey(modelSection)) {
                return RESULT_MODEL_ALREADY_EXISTS
            }

            val modelFolder = File(context.getExternalFilesDir(null), "/$vendor/$folder")
if (modelFolder.isFile) {
    return RESULT_ERROR
}
            if (!modelFolder.exists()) {
                modelFolder.mkdirs()
            }

            val section = config.add(modelSection)
            section.add("name", name)
            section.add("folder", folder)
            section.add("locale", locale)
            section.add("vendor", vendor)
            section.add("offline", isOffline.toString())
            section.add("parameters", Utils.encodeString(parameters))
            config.store(getConfigFile())
            return RESULT_OK
        } catch (e: Throwable) {
            e.printStackTrace()
            return RESULT_ERROR
        }
    }

    fun getModelConfig(model: Model): Ini {
        val ini = Ini()
        ini.config.isEscape = false

        val section = ini.add(getModelSectionNameFor(model))
        section.add("name", model.name)
        section.add("vendor", model.vendor)
        section.add("locale", model.locale)
        section.add("folder", model.folder)
        section.add("offline", model.isOffline.toString())
        section.add("parameters", Utils.encodeString(model.parameters))

        return ini
    }

    fun getModelsConfig(vendor: String): Ini {
        val ini = Ini()
        ini.config.isEscape = false

        val models = getModelsByVendor(vendor)

        for (model in models) {
            val section = config[model] ?: continue
            val newSection = ini.add(model)

            for ((key, value) in section) {
                newSection.add(key, value)
            }
        }

        return ini
    }

    fun removeModelsByVendor(vendor: String) {
        for (model in getModelsByVendor(vendor)) {
            val modelConfig = config[model] ?: continue

            // We have to remove model's data
            if (modelConfig["offline"] == "true") {
                val modelDir = File(context.getExternalFilesDir(null), "$vendor/${modelConfig["folder"]}")

                if (modelDir.exists() && modelDir.isDirectory) {
                    modelDir.deleteRecursively()
                } else if (modelDir.exists() && modelDir.isFile) {
                    modelDir.delete()
                }
            }

            config.remove(model)
        }

        config.store(getConfigFile())
    }

    data class Model(
        val name: String,
        val folder: String,
        val locale: String,
        val vendor: String,
        val isOffline: Boolean,
        val parameters: String
    ) {
        val preferenceName: String
            get() = "model_$folder"

        val modelSectionName: String
            get() = ModelConfigParser.getModelSectionNameFor(this)
    }

    fun getModel(modelName: String): Model {
        val model = config[modelName] ?: throw Exception("Model not found")

        return Model(
            model.getOrDefault("name", modelName),
            model.getOrDefault("folder", ""),
            model.getOrDefault("locale", ""),
            model.getOrDefault("vendor", ""),
            model.getOrDefault("offline", "false").toBoolean(),
            Utils.decodeString(
                model.getOrDefault("parameters", "")
            )
        )
    }

    fun getSpeechModels(vendor: String): List<Model> {
        val models = mutableListOf<Model>()

        for (model in getModelsByVendor(vendor)) {
            models.add(
                getModel(model)
            )
        }

        return models
    }

    fun removeModel(model: Model) {
        Log.i(TAG, "Removing model")
        for (modelName in getAllModels()) {
            val modelConfig = config[modelName] ?: continue

            if (modelConfig["folder"] == model.folder) {
                // delete model data
                val modelData = File(context.getExternalFilesDir(null), "${model.vendor}/${model.folder}")

                if (modelData.exists() && modelData.isFile) {
                    modelData.delete()
                } else if (modelData.exists() && modelData.isDirectory) {
                    modelData.deleteRecursively()
                }

                config.remove(modelName)
                config.store(getConfigFile())
                break
            }
        }

    }


    fun editModel(name: String, vendor: String, locale: String, folder: String, isOffline: Boolean, modelSectionName: String, parameters: String): Int {
        try {
            val modelSection = modelSectionName

            val modelFolder = File(context.getExternalFilesDir(null), "/$vendor/$folder")
            if (modelFolder.isFile) {
                return RESULT_ERROR
            }
            if (!modelFolder.exists()) {
                modelFolder.mkdirs()
            }

            var section = config[modelSectionName] ?: return  RESULT_ERROR
            config.remove(section)
            section = config.add(getModelSectionName(name, vendor, locale))
            section.add("name", name)
            section.add("folder", folder)
            section.add("locale", locale)
            section.add("vendor", vendor)
            section.add("offline", isOffline.toString())
            section.add("parameters", Utils.encodeString(parameters))
            config.store(getConfigFile())
            return RESULT_OK
        } catch (e: Throwable) {
            e.printStackTrace()
            return RESULT_ERROR
        }
    }

    // returns quontity of added models
    fun addModelsFromConfig(cfg: String): Int {
        val tmpIni = Ini(StringReader(cfg))
        tmpIni.config.isEscape = false

        for ((sectionName, sec) in tmpIni) {
            if (config.containsKey(sectionName)) {
                continue
            }

            val section = config.add(sectionName)

            for ((key, value) in sec) {
                section.add(key, value)
            }
        }

        config.store(getConfigFile())


        return tmpIni.keys.size
    }

    fun getDuplicates(cfg: String): List<String> {
        val ini = Ini(StringReader(cfg))
        ini.config.isEscape = false

        val duplicates = mutableListOf<String>()

        for ((key, section) in ini) {
            if (config[key] == null) {
                continue
            }

            val sb = StringBuilder()
                .append(key)
                .append(" ")
                .append(section["vendor"] ?: "")
                .append(" ")
                .append(section["locale"] ?: "")

            duplicates.add(sb.toString())
        }

        return duplicates
    }

    fun getAvailableLanguages(): Set<String> {
        val languages = mutableSetOf<String>()

        for ((_, section) in config) {
            languages.add(section["locale"] ?: continue)
        }

        return languages
    }

}