package com.arikachmad.pebblerun.android.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Android-specific Bluetooth management for PebbleRun.
 * Implements TASK-038: Platform-specific Bluetooth management.
 */
class BluetoothManager(private val context: Context) {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    // State tracking
    private val _bluetoothState = MutableStateFlow(BluetoothState.UNKNOWN)
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()
    
    private val _connectedDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val connectedDevices: StateFlow<Set<BluetoothDevice>> = _connectedDevices.asStateFlow()
    
    private val _pebbleDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val pebbleDevices: StateFlow<Set<BluetoothDevice>> = _pebbleDevices.asStateFlow()
    
    // Connection management
    private var connectionMonitoringJob: Job? = null
    private var discoveryJob: Job? = null
    
    enum class BluetoothState {
        UNKNOWN,
        DISABLED,
        ENABLED,
        TURNING_ON,
        TURNING_OFF,
        NOT_SUPPORTED
    }
    
    enum class ConnectionQuality {
        EXCELLENT,  // Strong signal, stable connection
        GOOD,       // Good signal, minor fluctuations
        FAIR,       // Weak signal, occasional drops
        POOR        // Very weak signal, frequent drops
    }
    
    data class PebbleConnectionInfo(
        val device: BluetoothDevice,
        val isConnected: Boolean,
        val connectionQuality: ConnectionQuality,
        val signalStrength: Int, // RSSI value
        val lastSeen: Long,
        val connectionAttempts: Int,
        val successfulConnections: Int
    )
    
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    handleBluetoothStateChange(intent)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    handleDeviceConnected(intent)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    handleDeviceDisconnected(intent)
                }
                BluetoothDevice.ACTION_FOUND -> {
                    handleDeviceFound(intent)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    handleDiscoveryFinished()
                }
            }
        }
    }
    
    init {
        registerBluetoothReceiver()
        updateBluetoothState()
        startConnectionMonitoring()
    }
    
    /**
     * Initializes Bluetooth management and checks initial state
     */
    fun initialize(): Boolean {
        return when {
            bluetoothAdapter == null -> {
                _bluetoothState.value = BluetoothState.NOT_SUPPORTED
                false
            }
            !bluetoothAdapter.isEnabled -> {
                _bluetoothState.value = BluetoothState.DISABLED
                false
            }
            else -> {
                _bluetoothState.value = BluetoothState.ENABLED
                scanForPebbleDevices()
                true
            }
        }
    }
    
    /**
     * Registers Bluetooth broadcast receiver
     */
    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        
        context.registerReceiver(bluetoothReceiver, filter)
    }
    
    /**
     * Unregisters Bluetooth broadcast receiver
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        
        connectionMonitoringJob?.cancel()
        discoveryJob?.cancel()
    }
    
    /**
     * Scans for Pebble devices
     */
    fun scanForPebbleDevices() {
        if (!hasBluetoothPermissions()) {
            println("Missing Bluetooth permissions")
            return
        }
        
        bluetoothAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                // Get already paired Pebble devices
                val pairedPebbles = adapter.bondedDevices.filter { isPebbleDevice(it) }.toSet()
                _pebbleDevices.value = pairedPebbles
                
                // Start discovery for new devices
                if (!adapter.isDiscovering) {
                    startBluetoothDiscovery()
                }
            }
        }
    }
    
    /**
     * Starts Bluetooth device discovery
     */
    private fun startBluetoothDiscovery() {
        if (!hasBluetoothPermissions()) return
        
        discoveryJob?.cancel()
        discoveryJob = GlobalScope.launch {
            try {
                bluetoothAdapter?.startDiscovery()
                println("Started Bluetooth discovery for Pebble devices")
                
                // Stop discovery after 30 seconds
                delay(30000)
                bluetoothAdapter?.cancelDiscovery()
                
            } catch (e: SecurityException) {
                println("Security exception during Bluetooth discovery: ${e.message}")
            }
        }
    }
    
    /**
     * Connects to a specific Pebble device
     */
    suspend fun connectToPebble(device: BluetoothDevice): Boolean {
        if (!hasBluetoothPermissions()) {
            println("Missing Bluetooth permissions for connection")
            return false
        }
        
        return try {
            println("Attempting to connect to Pebble: ${device.name}")
            
            // Implementation would use PebbleKit SDK or custom Bluetooth connection
            // This is a placeholder for the actual connection logic
            
            // For now, simulate connection attempt
            delay(2000)
            
            val connected = kotlin.random.Random.nextBoolean() // Simulate success/failure
            if (connected) {
                println("Successfully connected to Pebble: ${device.name}")
                updateConnectedDevices()
            } else {
                println("Failed to connect to Pebble: ${device.name}")
            }
            
            connected
            
        } catch (e: Exception) {
            println("Error connecting to Pebble: ${e.message}")
            false
        }
    }
    
    /**
     * Disconnects from a Pebble device
     */
    suspend fun disconnectFromPebble(device: BluetoothDevice): Boolean {
        if (!hasBluetoothPermissions()) return false
        
        return try {
            println("Disconnecting from Pebble: ${device.name}")
            
            // Implementation would disconnect using PebbleKit SDK
            delay(1000)
            
            updateConnectedDevices()
            true
            
        } catch (e: Exception) {
            println("Error disconnecting from Pebble: ${e.message}")
            false
        }
    }
    
    /**
     * Gets connection information for a Pebble device
     */
    fun getPebbleConnectionInfo(device: BluetoothDevice): PebbleConnectionInfo {
        val isConnected = isDeviceConnected(device)
        val quality = assessConnectionQuality(device)
        
        return PebbleConnectionInfo(
            device = device,
            isConnected = isConnected,
            connectionQuality = quality,
            signalStrength = getSignalStrength(device),
            lastSeen = System.currentTimeMillis(),
            connectionAttempts = 0, // Would be tracked in production
            successfulConnections = 0 // Would be tracked in production
        )
    }
    
    /**
     * Starts monitoring Bluetooth connections
     */
    private fun startConnectionMonitoring() {
        connectionMonitoringJob?.cancel()
        connectionMonitoringJob = GlobalScope.launch {
            while (isActive) {
                try {
                    updateBluetoothState()
                    updateConnectedDevices()
                    monitorConnectionQuality()
                    
                    delay(5000) // Check every 5 seconds
                    
                } catch (e: Exception) {
                    println("Error in connection monitoring: ${e.message}")
                    delay(10000) // Wait longer on error
                }
            }
        }
    }
    
    /**
     * Monitors connection quality for connected Pebble devices
     */
    private fun monitorConnectionQuality() {
        _connectedDevices.value.forEach { device ->
            if (isPebbleDevice(device)) {
                val quality = assessConnectionQuality(device)
                val signalStrength = getSignalStrength(device)
                
                println("Pebble ${device.name} - Quality: $quality, Signal: ${signalStrength}dBm")
                
                // Alert if connection quality is poor
                if (quality == ConnectionQuality.POOR) {
                    handlePoorConnectionQuality(device)
                }
            }
        }
    }
    
    /**
     * Handles poor connection quality by attempting reconnection
     */
    private fun handlePoorConnectionQuality(device: BluetoothDevice) {
        GlobalScope.launch {
            println("Poor connection quality detected for ${device.name}, attempting to improve...")
            
            // Try to reconnect
            try {
                delay(1000)
                connectToPebble(device)
            } catch (e: Exception) {
                println("Failed to improve connection for ${device.name}: ${e.message}")
            }
        }
    }
    
    /**
     * Event handlers
     */
    private fun handleBluetoothStateChange(intent: Intent) {
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        
        _bluetoothState.value = when (state) {
            BluetoothAdapter.STATE_OFF -> BluetoothState.DISABLED
            BluetoothAdapter.STATE_ON -> BluetoothState.ENABLED
            BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TURNING_OFF
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TURNING_ON
            else -> BluetoothState.UNKNOWN
        }
        
        println("Bluetooth state changed: ${_bluetoothState.value}")
        
        if (_bluetoothState.value == BluetoothState.ENABLED) {
            scanForPebbleDevices()
        }
    }
    
    private fun handleDeviceConnected(intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        
        device?.let {
            if (isPebbleDevice(it)) {
                println("Pebble device connected: ${it.name}")
                updateConnectedDevices()
            }
        }
    }
    
    private fun handleDeviceDisconnected(intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        
        device?.let {
            if (isPebbleDevice(it)) {
                println("Pebble device disconnected: ${it.name}")
                updateConnectedDevices()
            }
        }
    }
    
    private fun handleDeviceFound(intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        
        device?.let {
            if (isPebbleDevice(it)) {
                println("Pebble device found: ${it.name}")
                val currentPebbles = _pebbleDevices.value.toMutableSet()
                currentPebbles.add(it)
                _pebbleDevices.value = currentPebbles
            }
        }
    }
    
    private fun handleDiscoveryFinished() {
        println("Bluetooth discovery finished")
    }
    
    /**
     * Helper methods
     */
    private fun updateBluetoothState() {
        bluetoothAdapter?.let { adapter ->
            _bluetoothState.value = when {
                !adapter.isEnabled -> BluetoothState.DISABLED
                adapter.isEnabled -> BluetoothState.ENABLED
                else -> BluetoothState.UNKNOWN
            }
        } ?: run {
            _bluetoothState.value = BluetoothState.NOT_SUPPORTED
        }
    }
    
    private fun updateConnectedDevices() {
        if (!hasBluetoothPermissions()) return
        
        bluetoothAdapter?.let { adapter ->
            try {
                val connectedDevices = bluetoothManager.getConnectedDevices(android.bluetooth.BluetoothProfile.GATT)
                _connectedDevices.value = connectedDevices.toSet()
            } catch (e: SecurityException) {
                println("Security exception getting connected devices: ${e.message}")
            }
        }
    }
    
    private fun isPebbleDevice(device: BluetoothDevice): Boolean {
        val name = try {
            if (hasBluetoothPermissions()) {
                device.name
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        }
        
        return name?.contains("Pebble", ignoreCase = true) == true ||
               name?.contains("P2-HR", ignoreCase = true) == true
    }
    
    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return _connectedDevices.value.contains(device)
    }
    
    private fun assessConnectionQuality(device: BluetoothDevice): ConnectionQuality {
        val signalStrength = getSignalStrength(device)
        
        return when {
            signalStrength >= -40 -> ConnectionQuality.EXCELLENT
            signalStrength >= -60 -> ConnectionQuality.GOOD
            signalStrength >= -80 -> ConnectionQuality.FAIR
            else -> ConnectionQuality.POOR
        }
    }
    
    private fun getSignalStrength(device: BluetoothDevice): Int {
        // In a real implementation, this would get the actual RSSI value
        // For now, return a simulated value
        return kotlin.random.Random.nextInt(-100, -30)
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == 
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == 
                PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == 
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Gets Bluetooth management statistics
     */
    fun getBluetoothStats(): BluetoothStats {
        return BluetoothStats(
            bluetoothState = _bluetoothState.value,
            totalPebbleDevices = _pebbleDevices.value.size,
            connectedPebbleDevices = _connectedDevices.value.count { isPebbleDevice(it) },
            averageConnectionQuality = calculateAverageConnectionQuality(),
            hasPermissions = hasBluetoothPermissions()
        )
    }
    
    private fun calculateAverageConnectionQuality(): ConnectionQuality {
        val connectedPebbles = _connectedDevices.value.filter { isPebbleDevice(it) }
        
        if (connectedPebbles.isEmpty()) return ConnectionQuality.POOR
        
        val qualityScores = connectedPebbles.map { device ->
            when (assessConnectionQuality(device)) {
                ConnectionQuality.EXCELLENT -> 4
                ConnectionQuality.GOOD -> 3
                ConnectionQuality.FAIR -> 2
                ConnectionQuality.POOR -> 1
            }
        }
        
        val averageScore = qualityScores.average()
        
        return when {
            averageScore >= 3.5 -> ConnectionQuality.EXCELLENT
            averageScore >= 2.5 -> ConnectionQuality.GOOD
            averageScore >= 1.5 -> ConnectionQuality.FAIR
            else -> ConnectionQuality.POOR
        }
    }
    
    data class BluetoothStats(
        val bluetoothState: BluetoothState,
        val totalPebbleDevices: Int,
        val connectedPebbleDevices: Int,
        val averageConnectionQuality: ConnectionQuality,
        val hasPermissions: Boolean
    )
}
