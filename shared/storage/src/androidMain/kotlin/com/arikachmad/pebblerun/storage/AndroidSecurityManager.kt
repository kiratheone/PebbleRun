package com.arikachmad.pebblerun.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android-specific security manager for database encryption
 * Supports TASK-039: Android-specific database encryption with KeyStore integration
 * 
 * Uses Android KeyStore for secure key generation and storage
 * Provides encrypted database passphrase management
 */
class AndroidSecurityManager(private val context: Context) {
    
    companion object {
        private const val KEYSTORE_ALIAS = "PebbleRunDatabaseKey"
        private const val ENCRYPTED_PREFS_FILE = "pebble_run_secure_prefs"
        private const val DATABASE_KEY_PREF = "database_encryption_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Gets the database encryption key, generating one if it doesn't exist
     * Returns a char array suitable for SQLCipher passphrase
     */
    fun getDatabaseEncryptionKey(): CharArray {
        val existingKey = encryptedPrefs.getString(DATABASE_KEY_PREF, null)
        
        return if (existingKey != null) {
            decryptDatabaseKey(existingKey).toCharArray()
        } else {
            generateAndStoreDatabaseKey()
        }
    }
    
    /**
     * Generates a new database encryption key and stores it securely
     */
    private fun generateAndStoreDatabaseKey(): CharArray {
        // Generate 256-bit key for database encryption
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        val secretKey = keyGenerator.generateKey()
        
        // Generate a random passphrase for the database
        val databasePassphrase = generateRandomPassphrase()
        
        // Encrypt and store the passphrase
        val encryptedKey = encryptDatabaseKey(databasePassphrase)
        encryptedPrefs.edit()
            .putString(DATABASE_KEY_PREF, encryptedKey)
            .apply()
        
        return databasePassphrase.toCharArray()
    }
    
    /**
     * Encrypts the database key using Android KeyStore
     */
    private fun encryptDatabaseKey(key: String): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(key.toByteArray())
        
        // Combine IV and encrypted data
        val combined = ByteArray(GCM_IV_LENGTH + encryptedData.size)
        System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH)
        System.arraycopy(encryptedData, 0, combined, GCM_IV_LENGTH, encryptedData.size)
        
        return android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
    }
    
    /**
     * Decrypts the database key using Android KeyStore
     */
    private fun decryptDatabaseKey(encryptedKey: String): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        val combined = android.util.Base64.decode(encryptedKey, android.util.Base64.DEFAULT)
        
        // Extract IV and encrypted data
        val iv = ByteArray(GCM_IV_LENGTH)
        val encryptedData = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, encryptedData, 0, encryptedData.size)
        
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        val decryptedData = cipher.doFinal(encryptedData)
        return String(decryptedData)
    }
    
    /**
     * Generates a cryptographically secure random passphrase
     */
    private fun generateRandomPassphrase(): String {
        val random = java.security.SecureRandom()
        val passphraseBytes = ByteArray(32) // 256-bit passphrase
        random.nextBytes(passphraseBytes)
        return android.util.Base64.encodeToString(passphraseBytes, android.util.Base64.NO_WRAP)
    }
    
    /**
     * Clears all stored encryption keys (for logout/reset scenarios)
     */
    fun clearKeys() {
        encryptedPrefs.edit().clear().apply()
        
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            // Log but don't throw - key may not exist
        }
    }
    
    /**
     * Checks if encryption keys are properly set up
     */
    fun isKeySetup(): Boolean {
        return encryptedPrefs.contains(DATABASE_KEY_PREF)
    }
}
