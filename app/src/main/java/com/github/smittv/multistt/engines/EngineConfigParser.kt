package com.github.smittv.multistt.engines

import android.content.Context
import org.ini4j.Ini
import java.io.File
import java.io.StringReader

class EngineConfigParser(
    val context: Context
) {
    companion object {
        const val RESULT_OK = 0
        const val RESULT_ERROR = 1
        const val RESULT_ENGINE_ALREADY_EXISTS = 2
    }
    private val config = Ini()

    init {
        config.config.isEscape = false
        init()
    }

    fun getConfigFile() = File(context.getExternalFilesDir(null), "/engines.ini")

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

    fun getAvailableEngines(): List<String> {
        val list = mutableListOf<String>()
        for (sectionName in config.keys) {
            list.add(sectionName)
        }

        return list
    }

    data class EngineInfo(
        val name: String,
        val type: String
    )

    fun addEngine(name: String, isOffline: Boolean): Int {
        try {
            val engineDir = File(context.getExternalFilesDir(null), "/$name")
            if (!engineDir.exists()) {
                engineDir.mkdirs()
            } else if (engineDir.isFile) {
                return RESULT_ERROR
            } else if (engineDir.isDirectory) {
                return  RESULT_ENGINE_ALREADY_EXISTS
            }

            val configFile = getConfigFile()
            val section = config.add(name)
            section.add("offline", isOffline.toString())
            config.store(configFile)
            return RESULT_OK
        } catch (e: Throwable) {
            e.printStackTrace()
            return RESULT_ERROR
        }
    }

    fun getNamesAndPaths(): List<Pair<String, String>> {
        val engines = mutableListOf<Pair<String, String>>()

        for (engineName in getAvailableEngines()) {
            engines.add(
                Pair(
                    engineName,
                    engineName
                )
            )
        }

        return engines
    }

    fun getEngineConfig(engine: String): Ini {
        val section = config[engine] ?: return Ini()
        val ini = Ini()
        val newSection = ini.add(section.name)

        for ((key, value) in section) {
            newSection.add(key, value)
        }

        return ini
    }

    fun removeEngine(engine: String) {
        config.remove(engine)
        config.store(getConfigFile())
        // We should remove engine data
        try {
            val engineFile = File(context.getExternalFilesDir(null), engine)
            if (engineFile.exists()) {
                engineFile.deleteRecursively()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // Don't forget to remove engine's models as well
        ModelConfigParser(context).removeModelsByVendor(engine)
    }

    // returns quontity of added engines
    fun addEnginesFromConfig(cfg: String): Int {
        val tmpIni = Ini(StringReader(cfg))

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
}