package com.github.smittv.multistt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.inputmethod.InputMethodManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import com.google.android.material.navigation.NavigationView
import android.widget.ListView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontVariation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.smittv.multistt.engines.EngineConfigParser
import com.github.smittv.multistt.engines.MicrosoftOfflineSpeechRecognitionEngine
import com.github.smittv.multistt.engines.ModelConfigParser
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.microsoft.cognitiveservices.speech.EmbeddedSpeechConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.progress.ProgressMonitor
import org.ini4j.Ini
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val REQUEST_VOICE_INPUT = 1
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var edit: SharedPreferences.Editor
    private lateinit var engineListAdapter: EngineListAdapter
    private lateinit var modelsAdapter: SpeechModelsAdapter
    private val zipScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    private val zipPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleImportedZip(uri)
        }
    }

    private val launcher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val micGranted = result[Manifest.permission.RECORD_AUDIO] == true || PermissionUtils.hasMicrophonePermission(this)
            if (!micGranted) {
                showMicRequestDialog()
            } else {
                showMainMenu()
            }
        }

    fun handleImportedZip(uri: Uri) {
        val progressBar = LinearProgressIndicator(this)
        progressBar.max = 100
        progressBar.progress = 0

        val dialog = AlertDialog.Builder(this, R.style.dialog)
            .setTitle(R.string.decompression_progress)
            .setView(progressBar)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                zipScope.cancel(CancellationException())
            }
            .create()

        dialog.show()

            zipScope.launch {
                try {
                    Toast.makeText(this@MainActivity, R.string.importing_file, Toast.LENGTH_SHORT).show()
                    withContext(Dispatchers.IO) {
                        val file = UriUtils.getZipFileFromUri(this@MainActivity, uri)
                        val zip = ZipFile(file)

                        zip.isRunInThread = true

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, R.string.extracting, Toast.LENGTH_SHORT).show()
                        }

                        val enginesHeader = zip.getFileHeader("engines.ini")

                        if (enginesHeader == null) {
                            throw IOException(getString(R.string.invalid_archive))
                        }

                        var enginesConfigText = ""
                        zip.getInputStream(enginesHeader).use { input ->
                            enginesConfigText = input.bufferedReader().readText()
                            println(enginesConfigText)
                        }

                        val modelsHeader = zip.getFileHeader("models.ini") ?: throw Exception(
                            getString(
                                R.string.invalid_archive
                            )
                        )
                        var modelsConfigText = ""


                        zip.getInputStream(modelsHeader).use { input ->
                            modelsConfigText = input.bufferedReader().readText()
                            println(modelsConfigText)
                        }

                        val duplicatedModels = ModelConfigParser(this@MainActivity).getDuplicates(modelsConfigText)

                        if (duplicatedModels.size > 0) {
                            withContext(Dispatchers.Main) {
                                showDuplicatesDialog(duplicatedModels)
                            }
                            throw CancellationException()
                        }

                        val folders = zip.fileHeaders.filter { it.isDirectory }

                        folders.forEach {
                            val destination =
                                getExternalFilesDir(null)!!.absolutePath

                            zip.extractFile(it, destination)
                            val progressMonitor = zip.progressMonitor

                            while (progressMonitor.state == ProgressMonitor.State.BUSY) {
                                withContext(Dispatchers.Main) {
                                    progressBar.progress = progressMonitor.percentDone
                                }

                                delay(100)
                            }
                        }

                        withContext(Dispatchers.Main) {



                            dialog.dismiss()
                        }

                        val addedModelsCount =
                            ModelConfigParser(this@MainActivity).addModelsFromConfig(modelsConfigText)
                        val addedEngineCount =
                            EngineConfigParser(this@MainActivity).addEnginesFromConfig(enginesConfigText)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(
                                    R.string.imported_models,
                                    addedEngineCount,
                                    addedModelsCount
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                            initListView()
                        }
                    }
                } catch (e: Throwable) {
                    Toast.makeText(
                        this@MainActivity, getString(R.string.error_during_importing) + " " + e.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    dialog.dismiss()
                }
            }
    }

    fun showDuplicatesDialog(duplicates: List<String>) {
        val dialog = AlertDialog.Builder(this, R.style.dialog)
            .setTitle(R.string.duplicates_detected)
            .setMessage(duplicates.joinToString(separator = "\n"))
            .setPositiveButton(android.R.string.ok, null)
            .create()

            dialog.show()
    }

    fun checkIMe() {
        val iMeManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        var isEnabled = false
        for (ime in iMeManager.enabledInputMethodList) {
            if (ime.packageName == packageName) {
                isEnabled = true
            }
        }

        if (!isEnabled) {
            askToEnableIMe()
        }

        val isDefault = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )?.contains(packageName) == true
        if (!isDefault) {
            iMeManager.showInputMethodPicker()
        }
    }

    fun askToEnableIMe() {
        val dialog = AlertDialog.Builder(this, R.style.dialog)
            .setTitle(R.string.enable_ime_in_settings)
            .setMessage(R.string.press_ok_then_turn_ime_on)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()

    }

    fun initListView() {
        checkIMe()

        val models = findViewById<ListView>(R.id.listView)
        val engineConfig = EngineConfigParser(this)

        val engineList = findViewById<RecyclerView>(R.id.engineList)
        engineList.layoutManager =
            LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        engineListAdapter = EngineListAdapter(

            EngineConfigParser(this).getNamesAndPaths().toMutableList()
        ) { engineFolder ->
            reinitModelsAdapter()
        }

        engineList.adapter = engineListAdapter

        val currentEngine = engineListAdapter.getCurrentEngine()

        Log.i(TAG, "Current engine $currentEngine")

        val modelItems = if (currentEngine == null) emptyList<ModelConfigParser.Model>() else ModelConfigParser(this).getSpeechModels(currentEngine)

        Log.i(TAG, "Models $modelItems")

        modelsAdapter = SpeechModelsAdapter(this,
            modelItems.toMutableList()
        ) {
                model ->

            edit
                .putString(Prefs.ENGINE, model.vendor)
                .putString(Prefs.LOCALE, model.locale)
                .putString(Prefs.MODEL, model.name)
                .apply()
        }
        models.adapter = modelsAdapter
    }

    override fun onStart() {
        super.onStart()

        if (::engineListAdapter.isInitialized) {
            reinitEngineListAdapter()
        }
        if (::modelsAdapter.isInitialized) {
            reinitModelsAdapter()
        }
    }

    fun reinitModelsAdapter() {
        val currentEngine = engineListAdapter.getCurrentEngine()

        Log.i(TAG, "Current engine $currentEngine")

        val modelItems = if (currentEngine == null) emptyList<ModelConfigParser.Model>() else ModelConfigParser(this).getSpeechModels(currentEngine)

        Log.i(TAG, "Models $modelItems")

        modelsAdapter.setItems(modelItems.toMutableList())
    }

    fun reinitEngineListAdapter() {
        engineListAdapter.setItems(
            EngineConfigParser(this).getNamesAndPaths()
        )
    }

    fun onSpeechModelSelected(pathToModel: String, modelName: String) {
        prefs.edit()
            .putString(Prefs.SPEECH_MODEL, modelName)
            .apply()
        prefs.edit()
            .putString(Prefs.SPEECH_MODEL_PATH, pathToModel)
            .apply()
        val licenseFile = File(pathToModel, "/license.txt")
        var license = ""
        if (licenseFile.exists()) {
            license = licenseFile.readText()
        }
        prefs.edit()
            .putString(Prefs.SPEECH_MODEL_LICENSE, license)
            .apply()
        MultiSTTService.instance?.updateSpeechRecognizer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        edit = prefs.edit()

        if (intent?.action == Intent.ACTION_VIEW) {
            intent?.data?.let {
                handleImportedZip(it)
            }
        }

        if (!PermissionUtils.hasMicrophonePermission(this)) {
            setContentView(R.layout.request_permissions_page)

            findViewById<Button>(R.id.continueButton).setOnClickListener { _ ->


                val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                if (PermissionUtils.hasMicrophonePermission(this)) {
                    showMainMenu()
                } else {
                    launcher.launch(permissions.toTypedArray())
                }

            }
        } else {
            showMainMenu()
        }

    }

    fun showMainMenu() {
        setContentView(R.layout.activity_main)

        val voiceInput = findViewById<ImageButton>(R.id.btnVoiceInput)

        voiceInput.setOnClickListener {
            if (PermissionUtils.hasMicrophonePermission(this)) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_MAX_RESULTS, 1
                    )
                }
                startActivityForResult(intent, REQUEST_VOICE_INPUT)
            } else {
                launcher.launch(
                    arrayOf(Manifest.permission.RECORD_AUDIO)
                )
            }
        }

        val importModel = findViewById<ImageButton>(R.id.btnImport)
        importModel.setOnClickListener { _ ->
            zipPicker.launch(arrayOf("application/zip", "application/octet-stream"))
        }
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)


        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.open_drawer,
            R.string.close_drawer
        )

        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val navView = findViewById<NavigationView>(R.id.navigationView)

        val version = navView.getHeaderView(0).findViewById<TextView>(R.id.appVersion)
        val v = packageManager
            .getPackageInfo(packageName, 0)
            .versionName

        version.text = getString(R.string.version, v)

        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.open_settings -> openSettings()
                R.id.telegram_channel -> openTelegramChannel()
                R.id.help -> openHelp()
                R.id.view_log -> openViewLog()
                R.id.engines -> openEngines()
                R.id.telegram_resources_channel -> openResourcesChannel()
                else -> null
            }
            drawerLayout.closeDrawers()
            true
        }
        initListView()
    }

    fun openEngines() {
        startActivity(Intent(this, EnginesActivity::class.java))
    }

    fun openViewLog() {
        startActivity(Intent(this, ViewLogActivity::class.java))
    }

    fun openHelp() {
        startActivity(Intent(this, HelpActivity::class.java))
    }

    fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun openResourcesChannel() {
        startActivity(
            Intent(Intent.ACTION_VIEW,
                Uri.parse("https://t.me/multi_stt_resources"))
        )
    }

    fun openTelegramChannel() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(("https://t.me/smit_tv_official"))))
    }

    fun showMicRequestDialog() {
        val dialog = AlertDialog.Builder(this, R.style.dialog)
            .setTitle(R.string.microphone)
            .setMessage(R.string.needed_for_voice_recording)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) {_, _ ->
                if (!PermissionUtils.canShowMicrophonePermissionRequest(this)) {
                    PermissionUtils.openAppSettings(this)
                } else {
                    launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            }
            .create()
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(
            TAG,
            "onActivityResult"
                    + "request code "
                    + requestCode.toString()
                    + " result code "
                    + resultCode.toString()
        )

        when (requestCode) {
            REQUEST_VOICE_INPUT -> onRequestVoiceInputResult(resultCode, data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

        fun onRequestVoiceInputResult(resultCode: Int, data: Intent?) {
            Log.i(TAG, "onRequestVoiceInputResult")

        if (resultCode != RESULT_OK || data == null) {
            Log.w(TAG, "fail to obtain result")
            return
        }

        val resultText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: run {
            Log.w(TAG, "no data received" + data.extras.toString())
            return
        }

        val testEditText = findViewById<EditText>(R.id.test_edit)

        testEditText.setText(resultText.firstOrNull())
    }
}