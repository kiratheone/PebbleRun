package com.arikachmad.pebblerun.bridge.pebble

import com.arikachmad.pebblerun.domain.entity.HRSample
import com.arikachmad.pebblerun.util.error.PebbleRunError
import com.arikachmad.pebblerun.util.error.Result
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Mock tests for PebbleTransport testing AppMessage simulation.
 * Satisfies TEST-008: Mock PebbleTransport testing for AppMessage simulation.
 */
class MockPebbleTransportTest {
    
    // Mock implementation for testing
    private class MockPebbleTransport : PebbleTransport {
        private var connected = false
        private var shouldFailConnection = false
        private var shouldFailMessages = false
        private val receivedMessages = mutableListOf<Map<String, Any>>()
        private val hrSamples = mutableListOf<HRSample>()
        
        fun setConnectionFailure(fail: Boolean) {
            shouldFailConnection = fail
        }
        
        fun setMessageFailure(fail: Boolean) {
            shouldFailMessages = fail
        }
        
        fun simulateHeartRateReading(heartRate: Int, confidence: Float = 0.9f) {
            if (connected && !shouldFailMessages) {
                val sample = HRSample(
                    heartRate = heartRate,
                    timestamp = Clock.System.now(),
                    confidence = confidence
                )
                hrSamples.add(sample)
            }
        }
        
        fun getReceivedMessages(): List<Map<String, Any>> = receivedMessages.toList()
        fun getHRSamples(): List<HRSample> = hrSamples.toList()
        
        override suspend fun connect(): Result<Boolean> {
            return if (shouldFailConnection) {
                Result.Error(PebbleRunError.PebbleConnectionError("Mock connection failed"))
            } else {
                connected = true
                Result.Success(true)
            }
        }
        
        override suspend fun disconnect(): Result<Boolean> {
            connected = false
            return Result.Success(true)
        }
        
        override fun isConnected(): Boolean = connected
        
        override suspend fun sendMessage(data: Map<String, Any>): Result<Boolean> {
            return if (!connected) {
                Result.Error(PebbleRunError.PebbleConnectionError("Not connected"))
            } else if (shouldFailMessages) {
                Result.Error(PebbleRunError.PebbleMessageError("Mock message failed"))
            } else {
                receivedMessages.add(data)
                Result.Success(true)
            }
        }
        
        override fun observeHeartRate() = flowOf(*hrSamples.toTypedArray())
        
        override fun observeMessages() = flowOf(*receivedMessages.toTypedArray())
        
        override suspend fun launchWatchApp(): Result<Boolean> {
            return if (!connected) {
                Result.Error(PebbleRunError.PebbleConnectionError("Not connected"))
            } else {
                Result.Success(true)
            }
        }
        
        override suspend fun closeWatchApp(): Result<Boolean> {
            return if (!connected) {
                Result.Error(PebbleRunError.PebbleConnectionError("Not connected"))
            } else {
                Result.Success(true)
            }
        }
    }
    
    private lateinit var pebbleTransport: MockPebbleTransport
    
    @BeforeTest
    fun setup() {
        pebbleTransport = MockPebbleTransport()
    }
    
    @Test
    fun `connect succeeds when conditions are normal`() = runTest {
        val result = pebbleTransport.connect()
        
        assertTrue(result.isSuccess(), "Connection should succeed")
        assertTrue(pebbleTransport.isConnected(), "Should be connected after successful connect")
    }
    
    @Test
    fun `connect fails when simulating connection error`() = runTest {
        pebbleTransport.setConnectionFailure(true)
        
        val result = pebbleTransport.connect()
        
        assertTrue(result.isError(), "Connection should fail")
        assertFalse(pebbleTransport.isConnected(), "Should not be connected after failed connect")
        assertTrue(result.exceptionOrNull() is PebbleRunError.PebbleConnectionError)
    }
    
    @Test
    fun `disconnect works correctly`() = runTest {
        // Connect first
        pebbleTransport.connect()
        assertTrue(pebbleTransport.isConnected(), "Should be connected")
        
        // Then disconnect
        val result = pebbleTransport.disconnect()
        
        assertTrue(result.isSuccess(), "Disconnect should succeed")
        assertFalse(pebbleTransport.isConnected(), "Should not be connected after disconnect")
    }
    
