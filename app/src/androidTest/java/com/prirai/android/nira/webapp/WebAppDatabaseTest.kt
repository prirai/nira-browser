package com.prirai.android.nira.webapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for WebAppDatabase and WebAppDao.
 *
 * Tests the Room persistence layer for Progressive Web Apps (PWAs).
 * Uses an in-memory database on a real Android device/emulator.
 * Validates CRUD operations, profile isolation, usage tracking, and queries.
 */
@RunWith(AndroidJUnit4::class)
class WebAppDatabaseTest {

    // System under test
    private lateinit var database: WebAppDatabase
    private lateinit var dao: WebAppDao

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            context,
            WebAppDatabase::class.java
        ).build()

        dao = database.webAppDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -------------------------------------------------------------------------
    // Basic CRUD
    // -------------------------------------------------------------------------

    @Test
    fun `insert and get by ID`() = runBlocking {
        val entity = WebAppEntity(
            id = "pwa-1",
            url = "https://example.com",
            name = "Example PWA",
            manifestUrl = "https://example.com/manifest.json",
            iconUrl = null,
            themeColor = "#FF0000",
            backgroundColor = "#FFFFFF",
            installDate = 1000L,
            lastUsedDate = 1000L,
            launchCount = 0,
            isEnabled = true,
            profileId = "default"
        )

        dao.insert(entity)

        val loaded = dao.getById("pwa-1")
        assertNotNull(loaded)
        assertEquals("Example PWA", loaded!!.name)
        assertEquals("https://example.com", loaded.url)
        assertTrue(loaded.isEnabled)
    }

    @Test
    fun `getById returns null for non-existent ID`() = runBlocking {
        assertNull(dao.getById("non-existent"))
    }

    @Test
    fun `insert multiple and get all ordered by lastUsedDate desc`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://a.com", "A", lastUsed = 3000L))
        dao.insert(createEntity("pwa-2", "https://b.com", "B", lastUsed = 1000L))
        dao.insert(createEntity("pwa-3", "https://c.com", "C", lastUsed = 2000L))

        val all = dao.getAll().first()
        assertEquals(3, all.size)
        // Should be ordered by lastUsedDate DESC: A (3000), C (2000), B (1000)
        assertEquals("pwa-1", all[0].id)
        assertEquals("pwa-3", all[1].id)
        assertEquals("pwa-2", all[2].id)
    }

    @Test
    fun `update an entity`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Original"))

        val loaded = dao.getById("pwa-1")!!
        val updated = loaded.copy(name = "Updated Name", themeColor = "#00FF00")
        dao.update(updated)

        val reloaded = dao.getById("pwa-1")
        assertEquals("Updated Name", reloaded!!.name)
        assertEquals("#00FF00", reloaded.themeColor)
    }

    @Test
    fun `delete by ID removes the entity`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Delete Me"))

        dao.deleteById("pwa-1")

        assertNull(dao.getById("pwa-1"))
    }

    // -------------------------------------------------------------------------
    // URL-based queries
    // -------------------------------------------------------------------------

    @Test
    fun `getByUrl returns correct PWA`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Example"))
        dao.insert(createEntity("pwa-2", "https://google.com", "Google"))

        val found = dao.getByUrl("https://example.com")
        assertNotNull(found)
        assertEquals("pwa-1", found!!.id)
    }

    @Test
    fun `getByUrl returns null for unknown URL`() = runBlocking {
        assertNull(dao.getByUrl("https://nonexistent.com"))
    }

    // -------------------------------------------------------------------------
    // Profile isolation
    // -------------------------------------------------------------------------

    @Test
    fun `getByUrlAndProfile returns correct PWA for profile`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Default", profileId = "default"))
        dao.insert(createEntity("pwa-2", "https://example.com", "Work", profileId = "work"))

        val defaultResult = dao.getByUrlAndProfile("https://example.com", "default")
        val workResult = dao.getByUrlAndProfile("https://example.com", "work")

        assertNotNull(defaultResult)
        assertNotNull(workResult)
        assertEquals("default", defaultResult!!.profileId)
        assertEquals("work", workResult!!.profileId)
    }

    @Test
    fun `webAppExists returns true for existing PWA`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Test", profileId = "default"))

        val exists = dao.getByUrlAndProfile("https://example.com", "default")
        assertNotNull(exists)
    }

    @Test
    fun `webAppExists returns false for non-existent PWA`() = runBlocking {
        assertNull(dao.getByUrlAndProfile("https://nonexistent.com", "default"))
    }

    // -------------------------------------------------------------------------
    // Usage tracking
    // -------------------------------------------------------------------------

    @Test
    fun `updateLastUsed changes the lastUsedDate`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Test", lastUsed = 1000L))

        dao.updateLastUsed("pwa-1", 9999L)

        val loaded = dao.getById("pwa-1")
        assertEquals(9999L, loaded!!.lastUsedDate)
    }

    @Test
    fun `incrementLaunchCount increases by 1`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Test", launchCount = 5))

        dao.incrementLaunchCount("pwa-1")

        val loaded = dao.getById("pwa-1")
        assertEquals(6, loaded!!.launchCount)
    }

    @Test
    fun `setEnabled toggles the enabled state`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Test", enabled = true))

        dao.setEnabled("pwa-1", false)

        val loaded = dao.getById("pwa-1")
        assertFalse(loaded!!.isEnabled)
    }

    @Test
    fun `updateIcon changes the icon URL`() = runBlocking {
        dao.insert(createEntity("pwa-1", "https://example.com", "Test"))

        dao.updateIcon("pwa-1", "https://example.com/new-icon.png")

        val loaded = dao.getById("pwa-1")
        assertEquals("https://example.com/new-icon.png", loaded!!.iconUrl)
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `insert entities with null optional fields`() = runBlocking {
        val entity = WebAppEntity(
            id = "pwa-null",
            url = "https://example.com",
            name = "Null Fields",
            manifestUrl = null,
            iconUrl = null,
            themeColor = null,
            backgroundColor = null,
            installDate = 1000L,
            lastUsedDate = 1000L,
            launchCount = 0,
            isEnabled = true,
            profileId = "default"
        )

        dao.insert(entity)

        val loaded = dao.getById("pwa-null")
        assertNotNull(loaded)
        assertNull(loaded!!.manifestUrl)
        assertNull(loaded.iconUrl)
        assertNull(loaded.themeColor)
    }

    @Test
    fun `empty database returns empty list`() = runBlocking {
        val all = dao.getAll().first()
        assertTrue(all.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Helper factory method
    // -------------------------------------------------------------------------

    private fun createEntity(
        id: String,
        url: String,
        name: String,
        lastUsed: Long = 1000L,
        launchCount: Int = 0,
        enabled: Boolean = true,
        profileId: String = "default"
    ) = WebAppEntity(
        id = id,
        url = url,
        name = name,
        manifestUrl = null,
        iconUrl = null,
        themeColor = null,
        backgroundColor = null,
        installDate = 1000L,
        lastUsedDate = lastUsed,
        launchCount = launchCount,
        isEnabled = enabled,
        profileId = profileId
    )
}
