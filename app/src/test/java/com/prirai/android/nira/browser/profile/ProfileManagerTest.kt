package com.prirai.android.nira.browser.profile

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for ProfileManager — browser profile CRUD and persistence.
 *
 * ProfileManager stores profiles as JSON in SharedPreferences. It manages:
 * - Creating/deleting profiles with name, color, and emoji
 * - Active profile tracking
 * - Always ensuring the default profile exists
 *
 * Uses Robolectric for a real SharedPreferences instance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class ProfileManagerTest {

    // System under test
    private lateinit var manager: ProfileManager

    // Real application context
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Create via reflection to bypass singleton
        val constructor = ProfileManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        manager = constructor.newInstance(context.applicationContext)
    }

    // -------------------------------------------------------------------------
    // Default profile
    // -------------------------------------------------------------------------

    @Test
    fun `getAllProfiles always includes default profile`() {
        val profiles = manager.getAllProfiles()
        assertTrue(profiles.any { it.isDefault })
    }

    @Test
    fun `default profile has correct properties`() {
        val defaultProfile = BrowserProfile.getDefaultProfile()
        assertEquals("default", defaultProfile.id)
        assertEquals("Default", defaultProfile.name)
        assertTrue(defaultProfile.isDefault)
        assertEquals("👤", defaultProfile.emoji)
    }

    @Test
    fun `getActiveProfile returns default when no other set`() {
        val active = manager.getActiveProfile()
        assertEquals("default", active.id)
    }

    // -------------------------------------------------------------------------
    // Create profile
    // -------------------------------------------------------------------------

    @Test
    fun `createProfile adds new profile to list`() {
        val initial = manager.getAllProfiles().size

        manager.createProfile(name = "Work", color = 0xFF0000.toInt(), emoji = "💼")

        val afterCreate = manager.getAllProfiles()
        assertEquals(initial + 1, afterCreate.size)
        assertTrue(afterCreate.any { it.name == "Work" })
    }

    @Test
    fun `createProfile assigns random UUID`() {
        val profile = manager.createProfile(name = "Test", color = 0, emoji = "🔵")
        assertNotNull(profile.id)
        assertTrue(profile.id.isNotEmpty())
        assertFalse(profile.isDefault)
    }

    @Test
    fun `createProfile with non-default emoji works`() {
        val profile = manager.createProfile(name = "School", color = 0x00FF00.toInt(), emoji = "🎓")
        assertEquals("🎓", profile.emoji)
    }

    // -------------------------------------------------------------------------
    // Set active profile
    // -------------------------------------------------------------------------

    @Test
    fun `setActiveProfile changes the active profile`() {
        val profile = manager.createProfile(name = "Work", color = 0, emoji = "💼")

        manager.setActiveProfile(profile)

        val active = manager.getActiveProfile()
        assertEquals(profile.id, active.id)
    }

    @Test
    fun `setActiveProfile to default works`() {
        val profile = manager.createProfile(name = "Temp", color = 0, emoji = "🔵")
        manager.setActiveProfile(profile)

        // Switch back to default
        manager.setActiveProfile(BrowserProfile.getDefaultProfile())

        val active = manager.getActiveProfile()
        assertEquals("default", active.id)
    }

    // -------------------------------------------------------------------------
    // BrowserProfile data class
    // -------------------------------------------------------------------------

    @Test
    fun `BrowserProfile has default values`() {
        val profile = BrowserProfile(name = "Test Profile", color = 0xFF0000.toInt())
        assertNotNull(profile.id)
        assertEquals("👤", profile.emoji)
        assertFalse(profile.isDefault)
        assertTrue(profile.createdAt > 0)
    }

    @Test
    fun `BrowserProfile emoji helper returns correct index`() {
        assertEquals("👤", BrowserProfile.getEmojiForIndex(0))
        assertEquals("🎓", BrowserProfile.getEmojiForIndex(1))
        assertEquals("💼", BrowserProfile.getEmojiForIndex(2))
    }

    @Test
    fun `BrowserProfile emoji helper returns default for out of bounds`() {
        assertEquals("👤", BrowserProfile.getEmojiForIndex(100))
        assertEquals("👤", BrowserProfile.getEmojiForIndex(-1))
    }
}
