package com.carlren.photoframe

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class VpsCredentials(
    val baseUrl: String,
    val username: String,
    val password: String
)

object CredentialStore {
    private const val PREFS_NAME = "photo_frame_creds"
    private const val KEY_BASE_URL = "base_url"
    private const val LEGACY_KEY_HOST = "host"
    private const val LEGACY_KEY_SHARE = "share"
    private const val LEGACY_KEY_PATH = "path"
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

    fun save(context: Context, creds: VpsCredentials) {
        prefs(context).edit()
            .putString(KEY_BASE_URL, creds.baseUrl)
            .putString(KEY_USERNAME, creds.username)
            .putString(KEY_PASSWORD, creds.password)
            .remove(LEGACY_KEY_HOST)
            .remove(LEGACY_KEY_SHARE)
            .remove(LEGACY_KEY_PATH)
            .apply()
    }

    fun load(context: Context): VpsCredentials? {
        val p = prefs(context)
        val baseUrl = p.getString(KEY_BASE_URL, null) ?: return null
        val username = p.getString(KEY_USERNAME, null) ?: return null
        val password = p.getString(KEY_PASSWORD, null) ?: return null
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return null
        if (username.isBlank()) return null
        return VpsCredentials(baseUrl, username, password)
    }

    fun hasCredentials(context: Context): Boolean = load(context) != null

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
