package com.carlren.photoframe

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object VpsPhotoRepository {
    private const val TAG = "VpsPhotoRepository"
    private const val CACHE_DIR = "vps_photos"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "bmp")

    private data class Session(val credentials: VpsCredentials, val cookie: String)

    @Volatile
    private var session: Session? = null

    data class VpsPhoto(
        val name: String,
        val remotePath: String,
        val sizeBytes: Long,
        val modifiedAt: Double
    )

    suspend fun testConnection(creds: VpsCredentials): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { listRemotePhotosBlocking(creds).size }
    }

    suspend fun listRemotePhotos(creds: VpsCredentials): List<VpsPhoto> =
        withContext(Dispatchers.IO) { listRemotePhotosBlocking(creds) }

    private fun listRemotePhotosBlocking(creds: VpsCredentials): List<VpsPhoto> {
        val connection = authenticatedGet(creds, "/api/frame?path=")
        return try {
            requireSuccess(connection)
            val entries = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .getJSONArray("entries")
            buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.getJSONObject(index)
                    val name = entry.getString("name")
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension in IMAGE_EXTENSIONS && name == File(name).name) {
                        add(
                            VpsPhoto(
                                name = name,
                                remotePath = name,
                                sizeBytes = entry.optLong("size", 0L),
                                modifiedAt = entry.optDouble("mtime", 0.0)
                            )
                        )
                    }
                }
            }.sortedBy { it.name.lowercase() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun ensurePhotosCached(
        context: Context,
        creds: VpsCredentials,
        remotePhotos: List<VpsPhoto>
    ): List<File> = withContext(Dispatchers.IO) {
        buildList {
            for (photo in remotePhotos) {
                downloadPhoto(context, creds, photo)?.let { file ->
                    add(DisplayPhotoOptimizer.prepare(context, file) ?: file)
                }
            }
        }
    }

    private fun downloadPhoto(context: Context, creds: VpsCredentials, photo: VpsPhoto): File? {
        val cacheDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val localFile = File(cacheDir, photo.name)
        if (localFile.exists() && localFile.length() > 0L &&
            (photo.sizeBytes <= 0L || localFile.length() == photo.sizeBytes)
        ) {
            return localFile
        }

        val partialFile = File(cacheDir, "${photo.name}.part").apply { delete() }
        val encodedPath = URLEncoder.encode(photo.remotePath, Charsets.UTF_8.name())
        val connection = authenticatedGet(creds, "/api/media?kind=frame&path=$encodedPath")
        return try {
            requireSuccess(connection)
            connection.inputStream.use { input ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (photo.sizeBytes > 0L && partialFile.length() != photo.sizeBytes) {
                throw IOException("Incomplete VPS download")
            }
            if (localFile.exists() && !localFile.delete()) {
                throw IOException("Unable to replace cached photo")
            }
            if (!partialFile.renameTo(localFile)) {
                throw IOException("Unable to finalize cached photo")
            }
            localFile
        } catch (exception: Exception) {
            Log.e(TAG, "Photo download failed (${exception.javaClass.simpleName})")
            partialFile.delete()
            null
        } finally {
            connection.disconnect()
        }
    }

    fun clearCache(context: Context) {
        try {
            File(context.cacheDir, CACHE_DIR).listFiles()?.forEach { it.delete() }
            File(context.cacheDir, DisplayPhotoOptimizer.CACHE_DIR).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun authenticatedGet(creds: VpsCredentials, path: String): HttpURLConnection {
        var cookie = cookieFor(creds)
        var connection = openGet(creds, path, cookie)
        if (connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            connection.disconnect()
            invalidate(creds)
            cookie = cookieFor(creds)
            connection = openGet(creds, path, cookie)
        }
        return connection
    }

    private fun openGet(creds: VpsCredentials, path: String, cookie: String): HttpURLConnection {
        val connection = URL(baseUrl(creds) + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json, image/*")
        connection.setRequestProperty("Cookie", cookie)
        connection.setRequestProperty("User-Agent", "PortalPhotoFrame/2.0")
        return connection
    }

    private fun cookieFor(creds: VpsCredentials): String {
        synchronized(this) {
            session?.takeIf { it.credentials == creds }?.let { return it.cookie }
            val connection = URL(baseUrl(creds) + "/api/login").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "PortalPhotoFrame/2.0")
                val body = JSONObject()
                    .put("username", creds.username)
                    .put("password", creds.password)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }
                requireSuccess(connection)
                val cookie = connection.headerFields
                    .filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
                    .values
                    .flatten()
                    .map { it.substringBefore(';') }
                    .filter { it.isNotBlank() }
                    .joinToString("; ")
                if (cookie.isBlank()) throw IOException("VPS login returned no session cookie")
                session = Session(creds, cookie)
                return cookie
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun invalidate(creds: VpsCredentials) {
        synchronized(this) {
            if (session?.credentials == creds) session = null
        }
    }

    private fun baseUrl(creds: VpsCredentials): String = creds.baseUrl.trim().trimEnd('/')

    private fun requireSuccess(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            val detail = runCatching {
                (connection.errorStream ?: connection.inputStream)
                    .bufferedReader()
                    .use { it.readText() }
                    .take(160)
            }.getOrDefault("")
            throw IOException("HTTP $code${if (detail.isBlank()) "" else ": $detail"}")
        }
    }
}
