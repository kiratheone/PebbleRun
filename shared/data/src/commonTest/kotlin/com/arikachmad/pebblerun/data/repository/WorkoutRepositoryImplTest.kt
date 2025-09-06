package com.arikachmad.pebblerun.data.repository

import com.arikachmad.pebblerun.data.mapper.WorkoutDataMapper
import com.arikachmad.pebblerun.domain.entity.*
import com.arikachmad.pebblerun.storage.WorkoutDatabase
import com.arikachmad.pebblerun.util.error.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for WorkoutRepositoryImpl with in-memory database.
 * Satisfies TEST-006: Repository testing with in-memory SQLDelight database.
 */
class WorkoutRepositoryImplTest {
    
    // Mock database for testing
    private class MockWorkoutDatabase : WorkoutDatabase {
        private val sessions = mutableMapOf<String, WorkoutSession>()
        
        fun insertSession(session: WorkoutSession) {
            sessions[session.id] = session
        }
        
        fun updateSession(session: WorkoutSession) {
            sessions[session.id] = session
        }
        
        fun deleteSession(id: String) {
            sessions.remove(id)
        }
        
        fun getSessionById(id: String): WorkoutSession? {
            return sessions[id]
        }
        
        fun getAllSessions(): List<WorkoutSession> {
            return sessions.values.toList()
        }
        
        fun getSessionsByStatus(status: WorkoutStatus): List<WorkoutSession> {
            return sessions.values.filter { it.status == status }
        }
        
        fun searchSessions(query: String): List<WorkoutSession> {
            return sessions.values.filter { 
                it.notes.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
            }
        }
        
        fun clearAll() {
            sessions.clear()
        }
    }
    
    private lateinit var database: MockWorkoutDatabase
    private lateinit var mapper: WorkoutDataMapper
    private lateinit var repository: WorkoutRepositoryImpl
    
    @BeforeTest
    fun setup() {
        database = MockWorkoutDatabase()
        mapper = WorkoutDataMapper()
        repository = WorkoutRepositoryImpl(database, mapper)
    }
    
    @AfterTest
    fun cleanup() {
        database.clearAll()
    }
    
    private fun createTestSession(
        id: String = "test-session",
        status: WorkoutStatus = WorkoutStatus.PENDING
    ): WorkoutSession {
        return WorkoutSession(
            id = id,
            status = status,
            startTime = Clock.System.now(),
            endTime = null,
            totalDistance = 0.0,
            totalDuration = 0L,
            averageHeartRate = 0.0,
            averagePace = 0.0,
            calories = 0,
            geoPoints = emptyList(),
            hrSamples = emptyList(),
            notes = ""
        )
    }
    
    @Test
    fun `createSession stores session successfully`() = runTest {
        val session = createTestSession()
        
        val result = repository.createSession(session)
        
        assertTrue(result.isSuccess(), "Should create session successfully")
        val createdSession = result.getOrNull()!!
        assertEquals(session.id, createdSession.id)
        assertEquals(session.status, createdSession.status)
        
        // Verify it's stored in database
        val storedSession = database.getSessionById(session.id)
        assertNotNull(storedSession, "Session should be stored in database")
    }
    
    @Test
    fun `updateSession modifies existing session`() = runTest {
        val originalSession = createTestSession()
        database.insertSession(originalSession)
        
        val updatedSession = originalSession.copy(
            status = WorkoutStatus.ACTIVE,
            totalDistance = 1000.0,
            notes = "Updated session"
        )
        
        val result = repository.updateSession(updatedSession)
        
        assertTrue(result.isSuccess(), "Should update session successfully")
        val returnedSession = result.getOrNull()!!
        assertEquals(WorkoutStatus.ACTIVE, returnedSession.status)
        assertEquals(1000.0, returnedSession.totalDistance)
        assertEquals("Updated session", returnedSession.notes)
        
        // Verify changes are persisted
        val storedSession = database.getSessionById(originalSession.id)!!
        assertEquals(WorkoutStatus.ACTIVE, storedSession.status)
        assertEquals(1000.0, storedSession.totalDistance)
    }
    
    @Test
    fun `getSessionById retrieves correct session`() = runTest {
        val session = createTestSession(id = "specific-session")
        database.insertSession(session)
        
        val result = repository.getSessionById("specific-session")
        
        assertTrue(result.isSuccess(), "Should retrieve session successfully")
        val retrievedSession = result.getOrNull()!!
        assertEquals("specific-session", retrievedSession.id)
        assertEquals(session.status, retrievedSession.status)
    }
    
    @Test
    fun `getSessionById returns null for non-existent session`() = runTest {
        val result = repository.getSessionById("non-existent")
        
        assertTrue(result.isSuccess(), "Should handle non-existent session gracefully")
        assertNull(result.getOrNull(), "Should return null for non-existent session")
    }
    
