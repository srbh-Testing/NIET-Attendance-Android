package com.example.nietattendance

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_login_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(user: String, pass: String) {
        sharedPrefs.edit()
            .putString("username", user)
            .putString("password", pass)
            .apply()
    }

    fun getUsername(): String? = sharedPrefs.getString("username", null)
    fun getPassword(): String? = sharedPrefs.getString("password", null)

    fun clear() {
        sharedPrefs.edit().clear().apply()
    }
}