    @Test
    fun `sendMessage succeeds when connected`() = runTest {
        pebbleTransport.connect()
        
        val testData = mapOf(
            "command" to "start_workout",
            "timestamp" to Clock.System.now().epochSeconds
        )
        
        val result = pebbleTransport.sendMessage(testData)
        
        assertTrue(result.isSuccess(), "Message sending should succeed")
        
        val receivedMessages = pebbleTransport.getReceivedMessages()
        assertEquals(1, receivedMessages.size, "Should have received one message")
        assertEquals("start_workout", receivedMessages.first()["command"])
    }
    
    @Test
    fun `sendMessage fails when not connected`() = runTest {
        // Don't connect
        val testData = mapOf("command" to "test")
        
        val result = pebbleTransport.sendMessage(testData)
        
        assertTrue(result.isError(), "Message sending should fail when not connected")
        assertTrue(result.exceptionOrNull() is PebbleRunError.PebbleConnectionError)
        assertEquals(0, pebbleTransport.getReceivedMessages().size, "Should not have received messages")
    }
    
    @Test
    fun `sendMessage fails when simulating message error`() = runTest {
        pebbleTransport.connect()
        pebbleTransport.setMessageFailure(true)
        
        val testData = mapOf("command" to "test")
        val result = pebbleTransport.sendMessage(testData)
        
        assertTrue(result.isError(), "Message sending should fail")
        assertTrue(result.exceptionOrNull() is PebbleRunError.PebbleMessageError)
    }
    
    @Test
    fun `launchWatchApp succeeds when connected`() = runTest {
        pebbleTransport.connect()
        
        val result = pebbleTransport.launchWatchApp()
        
        assertTrue(result.isSuccess(), "Launch watchapp should succeed")
    }
    
    @Test
    fun `launchWatchApp fails when not connected`() = runTest {
        val result = pebbleTransport.launchWatchApp()
        
        assertTrue(result.isError(), "Launch watchapp should fail when not connected")
        assertTrue(result.exceptionOrNull() is PebbleRunError.PebbleConnectionError)
    }
    
    @Test
    fun `closeWatchApp succeeds when connected`() = runTest {
        pebbleTransport.connect()
        
        val result = pebbleTransport.closeWatchApp()
        
        assertTrue(result.isSuccess(), "Close watchapp should succeed")
    }
    
    @Test
    fun `closeWatchApp fails when not connected`() = runTest {
        val result = pebbleTransport.closeWatchApp()
        
        assertTrue(result.isError(), "Close watchapp should fail when not connected")
        assertTrue(result.exceptionOrNull() is PebbleRunError.PebbleConnectionError)
    }
    
    @Test
    fun `heart rate simulation works correctly`() = runTest {
        pebbleTransport.connect()
        
        // Simulate some HR readings
        pebbleTransport.simulateHeartRateReading(140, 0.9f)
        pebbleTransport.simulateHeartRateReading(145, 0.8f)
        pebbleTransport.simulateHeartRateReading(142, 0.95f)
        
        val hrSamples = pebbleTransport.getHRSamples()
        
        assertEquals(3, hrSamples.size, "Should have 3 HR samples")
        assertEquals(140, hrSamples[0].heartRate)
        assertEquals(145, hrSamples[1].heartRate)
        assertEquals(142, hrSamples[2].heartRate)
        assertEquals(0.9f, hrSamples[0].confidence)
        assertEquals(0.8f, hrSamples[1].confidence)
        assertEquals(0.95f, hrSamples[2].confidence)
    }
    
    @Test
    fun `heart rate simulation doesn't work when not connected`() = runTest {
        // Don't connect
        pebbleTransport.simulateHeartRateReading(140)
        
        val hrSamples = pebbleTransport.getHRSamples()
        assertEquals(0, hrSamples.size, "Should not receive HR when not connected")
    }
    
    @Test
    fun `multiple messages can be sent and received`() = runTest {
        pebbleTransport.connect()
        
        val messages = listOf(
            mapOf("command" to "start_workout", "id" to "session1"),
            mapOf("command" to "update_pace", "pace" to 360.0),
            mapOf("command" to "update_duration", "duration" to 1800L),
            mapOf("command" to "stop_workout", "id" to "session1")
        )
        
        // Send all messages
        messages.forEach { message ->
            val result = pebbleTransport.sendMessage(message)
            assertTrue(result.isSuccess(), "Each message should send successfully")
        }
        
        val receivedMessages = pebbleTransport.getReceivedMessages()
        assertEquals(4, receivedMessages.size, "Should have received all 4 messages")
        
        // Verify message order and content
        assertEquals("start_workout", receivedMessages[0]["command"])
        assertEquals("update_pace", receivedMessages[1]["command"])
        assertEquals("update_duration", receivedMessages[2]["command"])
        assertEquals("stop_workout", receivedMessages[3]["command"])
        
        assertEquals("session1", receivedMessages[0]["id"])
        assertEquals(360.0, receivedMessages[1]["pace"])
        assertEquals(1800L, receivedMessages[2]["duration"])
    }
    
