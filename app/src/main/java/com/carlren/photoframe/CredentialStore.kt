package com.carlren.photoframe

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class SmpCredentials(
    val host: String,
    val share: String,
    val path: String,
    val username: String,
    val password: String
)

object CredentialStore {
    private const val PREFS_NAME = "photo_frame_creds"
    private const val KEY_HOST = "host"
    private const val KEY_SHARE = "share"
    private const val KEY_PATH = "path"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, creds: SmpCredentials) {
        prefs(context).edit()
            .putString(KEY_HOST, creds.host)
            .putString(KEY_SHARE, creds.share)
            .putString(KEY_PATH, creds.path)
            .putString(KEY_USERNAME, creds.username)
            .putString(KEY_PASSWORD, creds.password)
            .apply()
    }

    fun load(context: Context): SmpCredentials? {
        val p = prefs(context)
        val host = p.getString(KEY_HOST, null) ?: return null
        val share = p.getString(KEY_SHARE, null) ?: return null
        val path = p.getString(KEY_PATH, null) ?: return null
        val username = p.getString(KEY_USERNAME, null) ?: return null
        val password = p.getString(KEY_PASSWORD, null) ?: return null
        if (host.isBlank() || username.isBlank()) return null
        return SmpCredentials(host, share, path, username, password)
    }

    fun hasCredentials(context: Context): Boolean = load(context) != null

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
