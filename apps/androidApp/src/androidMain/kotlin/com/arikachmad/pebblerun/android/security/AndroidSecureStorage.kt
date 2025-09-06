package com.arikachmad.pebblerun.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/**
 * Android KeyStore integration for secure data storage
 * Satisfies REQ-013 (Implement Android KeyStore integration for secure data storage)
 * Satisfies SEC-001 (Platform-specific security implementations - Android KeyStore)
 */
class AndroidSecureStorage(private val context: Context) {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "PebbleRunSecretKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFERENCES_FILE_NAME = "pebble_run_secure_prefs"
        private const val GCM_IV_LENGTH = 12
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }
    
    private val encryptedSharedPreferences by lazy {
        createEncryptedSharedPreferences()
    }
    
    init {
        generateSecretKey()
    }
    
    /**
     * Store encrypted data using Android KeyStore
     */
    fun storeSecureData(key: String, data: String) {
        try {
            encryptedSharedPreferences.edit()
                .putString(key, data)
                .apply()
        } catch (e: Exception) {
            throw SecurityException("Failed to store secure data", e)
        }
    }
    
    /**
     * Retrieve encrypted data using Android KeyStore
     */
    fun getSecureData(key: String): String? {
        return try {
            encryptedSharedPreferences.getString(key, null)
        } catch (e: Exception) {
            android.util.Log.e("AndroidSecureStorage", "Failed to retrieve secure data", e)
            null
        }
    }
    
    /**
     * Remove secure data
     */
    fun removeSecureData(key: String) {
        try {
            encryptedSharedPreferences.edit()
                .remove(key)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("AndroidSecureStorage", "Failed to remove secure data", e)
        }
    }
    
    /**
     * Clear all secure data
     */
    fun clearAllSecureData() {
        try {
            encryptedSharedPreferences.edit()
                .clear()
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("AndroidSecureStorage", "Failed to clear secure data", e)
        }
    }
    
    /**
     * Encrypt data directly using KeyStore key
     */
    fun encryptData(data: ByteArray): Pair<ByteArray, ByteArray> {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        
        return Pair(encryptedData, iv)
    }
    
    /**
     * Decrypt data directly using KeyStore key
     */
    fun decryptData(encryptedData: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(encryptedData)
    }
    
    /**
     * Generate secret key in Android KeyStore
     */
    private fun generateSecretKey() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    /**
     * Get secret key from Android KeyStore
     */
    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
    
    /**
     * Create encrypted shared preferences using Jetpack Security
     */
    private fun createEncryptedSharedPreferences(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            PREFERENCES_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}

/**
 * Interface for secure storage operations
 */
interface SecureStorage {
    fun storeSecureData(key: String, data: String)
    fun getSecureData(key: String): String?
    fun removeSecureData(key: String)
    fun clearAllSecureData()
}