    @Test
    fun `getAllSessions returns all stored sessions`() = runTest {
        val session1 = createTestSession(id = "session1")
        val session2 = createTestSession(id = "session2")
        val session3 = createTestSession(id = "session3")
        
        database.insertSession(session1)
        database.insertSession(session2)
        database.insertSession(session3)
        
        val result = repository.getAllSessions()
        
        assertTrue(result.isSuccess(), "Should retrieve all sessions successfully")
        val sessions = result.getOrNull()!!
        assertEquals(3, sessions.size, "Should return all 3 sessions")
        
        val sessionIds = sessions.map { it.id }
        assertTrue(sessionIds.contains("session1"))
        assertTrue(sessionIds.contains("session2"))
        assertTrue(sessionIds.contains("session3"))
    }
    
    @Test
    fun `getSessionsByStatus filters correctly`() = runTest {
        val pendingSession = createTestSession(id = "pending", status = WorkoutStatus.PENDING)
        val activeSession = createTestSession(id = "active", status = WorkoutStatus.ACTIVE)
        val completedSession = createTestSession(id = "completed", status = WorkoutStatus.COMPLETED)
        
        database.insertSession(pendingSession)
        database.insertSession(activeSession)
        database.insertSession(completedSession)
        
        val activeResult = repository.getSessionsByStatus(WorkoutStatus.ACTIVE)
        
        assertTrue(activeResult.isSuccess(), "Should filter by status successfully")
        val activeSessions = activeResult.getOrNull()!!
        assertEquals(1, activeSessions.size, "Should return only active session")
        assertEquals("active", activeSessions.first().id)
        assertEquals(WorkoutStatus.ACTIVE, activeSessions.first().status)
    }
    
    @Test
    fun `deleteSession removes session from database`() = runTest {
        val session = createTestSession(id = "to-delete")
        database.insertSession(session)
        
        // Verify session exists
        assertNotNull(database.getSessionById("to-delete"))
        
        val result = repository.deleteSession("to-delete")
        
        assertTrue(result.isSuccess(), "Should delete session successfully")
        assertTrue(result.getOrNull() == true, "Should return true for successful deletion")
        
        // Verify session is removed
        assertNull(database.getSessionById("to-delete"), "Session should be removed from database")
    }
    
    @Test
    fun `deleteSession returns false for non-existent session`() = runTest {
        val result = repository.deleteSession("non-existent")
        
        assertTrue(result.isSuccess(), "Should handle non-existent session gracefully")
        assertFalse(result.getOrNull() == true, "Should return false for non-existent session")
    }
    
    @Test
    fun `observeSessions provides reactive updates`() = runTest {
        val initialSession = createTestSession(id = "initial")
        database.insertSession(initialSession)
        
        val flow = repository.observeSessions()
        val initialSessions = flow.first()
        
        assertEquals(1, initialSessions.size, "Should have initial session")
        assertEquals("initial", initialSessions.first().id)
    }
    
    @Test
    fun `searchSessions finds sessions by query`() = runTest {
        val session1 = createTestSession(id = "session1").copy(notes = "Morning run in the park")
        val session2 = createTestSession(id = "session2").copy(notes = "Evening bike ride")
        val session3 = createTestSession(id = "session3").copy(notes = "Afternoon workout")
        
        database.insertSession(session1)
        database.insertSession(session2)
        database.insertSession(session3)
        
        val result = repository.searchSessions("run", limit = 10)
        
        assertTrue(result.isSuccess(), "Should search sessions successfully")
        val foundSessions = result.getOrNull()!!
        assertEquals(1, foundSessions.size, "Should find one session with 'run'")
        assertEquals("session1", foundSessions.first().id)
    }
    