    @Test
    fun `connection state management works correctly`() = runTest {
        // Initially not connected
        assertFalse(pebbleTransport.isConnected(), "Should start disconnected")
        
        // Connect
        val connectResult = pebbleTransport.connect()
        assertTrue(connectResult.isSuccess(), "Connection should succeed")
        assertTrue(pebbleTransport.isConnected(), "Should be connected")
        
        // Try to connect again (should still work)
        val reconnectResult = pebbleTransport.connect()
        assertTrue(reconnectResult.isSuccess(), "Reconnection should succeed")
        assertTrue(pebbleTransport.isConnected(), "Should still be connected")
        
        // Disconnect
        val disconnectResult = pebbleTransport.disconnect()
        assertTrue(disconnectResult.isSuccess(), "Disconnection should succeed")
        assertFalse(pebbleTransport.isConnected(), "Should be disconnected")
        
        // Try to disconnect again (should still work)
        val redisconnectResult = pebbleTransport.disconnect()
        assertTrue(redisconnectResult.isSuccess(), "Re-disconnection should succeed")
        assertFalse(pebbleTransport.isConnected(), "Should still be disconnected")
    }
    
    @Test
    fun `simulated workout session flow works end-to-end`() = runTest {
        // 1. Connect to Pebble
        val connectResult = pebbleTransport.connect()
        assertTrue(connectResult.isSuccess(), "Should connect successfully")
        
        // 2. Launch watchapp
        val launchResult = pebbleTransport.launchWatchApp()
        assertTrue(launchResult.isSuccess(), "Should launch watchapp successfully")
        
        // 3. Start workout session
        val startMessage = mapOf(
            "command" to "start_workout",
            "session_id" to "test-session-123",
            "timestamp" to Clock.System.now().epochSeconds
        )
        val startResult = pebbleTransport.sendMessage(startMessage)
        assertTrue(startResult.isSuccess(), "Should send start message successfully")
        
        // 4. Simulate HR readings during workout
        val hrReadings = listOf(
            Triple(140, 0.9f, "resting"),
            Triple(155, 0.8f, "warming up"),
            Triple(170, 0.95f, "active"),
            Triple(165, 0.9f, "steady"),
            Triple(145, 0.85f, "cooling down")
        )
        
        hrReadings.forEachIndexed { index, (hr, confidence, phase) ->
            pebbleTransport.simulateHeartRateReading(hr, confidence)
            
            // Send pace updates
            val paceMessage = mapOf(
                "command" to "update_pace",
                "pace" to 300.0 + (index * 10), // Varying pace
                "timestamp" to Clock.System.now().epochSeconds
            )
            val paceResult = pebbleTransport.sendMessage(paceMessage)
            assertTrue(paceResult.isSuccess(), "Should send pace update successfully")
        }
        
        // 5. Stop workout
        val stopMessage = mapOf(
            "command" to "stop_workout",
            "session_id" to "test-session-123",
            "timestamp" to Clock.System.now().epochSeconds
        )
        val stopResult = pebbleTransport.sendMessage(stopMessage)
        assertTrue(stopResult.isSuccess(), "Should send stop message successfully")
        
        // 6. Close watchapp
        val closeResult = pebbleTransport.closeWatchApp()
        assertTrue(closeResult.isSuccess(), "Should close watchapp successfully")
        
        // 7. Verify all data was captured
        val hrSamples = pebbleTransport.getHRSamples()
        assertEquals(5, hrSamples.size, "Should have captured all HR readings")
        
        val messages = pebbleTransport.getReceivedMessages()
        assertEquals(7, messages.size, "Should have all messages: start + 5 pace updates + stop")
        
        // Verify message sequence
        assertEquals("start_workout", messages[0]["command"])
        assertEquals("update_pace", messages[1]["command"])
        assertEquals("stop_workout", messages[6]["command"])
        
        // Verify HR data quality
        val expectedHRs = listOf(140, 155, 170, 165, 145)
        hrSamples.forEachIndexed { index, sample ->
            assertEquals(expectedHRs[index], sample.heartRate, "HR reading $index should match")
            assertTrue(sample.confidence >= 0.8f, "Confidence should be good")
        }
    }
}
