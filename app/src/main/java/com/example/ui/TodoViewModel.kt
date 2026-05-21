package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NotificationHelper
import com.example.data.SyncManager
import com.example.data.SyncResult
import com.example.data.TodoDatabase
import com.example.data.TodoItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TodoDatabase.getDatabase(application)
    private val todoDao = db.todoDao()

    // UI States
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Central tasks list filtered dynamically
    val todoItems: StateFlow<List<TodoItem>> = combine(
        todoDao.getAllActiveItems(),
        _selectedCategory,
        _searchQuery
    ) { items, category, query ->
        items.filter { item ->
            val matchesCategory = category == "All" || item.category.equals(category, ignoreCase = true)
            val matchesQuery = query.isEmpty() || 
                    item.title.contains(query, ignoreCase = true) || 
                    item.notes.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Voice UI States
    private val _isVoiceListening = MutableStateFlow(false)
    val isVoiceListening: StateFlow<Boolean> = _isVoiceListening.asStateFlow()

    private val _voiceStatus = MutableStateFlow("Tap mic to start speaking")
    val voiceStatus: StateFlow<String> = _voiceStatus.asStateFlow()

    private val _voiceTextFieldBuffer = MutableStateFlow("")
    val voiceTextFieldBuffer: StateFlow<String> = _voiceTextFieldBuffer.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    // Reminders selection
    private val _tempReminderTime = MutableStateFlow<Long?>(null)
    val tempReminderTime: StateFlow<Long?> = _tempReminderTime.asStateFlow()

    // Sync state
    private val _syncingState = MutableStateFlow<SyncStatusState>(SyncStatusState.Idle)
    val syncingState: StateFlow<SyncStatusState> = _syncingState.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    private val _syncServerUrl = MutableStateFlow("")
    val syncServerUrl: StateFlow<String> = _syncServerUrl.asStateFlow()

    private val _syncPasscode = MutableStateFlow("MySecureSync123")
    val syncPasscode: StateFlow<String> = _syncPasscode.asStateFlow()

    private val _deviceId = MutableStateFlow(UUID.randomUUID().toString().take(6))
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _deviceName = MutableStateFlow(android.os.Build.MODEL ?: "Android Device")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _pairingCode = MutableStateFlow("8899")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _syncSimulationMode = MutableStateFlow(true)
    val syncSimulationMode: StateFlow<Boolean> = _syncSimulationMode.asStateFlow()

    // Biometric & Lock screen
    private val _isAppLocked = MutableStateFlow(true) // Start locked for biometric privacy
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(true)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _masterPin = MutableStateFlow("1234") // Fallback PIN code
    val masterPin: StateFlow<String> = _masterPin.asStateFlow()

    // Dark Theme option
    private val _isDarkMode = MutableStateFlow(true) // Preferred dark mode default
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    init {
        // Load initial values from SharedPreferences
        val prefs = application.getSharedPreferences("todo_settings", Context.MODE_PRIVATE)
        _syncServerUrl.value = prefs.getString("sync_server_url", "") ?: ""
        _syncPasscode.value = prefs.getString("sync_passcode", "MySecureSync123") ?: "MySecureSync123"
        _pairingCode.value = prefs.getString("pairing_code", "8899") ?: "8899"
        _deviceId.value = prefs.getString("device_id", UUID.randomUUID().toString().take(6)) ?: ""
        _syncSimulationMode.value = prefs.getBoolean("sync_simulation", true)
        _isBiometricEnabled.value = prefs.getBoolean("biometric_enabled", true)
        _masterPin.value = prefs.getString("master_pin", "1234") ?: "1234"
        _isDarkMode.value = prefs.getBoolean("dark_mode", true)
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        getApplication<Application>().getSharedPreferences("todo_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("dark_mode", enabled).apply()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        _isBiometricEnabled.value = enabled
        getApplication<Application>().getSharedPreferences("todo_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("biometric_enabled", enabled).apply()
    }

    fun setMasterPin(pin: String) {
        _masterPin.value = pin
        getApplication<Application>().getSharedPreferences("todo_settings", Context.MODE_PRIVATE)
            .edit().putString("master_pin", pin).apply()
    }

    fun saveSyncSettings(url: String, passcode: String, pairing: String, isSimulated: Boolean) {
        _syncServerUrl.value = url
        _syncPasscode.value = passcode
        _pairingCode.value = pairing
        _syncSimulationMode.value = isSimulated

        getApplication<Application>().getSharedPreferences("todo_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("sync_server_url", url)
            .putString("sync_passcode", passcode)
            .putString("pairing_code", pairing)
            .putBoolean("sync_simulation", isSimulated)
            .apply()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        _isAppLocked.value = true
    }

    // Insert Task
    fun saveTodo(
        id: String?,
        title: String,
        notes: String,
        category: String,
        isUrgent: Boolean,
        dueDate: Long?,
        reminderTime: Long?
    ) {
        viewModelScope.launch {
            val todoId = id ?: UUID.randomUUID().toString()
            val finalReminder = reminderTime ?: _tempReminderTime.value
            
            val item = TodoItem(
                id = todoId,
                title = title,
                notes = notes,
                category = category,
                isCompleted = false,
                isUrgent = isUrgent,
                dueDate = dueDate,
                reminderTime = finalReminder,
                syncStatus = "pending",
                lastUpdated = System.currentTimeMillis()
            )

            todoDao.insertOrUpdate(item)
            _tempReminderTime.value = null // clear temp reminder

            // Schedule Notification Alarm
            if (finalReminder != null && finalReminder > System.currentTimeMillis()) {
                NotificationHelper.scheduleAlarm(getApplication(), item)
            }
        }
    }

    // Toggle Complete
    fun toggleCompleted(item: TodoItem) {
        viewModelScope.launch {
            val updated = item.copy(
                isCompleted = !item.isCompleted,
                syncStatus = "pending",
                lastUpdated = System.currentTimeMillis()
            )
            todoDao.insertOrUpdate(updated)
            
            // If completed, cancel any outstanding reminder
            if (updated.isCompleted) {
                NotificationHelper.cancelAlarm(getApplication(), updated)
            } else if (updated.reminderTime != null && updated.reminderTime > System.currentTimeMillis()) {
                NotificationHelper.scheduleAlarm(getApplication(), updated)
            }
        }
    }

    // Delete Task
    fun deleteTodo(item: TodoItem) {
        viewModelScope.launch {
            // Cancel reminders
            NotificationHelper.cancelAlarm(getApplication(), item)
            
            // Soft delete to propagate over Sync securely
            todoDao.softDeleteById(item.id)
        }
    }

    fun updateTempReminder(timestamp: Long?) {
        _tempReminderTime.value = timestamp
    }

    // --- SECURE DEVICE SYNC ENGINE ---
    fun runSecureSync() {
        if (_syncingState.value is SyncStatusState.Syncing) return

        _syncLogs.value = emptyList()
        _syncingState.value = SyncStatusState.Syncing

        addLog("Establishing cryptographic session...")
        addLog("Device metadata identified: [${_deviceName.value}] id: ${_deviceId.value}")

        viewModelScope.launch {
            val result = SyncManager.performSync(
                context = getApplication(),
                todoDao = todoDao,
                deviceId = _deviceId.value,
                deviceName = _deviceName.value,
                pairingCode = _pairingCode.value,
                passcode = _syncPasscode.value,
                customServerUrl = _syncServerUrl.value,
                isSimulationMode = _syncSimulationMode.value,
                onProgress = { log -> addLog(log) }
            )

            when (result) {
                is SyncResult.Success -> {
                    _syncingState.value = SyncStatusState.Success(result.message)
                    addLog("Final Status: Sync verification success.")
                }
                is SyncResult.Error -> {
                    _syncingState.value = SyncStatusState.Failure(result.errorMessage)
                    addLog("Final Status: Sync failure. ${result.errorMessage}")
                }
            }
        }
    }

    private fun addLog(message: String) {
        val currentLogs = _syncLogs.value.toMutableList()
        currentLogs.add("[${System.currentTimeMillis() % 100000}] $message")
        _syncLogs.value = currentLogs
    }

    // --- NATIVE SPEECH TO TEXT CONTROLLER ---
    fun startSpeechToText(context: Context) {
        _voiceTextFieldBuffer.value = ""
        
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceStatus.value = "Speech recognition is not available on this device."
            _isVoiceListening.value = false
            return
        }

        try {
            _isVoiceListening.value = true
            _voiceStatus.value = "Initializing micro..."

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceStatus.value = "Listening... Speak now."
                    }

                    override fun onBeginningOfSpeech() {
                        _voiceStatus.value = "Processing voice waves..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceStatus.value = "Transcribing speech..."
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client feedback error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission denied"
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No matches found"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic busy"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input received"
                            else -> "Mic initialized: Tap below to input"
                        }
                        _voiceStatus.value = message
                        _isVoiceListening.value = false
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val speechText = matches[0]
                            _voiceTextFieldBuffer.value = speechText
                            _voiceStatus.value = "Transcription success!"
                        } else {
                            _voiceStatus.value = "No speech matched."
                        }
                        _isVoiceListening.value = false
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            _voiceStatus.value = "Speech Recognition error: ${e.localizedMessage}"
            _isVoiceListening.value = false
        }
    }

    fun stopSpeechToText() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isVoiceListening.value = false
    }

    fun simulateVoiceInput(text: String) {
        _voiceTextFieldBuffer.value = text
        _voiceStatus.value = "Speech simulated successfully!"
        _isVoiceListening.value = false
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
    }
}

sealed class SyncStatusState {
    object Idle : SyncStatusState()
    object Syncing : SyncStatusState()
    data class Success(val msg: String) : SyncStatusState()
    data class Failure(val error: String) : SyncStatusState()
}
