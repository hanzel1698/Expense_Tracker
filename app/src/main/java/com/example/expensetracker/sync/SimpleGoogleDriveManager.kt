package com.example.expensetracker.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class SimpleGoogleDriveManager(private val context: Context) {
    
    private val HTTP_TRANSPORT = NetHttpTransport()
    private val JSON_FACTORY = GsonFactory.getDefaultInstance()
    private val APP_FOLDER = "ExpenseTracker_Backups"
    
    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null
    
    companion object {
        private const val REQUEST_SIGN_IN = 1001
        private const val MIME_TYPE_JSON = "application/json"
    }
    
    init {
        setupGoogleSignIn()
    }
    
    private fun setupGoogleSignIn() {
        // Simplified Google Sign-In without requiring OAuth client ID
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }
    
    suspend fun signIn(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null && account.email != null) {
                    createDriveService(account)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            googleSignInClient.signOut()
            driveService = null
        }
    }
    
    private fun createDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        
        driveService = Drive.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, credential
        ).setApplicationName("ExpenseTracker").build()
    }
    
    fun initializeDriveService(account: GoogleSignInAccount) {
        Log.d("SimpleGoogleDriveManager", "initializeDriveService called with account: ${account.email}")
        createDriveService(account)
        Log.d("SimpleGoogleDriveManager", "Drive service initialization completed")
    }
    
    suspend fun isSignedIn(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                val hasAccount = account != null && account.email != null
                val hasDriveService = driveService != null
                Log.d("SimpleGoogleDriveManager", "isSignedIn check - account: $hasAccount, driveService: $hasDriveService")
                hasAccount && hasDriveService
            }
        } catch (e: Exception) {
            Log.e("SimpleGoogleDriveManager", "Error checking sign-in status", e)
            false
        }
    }
    
    suspend fun uploadBackup(jsonData: String): Result<BackupInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = driveService ?: return@withContext Result.failure(Exception("Not signed in"))
                
                // Create app folder if it doesn't exist
                val appFolder = getOrCreateAppFolder(drive)
                
                // Create file metadata
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                val fileName = "expense_data_$timestamp.json"
                
                val fileMetadata = File().apply {
                    name = fileName
                    parents = listOf(appFolder.id)
                }
                
                // Upload file
                val uploadedFile = drive.files().create(fileMetadata, 
                    com.google.api.client.http.ByteArrayContent.fromString(MIME_TYPE_JSON, jsonData)
                ).execute()
                
                val backupInfo = BackupInfo(
                    fileId = uploadedFile.id ?: "",
                    fileName = uploadedFile.name?.toString() ?: fileName,
                    modifiedTime = uploadedFile.modifiedTime?.toString() ?: "",
                    size = "${uploadedFile.size ?: 0} bytes"
                )
                
                Result.success(backupInfo)
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun downloadBackup(fileId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = driveService ?: return@withContext Result.failure(Exception("Not signed in"))
                
                val outputStream = ByteArrayOutputStream()
                drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                
                val jsonData = String(outputStream.toByteArray())
                Result.success(jsonData)
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun listBackups(): Result<List<BackupInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = driveService ?: return@withContext Result.failure(Exception("Not signed in"))
                
                val appFolder = getOrCreateAppFolder(drive)
                
                val result = drive.files().list()
                    .setQ("name contains 'expense_data_' and '${appFolder.id}' in parents and trashed=false")
                    .setOrderBy("modifiedTime desc")
                    .setFields("files(id, name, modifiedTime, size)")
                    .execute()
                
                val backups = result.files?.map { file ->
                    BackupInfo(
                        fileId = file.id ?: "",
                        fileName = file.name?.toString() ?: "",
                        modifiedTime = file.modifiedTime?.toString() ?: "",
                        size = "${file.size ?: 0} bytes"
                    )
                } ?: emptyList()
                
                Result.success(backups)
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun deleteBackup(fileId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val drive = driveService ?: return@withContext Result.failure(Exception("Not signed in"))
                drive.files().delete(fileId).execute()
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private suspend fun getOrCreateAppFolder(drive: Drive): File {
        return withContext(Dispatchers.IO) {
            try {
                // Check if app folder exists
                val result = drive.files().list()
                    .setQ("name='$APP_FOLDER' and mimeType='application/vnd.google-apps.folder' and trashed=false")
                    .setFields("files(id, name)")
                    .execute()
                
                val existingFolder = result.files?.firstOrNull()
                if (existingFolder != null) {
                    return@withContext existingFolder
                }
                
                // Create new folder
                val folderMetadata = File().apply {
                    name = APP_FOLDER
                    mimeType = "application/vnd.google-apps.folder"
                }
                
                drive.files().create(folderMetadata).setFields("id, name").execute()
                
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    fun getGoogleSignInClient(): GoogleSignInClient {
        return googleSignInClient
    }
}
