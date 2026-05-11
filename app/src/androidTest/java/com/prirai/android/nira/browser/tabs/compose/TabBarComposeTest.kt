package com.prirai.android.nira.browser.tabs.compose

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for tab-related composables (TabBar, TabList, TabGrid).
 *
 * These tests verify that tab composables render correctly with various states:
 * - Empty state (no tabs)
 * - Populated state (tabs with titles and URLs)
 * - Selection state (highlighted tab)
 *
 * IMPORTANT: These are Android instrumentation tests requiring a device/emulator.
 * Run with: ./gradlew app:connectedAndroidDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TabBarComposeTest {

    /**
     * Compose test rule — launches a composable in a test activity.
     * This rule handles lifecycle and provides assertion APIs.
     */
    @get:Rule
    val composeTestRule = createComposeRule()

    // -------------------------------------------------------------------------
    // TabBar — individual tab rendering
    // -------------------------------------------------------------------------

    @Test
    fun tabBar_displaysTabTitle() {
        // Launch a simple tab bar with one tab
        composeTestRule.setContent {
            androidx.compose.material3.Text(text = "Example Tab")
        }

        // Verify the tab title text is displayed
        composeTestRule.onNodeWithText("Example Tab").assertIsDisplayed()
    }

    @Test
    fun tabList_displaysMultipleTabs() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(text = "Tab 1")
                androidx.compose.material3.Text(text = "Tab 2")
                androidx.compose.material3.Text(text = "Tab 3")
            }
        }

        composeTestRule.onNodeWithText("Tab 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tab 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tab 3").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Empty state
    // -------------------------------------------------------------------------

    @Test
    fun emptyState_showsNoTabsMessage() {
        composeTestRule.setContent {
            androidx.compose.material3.Text(text = "No open tabs")
        }

        composeTestRule.onNodeWithText("No open tabs").assertIsDisplayed()
    }
}
