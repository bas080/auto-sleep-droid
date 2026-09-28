package com.bas080.autosleepdroid

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class HealthConnectManagerApiTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testWriteSleepSession_CallsInsertRecordsWithCorrectRecord() {
        val mockClient = mock(HealthConnectClient::class.java)
        val mockPermissionController = mock(PermissionController::class.java)
        val mockResponse = mock(InsertRecordsResponse::class.java)

        runBlocking {
            `when`(mockPermissionController.getGrantedPermissions()).thenReturn(HealthConnectManager.REQUIRED_PERMISSIONS)
            `when`(mockClient.permissionController).thenReturn(mockPermissionController)
            `when`(mockClient.insertRecords(org.mockito.ArgumentMatchers.anyList())).thenReturn(mockResponse)
        }

        HealthConnectManager.setClientForTesting(mockClient, true)

        val startTime = System.currentTimeMillis() - 8 * 3600_000L
        val endTime = System.currentTimeMillis()

        val latch = CountDownLatch(1)
        val successRef = AtomicBoolean(false)
        var errorMsg: String? = null

        HealthConnectManager.writeSleepSession(context, startTime, endTime) { success, err ->
            successRef.set(success)
            errorMsg = err
            latch.countDown()
        }

        var attempts = 0

        while (latch.count > 0 && attempts < 100) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()
            Thread.sleep(50)
            attempts++
        }

        println("HealthConnectTest Result: success=${successRef.get()}, error=$errorMsg")
        assertTrue("Callback completed: $errorMsg", latch.count == 0L)
        assertTrue("Session write succeeded: $errorMsg", successRef.get())

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Record>>
        runBlocking {
            verify(mockClient).insertRecords(captor.capture() ?: emptyList())
        }

        val records = captor.value
        assertEquals(1, records.size)
        val record = records[0] as SleepSessionRecord
        assertEquals(startTime, record.startTime.toEpochMilli())
        assertEquals(endTime, record.endTime.toEpochMilli())

        HealthConnectManager.setClientForTesting(null, null)
    }

    @Test
    fun testWriteSleepSession_NapSession_CallsInsertRecordsWithCorrectRecord() {
        val mockClient = mock(HealthConnectClient::class.java)
        val mockPermissionController = mock(PermissionController::class.java)
        val mockResponse = mock(InsertRecordsResponse::class.java)

        runBlocking {
            `when`(mockPermissionController.getGrantedPermissions()).thenReturn(HealthConnectManager.REQUIRED_PERMISSIONS)
            `when`(mockClient.permissionController).thenReturn(mockPermissionController)
            `when`(mockClient.insertRecords(org.mockito.ArgumentMatchers.anyList())).thenReturn(mockResponse)
        }

        HealthConnectManager.setClientForTesting(mockClient, true)

        val napStartTime = System.currentTimeMillis() - 20 * 60_000L // 20 mins ago
        val wakeTime = System.currentTimeMillis()

        val latch = CountDownLatch(1)
        val successRef = AtomicBoolean(false)

        HealthConnectManager.writeSleepSession(context, napStartTime, wakeTime) { success, _ ->
            successRef.set(success)
            latch.countDown()
        }

        var attempts = 0
        while (latch.count > 0 && attempts < 100) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()
            Thread.sleep(50)
            attempts++
        }

        assertTrue("Session write succeeded for nap", successRef.get())

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Record>>
        runBlocking {
            verify(mockClient).insertRecords(captor.capture() ?: emptyList())
        }

        val records = captor.value
        assertEquals(1, records.size)
        val record = records[0] as SleepSessionRecord
        assertEquals(napStartTime, record.startTime.toEpochMilli())
        assertEquals(wakeTime, record.endTime.toEpochMilli())

        HealthConnectManager.setClientForTesting(null, null)
    }

    @Test
    fun testRevokeAllPermissions_CallsPermissionControllerRevokeAllPermissions() {
        val mockClient = mock(HealthConnectClient::class.java)
        val mockPermissionController = mock(PermissionController::class.java)

        runBlocking {
            `when`(mockClient.permissionController).thenReturn(mockPermissionController)
        }

        HealthConnectManager.setClientForTesting(mockClient, true)

        val latch = CountDownLatch(1)
        val successRef = AtomicBoolean(false)

        HealthConnectManager.revokeAllPermissions(context) { success, _ ->
            successRef.set(success)
            latch.countDown()
        }

        var attempts = 0
        while (latch.count > 0 && attempts < 100) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()
            Thread.sleep(50)
            attempts++
        }

        assertTrue("Revoke callback completed", latch.count == 0L)
        assertTrue("Revoke succeeded", successRef.get())

        runBlocking {
            verify(mockPermissionController).revokeAllPermissions()
        }

        HealthConnectManager.setClientForTesting(null, null)
    }

    @Test
    fun testWriteSleepSession_PermissionNotGranted_FailsWithErrorMessage() {
        val mockClient = mock(HealthConnectClient::class.java)
        val mockPermissionController = mock(PermissionController::class.java)

        runBlocking {
            `when`(mockPermissionController.getGrantedPermissions()).thenReturn(emptySet())
            `when`(mockClient.permissionController).thenReturn(mockPermissionController)
        }

        HealthConnectManager.setClientForTesting(mockClient, true)

        val latch = CountDownLatch(1)
        val successRef = AtomicBoolean(true)
        var errorMsg: String? = null

        val startTime = System.currentTimeMillis() - 3600_000L
        val endTime = System.currentTimeMillis()

        HealthConnectManager.writeSleepSession(context, startTime, endTime) { success, err ->
            successRef.set(success)
            errorMsg = err
            latch.countDown()
        }

        var attempts = 0
        while (latch.count > 0 && attempts < 100) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()
            Thread.sleep(50)
            attempts++
        }

        assertFalse("Session write must fail when permission not granted", successRef.get())
        assertEquals("Write permission not granted", errorMsg)

        HealthConnectManager.setClientForTesting(null, null)
    }

    @Test
    fun testWriteSleepSession_InsertException_FailsWithErrorMessage() {
        val mockClient = mock(HealthConnectClient::class.java)
        val mockPermissionController = mock(PermissionController::class.java)

        runBlocking {
            `when`(mockPermissionController.getGrantedPermissions()).thenReturn(HealthConnectManager.REQUIRED_PERMISSIONS)
            `when`(mockClient.permissionController).thenReturn(mockPermissionController)
            `when`(mockClient.insertRecords(org.mockito.ArgumentMatchers.anyList())).thenThrow(RuntimeException("Database lock failure"))
        }

        HealthConnectManager.setClientForTesting(mockClient, true)

        val latch = CountDownLatch(1)
        val successRef = AtomicBoolean(true)
        var errorMsg: String? = null

        val startTime = System.currentTimeMillis() - 3600_000L
        val endTime = System.currentTimeMillis()

        HealthConnectManager.writeSleepSession(context, startTime, endTime) { success, err ->
            successRef.set(success)
            errorMsg = err
            latch.countDown()
        }

        var attempts = 0
        while (latch.count > 0 && attempts < 100) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()
            Thread.sleep(50)
            attempts++
        }

        assertFalse("Session write must fail when exception is thrown", successRef.get())
        assertTrue(errorMsg!!.contains("Database lock failure"))

        HealthConnectManager.setClientForTesting(null, null)
    }

    @Test
    fun testRevokeAllPermissions_Exception_FailsWithErrorMessage() {
        val mockClient = mock(HealthConnectClient::class.java)
        val mockPermissionController = mock(PermissionController::class.java)

        runBlocking {
            `when`(mockPermissionController.revokeAllPermissions()).thenThrow(RuntimeException("Revoke failed"))
            `when`(mockClient.permissionController).thenReturn(mockPermissionController)
        }

        HealthConnectManager.setClientForTesting(mockClient, true)

        val latch = CountDownLatch(1)
        val successRef = AtomicBoolean(true)
        var errorMsg: String? = null

        HealthConnectManager.revokeAllPermissions(context) { success, err ->
            successRef.set(success)
            errorMsg = err
            latch.countDown()
        }

        var attempts = 0
        while (latch.count > 0 && attempts < 100) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()
            Thread.sleep(50)
            attempts++
        }

        assertFalse("Revoke must fail when exception is thrown", successRef.get())
        assertTrue(errorMsg!!.contains("Revoke failed"))

        HealthConnectManager.setClientForTesting(null, null)
    }

    @Test
    fun testHasSleepWritePermission_UnsetTestClient_ReturnsFalseWithoutCrashing() {
        HealthConnectManager.setClientForTesting(null, null)

        val latch = CountDownLatch(1)
        val permissionRef = AtomicBoolean(true)

        HealthConnectManager.hasSleepWritePermission(context) { hasPermission ->
            permissionRef.set(hasPermission)
            latch.countDown()
        }

        var attempts = 0
        while (latch.count > 0 && attempts < 100) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks()
            Thread.sleep(50)
            attempts++
        }

        assertFalse("Permission check without client/SDK returns false", permissionRef.get())
    }
}
