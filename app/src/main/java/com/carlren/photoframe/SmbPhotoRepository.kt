package com.carlren.photoframe

import android.content.Context
import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SmbPhotoRepository {
    private const val TAG = "SmbPhotoRepository"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "bmp")
    private val fallbackHost = BuildConfig.SMB_FALLBACK_HOST.trim()

    data class SmbPhoto(val name: String, val remotePath: String)

    suspend fun testConnection(creds: SmpCredentials): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val photos = listRemotePhotosInternal(creds, creds.host)
            Result.success(photos.size)
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed (${e.javaClass.simpleName})")
            // Retry with fallback IP if .local fails
            if (shouldUseFallback(creds.host) && isUnknownHost(e)) {
                try {
                    Log.w(TAG, "Retrying with configured fallback host")
                    val fallbackCreds = creds.copy(host = fallbackHost)
                    val photos2 = listRemotePhotosInternal(fallbackCreds, fallbackHost)
                    return@withContext Result.success(photos2.size)
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback connection failed (${e2.javaClass.simpleName})")
                    return@withContext Result.failure(e2)
                }
            }
            Result.failure(e)
        }
    }

    private fun shouldUseFallback(host: String): Boolean =
        fallbackHost.isNotBlank() &&
            !host.equals(fallbackHost, ignoreCase = true) &&
            host.endsWith(".local", ignoreCase = true)

    private fun isUnknownHost(e: Exception): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is java.net.UnknownHostException) return true
            if (t.message?.contains("unknown host", true) == true) return true
            t = t.cause
        }
        return false
    }

    suspend fun listRemotePhotos(creds: SmpCredentials): List<SmbPhoto> = withContext(Dispatchers.IO) {
        try {
            listRemotePhotosInternal(creds, creds.host)
        } catch (e: Exception) {
            if (shouldUseFallback(creds.host) && isUnknownHost(e)) {
                Log.w(TAG, "Listing photos with configured fallback host")
                listRemotePhotosInternal(creds.copy(host = fallbackHost), fallbackHost)
            } else throw e
        }
    }

    private suspend fun listRemotePhotosInternal(creds: SmpCredentials, host: String): List<SmbPhoto> = withContext(Dispatchers.IO) {
        val client = SMBClient()
        var connection: com.hierynomus.smbj.connection.Connection? = null
        var session: com.hierynomus.smbj.session.Session? = null
        var share: DiskShare? = null
        try {
            Log.d(TAG, "Connecting to configured SMB server")
            connection = client.connect(host)
            val auth = AuthenticationContext(creds.username, creds.password.toCharArray(), "")
            session = connection.authenticate(auth)
            share = session.connectShare(creds.share) as DiskShare
            val folder = creds.path.trim().trim('/').let { if (it.isEmpty()) "" else it }
            Log.d(TAG, "Listing configured photo folder")
            val infos = share.list(folder, "*")
            val photos = mutableListOf<SmbPhoto>()
            for (info in infos) {
                val name = info.fileName
                if (name == "." || name == "..") continue
                val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value.toLong()) != 0L
                if (isDir) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in IMAGE_EXTENSIONS) {
                    val remotePath = if (folder.isEmpty()) name else "$folder/$name"
                    photos.add(SmbPhoto(name, remotePath))
                }
            }
            photos.sortedBy { it.name.lowercase() }
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    suspend fun downloadPhoto(
        context: Context,
        creds: SmpCredentials,
        photo: SmbPhoto
    ): File? = withContext(Dispatchers.IO) {
        downloadPhotoInternal(context, creds, creds.host, photo) ?: run {
            if (shouldUseFallback(creds.host)) {
                Log.w(TAG, "Downloading with configured fallback host")
                downloadPhotoInternal(context, creds.copy(host = fallbackHost), fallbackHost, photo)
            } else null
        }
    }

    private suspend fun downloadPhotoInternal(
        context: Context,
        creds: SmpCredentials,
        host: String,
        photo: SmbPhoto
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "smb_photos").apply { mkdirs() }
            val localFile = File(cacheDir, photo.name)
            if (localFile.exists() && localFile.length() > 0) return@withContext localFile
            val client = SMBClient()
            var connection: com.hierynomus.smbj.connection.Connection? = null
            var session: com.hierynomus.smbj.session.Session? = null
            var share: DiskShare? = null
            try {
                connection = client.connect(host)
                val auth = AuthenticationContext(creds.username, creds.password.toCharArray(), "")
                session = connection.authenticate(auth)
                share = session.connectShare(creds.share) as DiskShare
                val smbFile = share.openFile(
                    photo.remotePath,
                    setOf(AccessMask.FILE_READ_DATA),
                    setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    setOf(SMB2ShareAccess.FILE_SHARE_READ),
                    SMB2CreateDisposition.FILE_OPEN,
                    setOf(SMB2CreateOptions.FILE_SEQUENTIAL_ONLY)
                )
                smbFile.use { f ->
                    FileOutputStream(localFile).use { out ->
                        val input = f.inputStream
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                    }
                }
                Log.d(TAG, "Downloaded photo to app cache")
                localFile
            } finally {
                try { share?.close() } catch (_: Exception) {}
                try { session?.close() } catch (_: Exception) {}
                try { connection?.close() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Photo download failed (${e.javaClass.simpleName})")
            null
        }
    }

    suspend fun ensurePhotosCached(
        context: Context,
        creds: SmpCredentials,
        remotePhotos: List<SmbPhoto>
    ): List<File> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        for (photo in remotePhotos) {
            val f = downloadPhoto(context, creds, photo)
            if (f != null && f.exists()) files.add(f)
        }
        files
    }

    fun clearCache(context: Context) {
        try {
            val dir = File(context.cacheDir, "smb_photos")
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
