package com.example.expensetracker.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class BackupInfo(
    val fileId: String,
    val fileName: String,
    val modifiedTime: String,
    val size: String
)

/**
 * Backs expense backups with a user-picked folder via the Storage Access Framework rather than
 * the Drive REST API. The REST API's `drive.file` scope can only ever see files/folders the app
 * itself created under the current OAuth client identity — it can't be pointed at a folder the
 * user already has, and that identity changes whenever the app's signing key or registered OAuth
 * client changes (e.g. a CI resign). SAF's folder picker instead lets the user explicitly select
 * their existing Drive folder (or any other storage location), with access persisted independent
 * of any OAuth scope.
 *
 * Google Sign-In is kept only for the "connected account" identity/gate in the UI, not for Drive
 * file access.
 */
class SimpleGoogleDriveManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    suspend fun signIn(): Boolean = withContext(Dispatchers.IO) {
        GoogleSignIn.getLastSignedInAccount(context)?.email != null
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        googleSignInClient.signOut()
    }

    suspend fun isSignedIn(): Boolean = withContext(Dispatchers.IO) {
        GoogleSignIn.getLastSignedInAccount(context)?.email != null
    }

    fun initializeDriveService(account: GoogleSignInAccount) {
        // No Drive service to initialize under the SAF-based backup path; kept so callers that
        // re-attach a cached sign-in on cold start don't need to know that changed.
    }

    fun getGoogleSignInClient(): GoogleSignInClient = googleSignInClient

    /** The backup folder the user picked via [saveFolderUri], if its access grant is still held. */
    fun getFolderUri(): Uri? {
        val stored = prefs.getString(KEY_FOLDER_URI, null) ?: return null
        val uri = Uri.parse(stored)
        val stillGranted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return if (stillGranted) uri else null
    }

    fun hasBackupFolder(): Boolean = getFolderUri() != null

    /** Persists the folder [uri] returned by an `ACTION_OPEN_DOCUMENT_TREE` picker result. */
    fun saveFolderUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    private fun folderDocument(): DocumentFile? {
        val uri = getFolderUri() ?: return null
        return DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory }
    }

    suspend fun uploadBackup(jsonData: String): Result<BackupInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val folder = folderDocument()
                    ?: return@withContext Result.failure(Exception("No backup folder selected"))

                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                val fileName = "expense_data_$timestamp.json"

                val file = folder.createFile(MIME_TYPE_JSON, fileName)
                    ?: return@withContext Result.failure(Exception("Could not create backup file in the selected folder"))

                val bytes = jsonData.toByteArray()
                context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
                    ?: return@withContext Result.failure(Exception("Could not write backup file"))

                Result.success(
                    BackupInfo(
                        fileId = file.uri.toString(),
                        fileName = file.name ?: fileName,
                        modifiedTime = LocalDateTime.now().toString(),
                        size = "${bytes.size} bytes"
                    )
                )
            } catch (e: Exception) {
                Log.e("SimpleGoogleDriveManager", "uploadBackup failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun downloadBackup(fileId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(fileId)
                val jsonData = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext Result.failure(Exception("Could not read backup file"))
                Result.success(jsonData)
            } catch (e: Exception) {
                Log.e("SimpleGoogleDriveManager", "downloadBackup failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun listBackups(): Result<List<BackupInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val folder = folderDocument()
                    ?: return@withContext Result.failure(Exception("No backup folder selected"))

                val backups = folder.listFiles()
                    .filter { it.isFile && it.name?.let { n -> n.startsWith("expense_data_") && n.endsWith(".json") } == true }
                    .sortedByDescending { it.lastModified() }
                    .map {
                        BackupInfo(
                            fileId = it.uri.toString(),
                            fileName = it.name ?: "",
                            modifiedTime = it.lastModified().toString(),
                            size = "${it.length()} bytes"
                        )
                    }

                Result.success(backups)
            } catch (e: Exception) {
                Log.e("SimpleGoogleDriveManager", "listBackups failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun deleteBackup(fileId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(fileId)
                val deleted = DocumentFile.fromSingleUri(context, uri)?.delete() == true
                Result.success(deleted)
            } catch (e: Exception) {
                Log.e("SimpleGoogleDriveManager", "deleteBackup failed", e)
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "drive_backup_prefs"
        private const val KEY_FOLDER_URI = "backup_folder_uri"
        private const val MIME_TYPE_JSON = "application/json"
    }
}
