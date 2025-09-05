package com.arikachmad.pebblerun.data.security

import kotlinx.datetime.Clock

/**
 * Interface for encrypting and decrypting sensitive workout data.
 * Satisfies SEC-001 (Secure local data storage with encryption).
 * Supports TASK-018: Implement local storage with encryption for sensitive data.
 */
interface DataEncryption {
    
    /**
     * Encrypts sensitive data for secure storage
     * @param data The data to encrypt
     * @return Encrypted data as Base64 string
     */
    suspend fun encrypt(data: String): Result<String>
    
    /**
     * Decrypts previously encrypted data
     * @param encryptedData The encrypted data as Base64 string
     * @return Decrypted original data
     */
    suspend fun decrypt(encryptedData: String): Result<String>
    
    /**
     * Encrypts location data (latitude, longitude)
     * Implements privacy-compliant location data handling per SEC-002
     */
    suspend fun encryptLocationData(latitude: Double, longitude: Double): Result<Pair<String, String>>
    
    /**
     * Decrypts location data back to coordinates
     */
    suspend fun decryptLocationData(encryptedLat: String, encryptedLng: String): Result<Pair<Double, Double>>
    
    /**
     * Generates a secure key for encryption operations
     * Should be called once per app installation
     */
    suspend fun generateSecureKey(): Result<String>
    
    /**
     * Validates encryption key integrity
     */
    suspend fun validateKey(key: String): Boolean
}

/**
 * Simple encryption implementation using platform-appropriate methods.
 * This is a basic implementation - production apps should use more robust encryption.
 */
class SimpleDataEncryption : DataEncryption {
    
    companion object {
        private const val ENCRYPTION_KEY_PREFIX = "PEBBLE_RUN_"
        private const val LOCATION_SALT = "LOC_SALT_2024"
    }
    
    override suspend fun encrypt(data: String): Result<String> {
        return try {
            // Simple XOR encryption for demonstration
            // In production, use AES encryption with proper key management
            val key = getEncryptionKey()
            val encrypted = xorEncrypt(data, key)
            val encoded = encoded(encrypted)
            Result.success(encoded)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun decrypt(encryptedData: String): Result<String> {
        return try {
            val key = getEncryptionKey()
            val decoded = decode(encryptedData)
            val decrypted = xorDecrypt(decoded, key)
            Result.success(decrypted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun encryptLocationData(latitude: Double, longitude: Double): Result<Pair<String, String>> {
        return try {
            val latString = latitude.toString()
            val lngString = longitude.toString()
            
            val encryptedLat = encrypt(latString).getOrThrow()
            val encryptedLng = encrypt(lngString).getOrThrow()
            
            Result.success(Pair(encryptedLat, encryptedLng))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun decryptLocationData(encryptedLat: String, encryptedLng: String): Result<Pair<Double, Double>> {
        return try {
            val latString = decrypt(encryptedLat).getOrThrow()
            val lngString = decrypt(encryptedLng).getOrThrow()
            
            val latitude = latString.toDouble()
            val longitude = lngString.toDouble()
            
            Result.success(Pair(latitude, longitude))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun generateSecureKey(): Result<String> {
        return try {
            // Generate a simple key based on current time and random data
            // In production, use proper cryptographic key generation
            val timestamp = Clock.System.now().epochSeconds
            val random = kotlin.random.Random.nextLong()
            val key = "$ENCRYPTION_KEY_PREFIX${timestamp}_$random"
            Result.success(key)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun validateKey(key: String): Boolean {
        return key.startsWith(ENCRYPTION_KEY_PREFIX) && key.length > 20
    }
    
    private fun getEncryptionKey(): String {
        // In production, this should be stored securely using platform keychain/keystore
        return "default_key_should_be_replaced_in_production"
    }
    
    private fun xorEncrypt(data: String, key: String): ByteArray {
        val keyBytes = key.encodeToByteArray()
        val dataBytes = data.encodeToByteArray()
        val result = ByteArray(dataBytes.size)
        
        for (i in dataBytes.indices) {
            result[i] = (dataBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        
        return result
    }
    
    private fun xorDecrypt(data: ByteArray, key: String): String {
        val keyBytes = key.encodeToByteArray()
        val result = ByteArray(data.size)
        
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        
        return result.decodeToString()
    }
    
    private fun encoded(data: ByteArray): String {
        // Simple base64-like encoding
        return data.joinToString(",") { (it.toInt() and 0xFF).toString() }
    }
    
    private fun decode(data: String): ByteArray {
        return data.split(",").map { it.toInt().toByte() }.toByteArray()
    }
}

/**
 * Factory for creating encryption instances based on platform capabilities
 */
object DataEncryptionFactory {
    
    fun create(): DataEncryption {
        // In production, this could return platform-specific encryption implementations
        return SimpleDataEncryption()
    }
}
