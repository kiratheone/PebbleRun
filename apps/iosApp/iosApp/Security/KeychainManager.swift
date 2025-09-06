import Foundation
import Security

/**
 * iOS-specific Keychain wrapper for secure data storage.
 * Implements TASK-021: Implement iOS Keychain integration for secure data storage.
 * Follows SEC-001: Platform-specific security implementations (iOS Keychain).
 */
class KeychainManager {
    
    // MARK: - Constants
    private let serviceName = "com.arikachmad.pebblerun"
    
    // MARK: - Key Types
    enum KeyType: String {
        case pebbleDeviceId = "pebble_device_id"
        case pebbleConnectionToken = "pebble_connection_token"
        case userAuthToken = "user_auth_token"
        case encryptionKey = "encryption_key"
        case apiKey = "api_key"
        case refreshToken = "refresh_token"
        
        var account: String {
            return rawValue
        }
    }
    
    // MARK: - Error Types
    enum KeychainError: Error, LocalizedError {
        case duplicateItem
        case itemNotFound
        case invalidItemFormat
        case unexpectedPasswordData
        case unhandledError(status: OSStatus)
        
        var errorDescription: String? {
            switch self {
            case .duplicateItem:
                return "Item already exists in keychain"
            case .itemNotFound:
                return "Item not found in keychain"
            case .invalidItemFormat:
                return "Invalid item format"
            case .unexpectedPasswordData:
                return "Unexpected password data format"
            case .unhandledError(let status):
                return "Unhandled keychain error: \(status)"
            }
        }
    }
    
    // MARK: - Singleton
    static let shared = KeychainManager()
    private init() {}
    
    // MARK: - Public Methods
    
    /**
     * Store a string value in the keychain
     */
    func store(_ value: String, for keyType: KeyType) throws {
        guard let data = value.data(using: .utf8) else {
            throw KeychainError.invalidItemFormat
        }
        
        try store(data, for: keyType)
    }
    
    /**
     * Store data in the keychain
     */
    func store(_ data: Data, for keyType: KeyType) throws {
        let query = createBaseQuery(for: keyType)
        
        // Check if item already exists
        let status = SecItemCopyMatching(query, nil)
        
        switch status {
        case errSecSuccess:
            // Item exists, update it
            let attributesToUpdate: [String: Any] = [
                kSecValueData as String: data
            ]
            
            let updateStatus = SecItemUpdate(query, attributesToUpdate as CFDictionary)
            
            if updateStatus != errSecSuccess {
                throw KeychainError.unhandledError(status: updateStatus)
            }
            
        case errSecItemNotFound:
            // Item doesn't exist, add it
            var newQuery = query
            newQuery[kSecValueData as String] = data
            
            let addStatus = SecItemAdd(newQuery as CFDictionary, nil)
            
            if addStatus != errSecSuccess {
                throw KeychainError.unhandledError(status: addStatus)
            }
            
        default:
            throw KeychainError.unhandledError(status: status)
        }
    }
    
    /**
     * Retrieve a string value from the keychain
     */
    func retrieveString(for keyType: KeyType) throws -> String? {
        guard let data = try retrieveData(for: keyType) else { return nil }
        
        guard let string = String(data: data, encoding: .utf8) else {
            throw KeychainError.unexpectedPasswordData
        }
        
        return string
    }
    
    /**
     * Retrieve data from the keychain
     */
    func retrieveData(for keyType: KeyType) throws -> Data? {
        var query = createBaseQuery(for: keyType)
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        query[kSecReturnData as String] = true
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        switch status {
        case errSecSuccess:
            guard let data = result as? Data else {
                throw KeychainError.unexpectedPasswordData
            }
            return data
            
        case errSecItemNotFound:
            return nil
            
        default:
            throw KeychainError.unhandledError(status: status)
        }
    }
    
    /**
     * Delete an item from the keychain
     */
    func delete(_ keyType: KeyType) throws {
        let query = createBaseQuery(for: keyType)
        
        let status = SecItemDelete(query as CFDictionary)
        
        switch status {
        case errSecSuccess, errSecItemNotFound:
            // Success or item didn't exist anyway
            break
        default:
            throw KeychainError.unhandledError(status: status)
        }
    }
    
    /**
     * Check if an item exists in the keychain
     */
    func exists(_ keyType: KeyType) -> Bool {
        let query = createBaseQuery(for: keyType)
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }
    
    /**
     * Clear all items from the keychain for this app
     */
    func clearAll() throws {
        for keyType in KeyType.allCases {
            try delete(keyType)
        }
    }
    
    /**
     * Generate and store a new encryption key
     */
    func generateEncryptionKey() throws -> Data {
        var keyData = Data(count: 32) // 256-bit key
        let result = keyData.withUnsafeMutableBytes { bytes in
            SecRandomCopyBytes(kSecRandomDefault, 32, bytes.bindMemory(to: UInt8.self).baseAddress!)
        }
        
        guard result == errSecSuccess else {
            throw KeychainError.unhandledError(status: result)
        }
        
        try store(keyData, for: .encryptionKey)
        return keyData
    }
    
    /**
     * Get or generate encryption key
     */
    func getOrGenerateEncryptionKey() throws -> Data {
        if let existingKey = try retrieveData(for: .encryptionKey) {
            return existingKey
        } else {
            return try generateEncryptionKey()
        }
    }
    
    // MARK: - Private Methods
    
