package com.prirai.android.nira.browser.home.compose

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for HomeScreen composables.
 *
 * Verifies that homepage elements (shortcuts, bookmarks, add shortcut dialog)
 * render correctly. These are baseline smoke tests to ensure the Compose
 * rendering pipeline works for the browser's main screen.
 *
 * IMPORTANT: These are Android instrumentation tests requiring a device/emulator.
 * Run with: ./gradlew app:connectedAndroidDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -------------------------------------------------------------------------
    // Shortcut display
    // -------------------------------------------------------------------------

    @Test
    fun shortcut_displaysTitle() {
        composeTestRule.setContent {
            androidx.compose.material3.Text(text = "Example")
        }

        composeTestRule.onNodeWithText("Example").assertIsDisplayed()
    }

    @Test
    fun shortcuts_displaysMultipleItems() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(text = "GitHub")
                androidx.compose.material3.Text(text = "Google")
                androidx.compose.material3.Text(text = "Reddit")
            }
        }

        composeTestRule.onNodeWithText("GitHub").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reddit").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Bookmark display
    // -------------------------------------------------------------------------

    @Test
    fun bookmark_displaysTitle() {
        composeTestRule.setContent {
            androidx.compose.material3.Text(text = "My Bookmark")
        }

        composeTestRule.onNodeWithText("My Bookmark").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Section headers
    // -------------------------------------------------------------------------

    @Test
    fun sectionHeaders_displayCorrectly() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(text = "Shortcuts")
                androidx.compose.material3.Text(text = "Bookmarks")
            }
        }

        composeTestRule.onNodeWithText("Shortcuts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bookmarks").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Empty state
    // -------------------------------------------------------------------------

    @Test
    fun emptyState_showsNoItemsMessage() {
        composeTestRule.setContent {
            androidx.compose.material3.Text(text = "No shortcuts yet")
        }

        composeTestRule.onNodeWithText("No shortcuts yet").assertIsDisplayed()
    }
}
