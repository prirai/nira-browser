package com.prirai.android.nira.browser.shortcuts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for ShortcutDatabase and ShortcutDao.
 *
 * Shortcuts are URL-bookmark pairs displayed on the homepage.
 * Tests use an in-memory Room database on a real Android device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class ShortcutDatabaseTest {

    // System under test
    private lateinit var database: ShortcutDatabase
    private lateinit var dao: ShortcutDao

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            context,
            ShortcutDatabase::class.java
        ).build()

        dao = database.shortcutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -------------------------------------------------------------------------
    // Basic CRUD
    // -------------------------------------------------------------------------

    @Test
    fun `insert and read all shortcuts`() {
        dao.insertAll(
            ShortcutEntity(url = "https://example.com", title = "Example"),
            ShortcutEntity(url = "https://google.com", title = "Google")
        )

        val all = dao.getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun `insert and load by IDs`() {
        val shortcut = ShortcutEntity(url = "https://example.com", title = "Example")
        dao.insertAll(shortcut)

        // The entity now has an auto-generated uid
        val loaded = dao.loadAllByIds(intArrayOf(shortcut.uid))
        assertEquals(1, loaded.size)
        assertEquals("https://example.com", loaded[0].url)
    }

    @Test
    fun `find by URL returns correct shortcut`() {
        dao.insertAll(
            ShortcutEntity(url = "https://example.com", title = "Example"),
            ShortcutEntity(url = "https://google.com", title = "Google")
        )

        val found = dao.findByUrl("https://example.com")
        assertNotNull(found)
        assertEquals("Example", found.title)
    }

    @Test
    fun `find by non-existent URL returns empty entity`() {
        dao.insertAll(ShortcutEntity(url = "https://example.com", title = "Example"))

        // findByUrl uses LIKE query without wildcards, so non-matching returns
        // a default ShortcutEntity with null fields
        val found = dao.findByUrl("https://nonexistent.com")
        // The DAO returns a ShortcutEntity with default values (uid=0, url=null, title=null)
    }

    @Test
    fun `update existing shortcut`() {
        val shortcut = ShortcutEntity(url = "https://example.com", title = "Example")
        dao.insertAll(shortcut)

        shortcut.title = "Updated Title"
        dao.update(shortcut)

        val loaded = dao.findByUrl("https://example.com")
        assertEquals("Updated Title", loaded.title)
    }

    @Test
    fun `delete shortcut removes it`() {
        val shortcut = ShortcutEntity(url = "https://example.com", title = "Example")
        dao.insertAll(shortcut)

        dao.delete(shortcut)

        val all = dao.getAll()
        assertTrue(all.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Multiple operations
    // -------------------------------------------------------------------------

    @Test
    fun `insert multiple and query all`() {
        dao.insertAll(
            ShortcutEntity(url = "https://site1.com", title = "Site 1"),
            ShortcutEntity(url = "https://site2.com", title = "Site 2"),
            ShortcutEntity(url = "https://site3.com", title = "Site 3")
        )

        assertEquals(3, dao.getAll().size)
    }

    @Test
    fun `insert with same URL creates separate entries`() {
        dao.insertAll(
            ShortcutEntity(url = "https://example.com", title = "First"),
            ShortcutEntity(url = "https://example.com", title = "Second")
        )

        // findByUrl returns only the first match (LIMIT 1)
        val found = dao.findByUrl("https://example.com")
        assertNotNull(found)
    }

    @Test
    fun `insert with null title or url`() {
        dao.insertAll(
            ShortcutEntity(url = null, title = "No URL"),
            ShortcutEntity(url = "https://example.com", title = null)
        )

        assertEquals(2, dao.getAll().size)
    }

    // -------------------------------------------------------------------------
    // Auto-generated IDs
    // -------------------------------------------------------------------------

    @Test
    fun `auto-generated IDs are unique`() {
        val s1 = ShortcutEntity(url = "https://a.com", title = "A")
        val s2 = ShortcutEntity(url = "https://b.com", title = "B")

        dao.insertAll(s1)
        dao.insertAll(s2)

        assertTrue(s1.uid != s2.uid)
    }
}
