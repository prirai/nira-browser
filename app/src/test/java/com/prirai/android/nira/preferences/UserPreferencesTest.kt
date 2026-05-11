package com.prirai.android.nira.preferences

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.prirai.android.nira.settings.ThemeChoice
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for UserPreferences — the SharedPreferences-based settings wrapper.
 *
 * UserPreferences wraps Android SharedPreferences with type-safe property
 * delegates from Mozilla's support-ktx library. These tests verify:
 * - All default values are returned when nothing has been persisted
 * - Read/write round-trip for each preference type (boolean, int, float, long, string)
 * - Computed properties like shouldUseBottomToolbar and statusBarBlurEnabled
 *
 * Uses Robolectric to provide a real Android Context with working SharedPreferences
 * (backed by an in-memory XML file that AutoDataStore or similar would manage).
 * This is more realistic than mocking SharedPreferences directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class UserPreferencesTest {

    // System under test
    private lateinit var prefs: UserPreferences

    // Real application context from Robolectric — gives us a working
    // SharedPreferences instance backed by a temporary file
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Create a fresh UserPreferences for each test.
        // Each test gets its own SharedPreferences via the context.
        prefs = UserPreferences(context)
    }

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Test
    fun `bookmarkFolder defaults to false`() {
        assertFalse(prefs.bookmarkFolder)
    }

    @Test
    fun `bookmarkFolderId defaults to -1`() {
        assertEquals(-1L, prefs.bookmarkFolderId)
    }

    @Test
    fun `firstLaunch defaults to true`() {
        assertTrue(prefs.firstLaunch)
    }

    @Test
    fun `javaScriptEnabled defaults to true`() {
        assertTrue(prefs.javaScriptEnabled)
    }

    @Test
    fun `searchEngineChoice defaults to 0`() {
        assertEquals(0, prefs.searchEngineChoice)
    }

    @Test
    fun `toolbarPosition defaults to BOTTOM`() {
        assertEquals(ToolbarPosition.BOTTOM.ordinal, prefs.toolbarPosition)
    }

    @Test
    fun `appThemeChoice defaults to SYSTEM`() {
        assertEquals(ThemeChoice.SYSTEM.ordinal, prefs.appThemeChoice)
    }

    @Test
    fun `swipeToRefresh defaults to true`() {
        assertTrue(prefs.swipeToRefresh)
    }

    @Test
    fun `toolbarIconSize defaults to 1_0`() {
        assertEquals(1.0f, prefs.toolbarIconSize, 0.001f)
    }

    @Test
    fun `interfaceFontScale defaults to 1_0`() {
        assertEquals(1.0f, prefs.interfaceFontScale, 0.001f)
    }

    @Test
    fun `amoledMode defaults to false`() {
        assertFalse(prefs.amoledMode)
    }

    @Test
    fun `dynamicColors defaults to true`() {
        assertTrue(prefs.dynamicColors)
    }

    @Test
    fun `showTabGroupBar defaults to true`() {
        assertTrue(prefs.showTabGroupBar)
    }

    @Test
    fun `safeBrowsing defaults to true`() {
        assertTrue(prefs.safeBrowsing)
    }

    @Test
    fun `trackingProtection defaults to true`() {
        assertTrue(prefs.trackingProtection)
    }

    // -------------------------------------------------------------------------
    // Boolean read/write round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `boolean preference write then read returns same value`() {
        prefs.javaScriptEnabled = false
        assertFalse(prefs.javaScriptEnabled)

        prefs.javaScriptEnabled = true
        assertTrue(prefs.javaScriptEnabled)
    }

    @Test
    fun `swipeToRefresh round-trip`() {
        prefs.swipeToRefresh = false
        assertFalse(prefs.swipeToRefresh)

        prefs.swipeToRefresh = true
        assertTrue(prefs.swipeToRefresh)
    }

    @Test
    fun `amoledMode round-trip`() {
        prefs.amoledMode = true
        assertTrue(prefs.amoledMode)

        prefs.amoledMode = false
        assertFalse(prefs.amoledMode)
    }

    // -------------------------------------------------------------------------
    // Int preference read/write round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `int preference write then read returns same value`() {
        prefs.searchEngineChoice = 3
        assertEquals(3, prefs.searchEngineChoice)
    }

    @Test
    fun `toolbarPosition round-trip`() {
        prefs.toolbarPosition = ToolbarPosition.TOP.ordinal
        assertEquals(ToolbarPosition.TOP.ordinal, prefs.toolbarPosition)
    }

    // -------------------------------------------------------------------------
    // Float preference read/write round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `float preference write then read returns same value`() {
        prefs.toolbarIconSize = 1.25f
        assertEquals(1.25f, prefs.toolbarIconSize, 0.001f)
    }

    @Test
    fun `fontSizeFactor round-trip`() {
        prefs.fontSizeFactor = 1.5f
        assertEquals(1.5f, prefs.fontSizeFactor, 0.001f)
    }

    // -------------------------------------------------------------------------
    // String preference read/write round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `string preference write then read returns same value`() {
        prefs.customHomepageUrl = "https://example.com"
        assertEquals("https://example.com", prefs.customHomepageUrl)
    }

    @Test
    fun `customAddonCollectionUser string round-trip`() {
        prefs.customAddonCollectionUser = "test-user"
        assertEquals("test-user", prefs.customAddonCollectionUser)
    }

    // -------------------------------------------------------------------------
    // Long preference read/write round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `long preference write then read returns same value`() {
        prefs.bookmarkFolderId = 42L
        assertEquals(42L, prefs.bookmarkFolderId)
    }

    // -------------------------------------------------------------------------
    // Computed properties — shouldUseBottomToolbar
    // -------------------------------------------------------------------------

    @Test
    fun `shouldUseBottomToolbar returns true when toolbarPosition is BOTTOM`() {
        prefs.toolbarPosition = ToolbarPosition.BOTTOM.ordinal
        assertTrue(prefs.shouldUseBottomToolbar)
    }

    @Test
    fun `shouldUseBottomToolbar returns false when toolbarPosition is TOP`() {
        prefs.toolbarPosition = ToolbarPosition.TOP.ordinal
        assertFalse(prefs.shouldUseBottomToolbar)
    }

    @Test
    fun `shouldUseBottomToolbar setter maps to toolbarPosition`() {
        prefs.shouldUseBottomToolbar = false
        assertEquals(ToolbarPosition.TOP.ordinal, prefs.toolbarPosition)

        prefs.shouldUseBottomToolbar = true
        assertEquals(ToolbarPosition.BOTTOM.ordinal, prefs.toolbarPosition)
    }

    // -------------------------------------------------------------------------
    // Computed properties — toolbarPositionType
    // -------------------------------------------------------------------------

    @Test
    fun `toolbarPositionType returns BOTTOM for bottom ordinal`() {
        prefs.toolbarPosition = ToolbarPosition.BOTTOM.ordinal
        assertEquals(ToolbarPosition.BOTTOM, prefs.toolbarPositionType)
    }

    @Test
    fun `toolbarPositionType returns TOP for top ordinal`() {
        prefs.toolbarPosition = ToolbarPosition.TOP.ordinal
        assertEquals(ToolbarPosition.TOP, prefs.toolbarPositionType)
    }

    // -------------------------------------------------------------------------
    // Computed properties — statusBarBlurEnabled
    // -------------------------------------------------------------------------

    @Test
    fun `statusBarBlurEnabled is true on Android S and above`() {
        // This test runs on VANILLA_ICE_CREAM (API 35), which is >= S (31)
        assertTrue(prefs.statusBarBlurEnabled)
    }

    // -------------------------------------------------------------------------
    // getHomepageUrl
    // -------------------------------------------------------------------------

    @Test
    fun `getHomepageUrl returns about homepage`() {
        assertEquals("about:homepage", prefs.getHomepageUrl())
    }

    // -------------------------------------------------------------------------
    // Preference isolation — different keys don't interfere
    // -------------------------------------------------------------------------

    @Test
    fun `preferences are correctly isolated by key`() {
        prefs.javaScriptEnabled = false
        prefs.swipeToRefresh = true

        assertFalse(prefs.javaScriptEnabled)
        assertTrue(prefs.swipeToRefresh)
    }

    // -------------------------------------------------------------------------
    // Persistence across instances
    // -------------------------------------------------------------------------

    @Test
    fun `preferences persist across new instances`() {
        prefs.javaScriptEnabled = false
        prefs.toolbarIconSize = 1.25f

        // Create a new UserPreferences pointing at the same file
        val prefs2 = UserPreferences(context)

        assertFalse(prefs2.javaScriptEnabled)
        assertEquals(1.25f, prefs2.toolbarIconSize, 0.001f)
    }
}