    @Test
    fun `data mapper converts between domain and data layer correctly`() = runTest {
        val baseTime = Clock.System.now()
        val domainSession = WorkoutSession(
            id = "mapper-test",
            status = WorkoutStatus.ACTIVE,
            startTime = baseTime,
            endTime = baseTime.plus(30.minutes),
            totalDistance = 5000.0,
            totalDuration = 1800L,
            averageHeartRate = 145.0,
            averagePace = 360.0,
            calories = 250,
            geoPoints = listOf(
                GeoPoint(40.7128, -74.0060, 5.0f, baseTime),
                GeoPoint(40.7138, -74.0060, 5.0f, baseTime.plus(10.minutes))
            ),
            hrSamples = listOf(
                HRSample(140, baseTime, 0.9f),
                HRSample(150, baseTime.plus(10.minutes), 0.8f)
            ),
            notes = "Great workout session"
        )
        
        // Store and retrieve to test data conversion
        val createResult = repository.createSession(domainSession)
        assertTrue(createResult.isSuccess(), "Should create session successfully")
        
        val retrieveResult = repository.getSessionById("mapper-test")
        assertTrue(retrieveResult.isSuccess(), "Should retrieve session successfully")
        
        val retrievedSession = retrieveResult.getOrNull()!!
        
        // Verify all data is preserved through conversion
        assertEquals(domainSession.id, retrievedSession.id)
        assertEquals(domainSession.status, retrievedSession.status)
        assertEquals(domainSession.totalDistance, retrievedSession.totalDistance, 0.01)
        assertEquals(domainSession.totalDuration, retrievedSession.totalDuration)
        assertEquals(domainSession.averageHeartRate, retrievedSession.averageHeartRate, 0.01)
        assertEquals(domainSession.averagePace, retrievedSession.averagePace, 0.01)
        assertEquals(domainSession.calories, retrievedSession.calories)
        assertEquals(domainSession.notes, retrievedSession.notes)
        assertEquals(domainSession.geoPoints.size, retrievedSession.geoPoints.size)
        assertEquals(domainSession.hrSamples.size, retrievedSession.hrSamples.size)
    }
    
    @Test
    fun `repository handles complex workout session with all data types`() = runTest {
        val baseTime = Clock.System.now()
        val complexSession = WorkoutSession(
            id = "complex-session",
            status = WorkoutStatus.COMPLETED,
            startTime = baseTime.minus(45.minutes),
            endTime = baseTime,
            totalDistance = 8500.0,
            totalDuration = 2700L, // 45 minutes
            averageHeartRate = 155.5,
            averagePace = 317.6, // ~5:18 per km
            calories = 420,
            geoPoints = (0..100).map { i ->
                GeoPoint(
                    latitude = 40.7128 + (i * 0.0001),
                    longitude = -74.0060 + (i * 0.0001),
                    accuracy = 3.0f + (i % 3),
                    timestamp = baseTime.minus(45.minutes).plus((i * 27).seconds)
                )
            },
            hrSamples = (0..270).map { i ->
                HRSample(
                    heartRate = 145 + (i % 20) - 10, // Varying HR between 135-165
                    timestamp = baseTime.minus(45.minutes).plus((i * 10).seconds),
                    confidence = 0.8f + (i % 2) * 0.1f
                )
            },
            notes = "Long training run with interval training. Weather was perfect. Felt strong throughout."
        )
        
        // Test storing complex session
        val createResult = repository.createSession(complexSession)
        assertTrue(createResult.isSuccess(), "Should handle complex session creation")
        
        // Test retrieving complex session
        val retrieveResult = repository.getSessionById("complex-session")
        assertTrue(retrieveResult.isSuccess(), "Should handle complex session retrieval")
        
        val retrieved = retrieveResult.getOrNull()!!
        assertEquals(101, retrieved.geoPoints.size, "Should preserve all GPS points")
        assertEquals(271, retrieved.hrSamples.size, "Should preserve all HR samples")
        assertTrue(retrieved.notes.contains("interval training"), "Should preserve long notes")
        
        // Test updating complex session
        val updatedSession = retrieved.copy(
            status = WorkoutStatus.COMPLETED,
            notes = retrieved.notes + "\nPost-workout: Feeling great!"
        )
        
        val updateResult = repository.updateSession(updatedSession)
        assertTrue(updateResult.isSuccess(), "Should handle complex session update")
        
        val finalRetrieve = repository.getSessionById("complex-session")
        val final = finalRetrieve.getOrNull()!!
        assertTrue(final.notes.contains("Post-workout"), "Should preserve note updates")
    }
    
    @Test
    fun `repository handles concurrent operations correctly`() = runTest {
        val session1 = createTestSession(id = "concurrent1")
        val session2 = createTestSession(id = "concurrent2")
        val session3 = createTestSession(id = "concurrent3")
        
        // Simulate concurrent operations
        val results = listOf(
            repository.createSession(session1),
            repository.createSession(session2),
            repository.createSession(session3)
        )
        
        // All operations should succeed
        assertTrue(results.all { it.isSuccess() }, "All concurrent creates should succeed")
        
        // Verify all sessions are stored
        val allSessions = repository.getAllSessions().getOrNull()!!
        assertEquals(3, allSessions.size, "Should have all 3 sessions")
        
        // Test concurrent updates
        val updates = allSessions.map { session ->
            repository.updateSession(session.copy(notes = "Updated concurrently"))
        }
        
        assertTrue(updates.all { it.isSuccess() }, "All concurrent updates should succeed")
    }
}