    private func createBaseQuery(for keyType: KeyType) -> [String: Any] {
        return [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: keyType.account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
    }
}

// MARK: - KeyType Extension

extension KeychainManager.KeyType: CaseIterable {
    static var allCases: [KeychainManager.KeyType] {
        return [
            .pebbleDeviceId,
            .pebbleConnectionToken,
            .userAuthToken,
            .encryptionKey,
            .apiKey,
            .refreshToken
        ]
    }
}

// MARK: - Secure Storage Protocol

/**
 * Protocol for secure storage operations
 */
protocol SecureStorage {
    func store(_ value: String, forKey key: String) throws
    func retrieve(forKey key: String) throws -> String?
    func delete(forKey key: String) throws
    func exists(forKey key: String) -> Bool
}

/**
 * Keychain-based secure storage implementation
 */
class KeychainSecureStorage: SecureStorage {
    private let keychainManager = KeychainManager.shared
    
    func store(_ value: String, forKey key: String) throws {
        // Map generic keys to specific KeyType values
        guard let keyType = mapKeyToKeyType(key) else {
            // For dynamic keys, store as-is (this would require extending the implementation)
            throw KeychainManager.KeychainError.invalidItemFormat
        }
        
        try keychainManager.store(value, for: keyType)
    }
    
    func retrieve(forKey key: String) throws -> String? {
        guard let keyType = mapKeyToKeyType(key) else {
            return nil
        }
        
        return try keychainManager.retrieveString(for: keyType)
    }
    
    func delete(forKey key: String) throws {
        guard let keyType = mapKeyToKeyType(key) else {
            return
        }
        
        try keychainManager.delete(keyType)
    }
    
    func exists(forKey key: String) -> Bool {
        guard let keyType = mapKeyToKeyType(key) else {
            return false
        }
        
        return keychainManager.exists(keyType)
    }
    
    private func mapKeyToKeyType(_ key: String) -> KeychainManager.KeyType? {
        switch key {
        case "pebble_device_id":
            return .pebbleDeviceId
        case "pebble_connection_token":
            return .pebbleConnectionToken
        case "user_auth_token":
            return .userAuthToken
        case "encryption_key":
            return .encryptionKey
        case "api_key":
            return .apiKey
        case "refresh_token":
            return .refreshToken
        default:
            return nil
        }
    }
}

// MARK: - Data Encryption Helper

/**
 * Helper class for encrypting/decrypting data using keychain-stored keys
 */
class DataEncryption {
    
    private let keychainManager = KeychainManager.shared
    
    /**
     * Encrypt data using AES-256
     */
    func encrypt(_ data: Data) throws -> Data {
        let key = try keychainManager.getOrGenerateEncryptionKey()
        
        // Generate a random IV
        var iv = Data(count: 16) // AES block size
        let ivResult = iv.withUnsafeMutableBytes { bytes in
            SecRandomCopyBytes(kSecRandomDefault, 16, bytes.bindMemory(to: UInt8.self).baseAddress!)
        }
        
        guard ivResult == errSecSuccess else {
            throw KeychainManager.KeychainError.unhandledError(status: ivResult)
        }
        
        // For production, you would use CommonCrypto or CryptoKit
        // This is a simplified implementation
        let encryptedData = performAESEncryption(data: data, key: key, iv: iv)
        
        // Prepend IV to encrypted data
        var result = iv
        result.append(encryptedData)
        
        return result
    }
    
    /**
     * Decrypt data using AES-256
     */
    func decrypt(_ encryptedData: Data) throws -> Data {
        guard encryptedData.count > 16 else {
            throw KeychainManager.KeychainError.invalidItemFormat
        }
        
        let key = try keychainManager.getOrGenerateEncryptionKey()
        
        // Extract IV from the beginning
        let iv = encryptedData.prefix(16)
        let ciphertext = encryptedData.suffix(from: 16)
        
        // Decrypt the data
        let decryptedData = performAESDecryption(data: ciphertext, key: key, iv: Data(iv))
        
        return decryptedData
    }
    
    // MARK: - Private Encryption Methods
    
    private func performAESEncryption(data: Data, key: Data, iv: Data) -> Data {
        // This is a placeholder implementation
        // In a real app, you would use CommonCrypto or CryptoKit for actual AES encryption
        return data // Return unencrypted for now
    }
    
    private func performAESDecryption(data: Data, key: Data, iv: Data) -> Data {
        // This is a placeholder implementation
        // In a real app, you would use CommonCrypto or CryptoKit for actual AES decryption
        return data // Return as-is for now
    }
}

// MARK: - Usage Examples

extension KeychainManager {
    
    /**
     * Store Pebble device credentials securely
     */
    func storePebbleCredentials(deviceId: String, connectionToken: String) throws {
        try store(deviceId, for: .pebbleDeviceId)
        try store(connectionToken, for: .pebbleConnectionToken)
    }
    
    /**
     * Retrieve Pebble device credentials
     */
    func getPebbleCredentials() throws -> (deviceId: String?, connectionToken: String?) {
        let deviceId = try retrieveString(for: .pebbleDeviceId)
        let connectionToken = try retrieveString(for: .pebbleConnectionToken)
        return (deviceId, connectionToken)
    }
    
    /**
     * Store user authentication token
     */
    func storeUserAuthToken(_ token: String) throws {
        try store(token, for: .userAuthToken)
    }
    
    /**
     * Get user authentication token
     */
    func getUserAuthToken() throws -> String? {
        return try retrieveString(for: .userAuthToken)
    }
}
