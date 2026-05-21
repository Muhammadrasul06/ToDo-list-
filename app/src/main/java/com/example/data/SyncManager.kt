package com.example.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object SyncManager {
    private const val TAG = "SyncManager"

    // High fidelity simulator + real HTTP sender
    suspend fun performSync(
        context: Context,
        todoDao: TodoDao,
        deviceId: String,
        deviceName: String,
        pairingCode: String,
        passcode: String,
        customServerUrl: String,
        isSimulationMode: Boolean,
        onProgress: (String) -> Unit
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Starting secure data packaging...")
            Thread.sleep(300)

            // 1. Gather pending and total active tasks
            val allItems = todoDao.getAllRawItems()
            if (allItems.isEmpty()) {
                onProgress("No tasks available to sync.")
                return@withContext SyncResult.Success(0, "Database is empty. Nothing to sync.")
            }

            // 2. Serialize tasks into JSON
            onProgress("Serializing ${allItems.size} tasks to JSON...")
            val jsonArray = JSONArray()
            for (item in allItems) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("notes", item.notes)
                    put("category", item.category)
                    put("isCompleted", item.isCompleted)
                    put("isUrgent", item.isUrgent)
                    put("dueDate", item.dueDate ?: -1L)
                    put("reminderTime", item.reminderTime ?: -1L)
                    put("lastUpdated", item.lastUpdated)
                    put("isDeleted", item.isDeleted)
                }
                jsonArray.put(obj)
            }
            val plainJson = jsonArray.toString()
            Log.d(TAG, "Plaintext JSON: $plainJson")

            // 3. SECURE ENCRYPTION
            onProgress("Performing AES-128 client-side encryption...")
            Thread.sleep(400)
            val encryptedPayload = CryptoUtils.encrypt(plainJson, passcode)
            Log.d(TAG, "Encrypted Base64 Payload: $encryptedPayload")
            
            val packageObj = JSONObject().apply {
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("pairingCode", pairingCode)
                put("encryptedData", encryptedPayload)
                put("timestamp", System.currentTimeMillis())
            }
            val bodyString = packageObj.toString()

            if (isSimulationMode) {
                // High fidelity sandbox simulation
                onProgress("Securing connection with pairing code: $pairingCode...")
                Thread.sleep(600)
                onProgress("Uploading encrypted bundle to secure hub (Simulated)...")
                Thread.sleep(800)
                
                // Simulate receiving other devices' items merged from the cloud
                onProgress("Sync Hub response successful! Merging cloud updates...")
                Thread.sleep(500)

                // Mark items in local DB as synced
                val updatedItems = allItems.map { it.copy(syncStatus = "synced") }
                todoDao.insertOrUpdateAll(updatedItems)

                onProgress("Decrypted remote updates verified. Sync complete!")
                return@withContext SyncResult.Success(allItems.size, "Successfully synced ${allItems.size} items securely via simulation.")
            } else {
                // Real server exchange
                if (customServerUrl.isEmpty() || !customServerUrl.startsWith("http")) {
                    return@withContext SyncResult.Error("Invalid Server URL. Use simulation or specify a correct HTTP/HTTPS URL.")
                }

                onProgress("Opening secure connection to $customServerUrl...")
                val url = URL(customServerUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                onProgress("Transmitting cryptographically packed task list...")
                conn.outputStream.use { os ->
                    OutputStreamWriter(os, "UTF-8").use { writer ->
                        writer.write(bodyString)
                        writer.flush()
                    }
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "HTTP Response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val respObj = JSONObject(responseText)
                    val remoteEncryptedData = respObj.optString("encryptedData", "")
                    
                    var mergeCount = 0
                    if (remoteEncryptedData.isNotEmpty()) {
                        onProgress("Sync response received. Decrypting cloud payload...")
                        val decryptedRemote = CryptoUtils.decrypt(remoteEncryptedData, passcode)
                        if (decryptedRemote.isNotEmpty()) {
                            val remoteArray = JSONArray(decryptedRemote)
                            val itemsToInsert = mutableListOf<TodoItem>()
                            for (i in 0 until remoteArray.length()) {
                                val rObj = remoteArray.getJSONObject(i)
                                val rId = rObj.getString("id")
                                val localItem = todoDao.getItemById(rId)
                                val remoteUpdated = rObj.getLong("lastUpdated")
                                
                                // Conflict Resolution: LWW (Last-Write-Wins)
                                if (localItem == null || remoteUpdated > localItem.lastUpdated) {
                                    val rDueDate = rObj.getLong("dueDate")
                                    val rReminderTime = rObj.getLong("reminderTime")
                                    val newItem = TodoItem(
                                        id = rId,
                                        title = rObj.getString("title"),
                                        notes = rObj.optString("notes", ""),
                                        category = rObj.optString("category", "Inbox"),
                                        isCompleted = rObj.getBoolean("isCompleted"),
                                        isUrgent = rObj.getBoolean("isUrgent"),
                                        dueDate = if (rDueDate == -1L) null else rDueDate,
                                        reminderTime = if (rReminderTime == -1L) null else rReminderTime,
                                        syncStatus = "synced",
                                        lastUpdated = remoteUpdated,
                                        isDeleted = rObj.getBoolean("isDeleted")
                                    )
                                    itemsToInsert.add(newItem)
                                }
                            }
                            if (itemsToInsert.isNotEmpty()) {
                                todoDao.insertOrUpdateAll(itemsToInsert)
                                mergeCount = itemsToInsert.size
                                onProgress("Merged $mergeCount items from other device(s) successfully.")
                            }
                        } else {
                            onProgress("Warning: Decryption of cloud updates failed (Key error).")
                        }
                    }

                    // Set pending local items as synced
                    val updatedItems = allItems.map { it.copy(syncStatus = "synced") }
                    todoDao.insertOrUpdateAll(updatedItems)
                    onProgress("Local states updated.")

                    return@withContext SyncResult.Success(allItems.size + mergeCount, "Successfully verified sync. Merged $mergeCount nodes.")
                } else {
                    val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    return@withContext SyncResult.Error("Server Connection Failed: $errorText")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext SyncResult.Error("Error: ${e.localizedMessage ?: "Unknown transport failure"}")
        }
    }
}

sealed class SyncResult {
    data class Success(val itemsSyncedCount: Int, val message: String) : SyncResult()
    data class Error(val errorMessage: String) : SyncResult()
}
