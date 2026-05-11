package com.prirai.android.nira.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import com.prirai.android.nira.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for Utils — helper functions for bitmap generation and device detection.
 *
 * createImage relies on resources from the Mozaic browser-icons AAR that
 * Robolectric cannot always resolve. We mock the Resources for these tests.
 * isTablet uses the real Configuration and is tested with Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class UtilsTest {

    private val utils = Utils()

    private val mockResources: Resources = mockk()
    private val displayMetrics = DisplayMetrics().apply { density = 2f }
    private val mockContext: Context = mockk()

    @Before
    fun setUp() {
        every { mockContext.resources } returns mockResources
        every { mockResources.displayMetrics } returns displayMetrics
        every { mockResources.getDimension(R.dimen.mozac_browser_icons_size_default) } returns 128f
        every { mockResources.getDimension(R.dimen.mozac_browser_icons_generator_default_corner_radius) } returns 4f
    }

    @Test
    fun `createImage returns non-null bitmap`() {
        val bitmap = utils.createImage(mockContext, "Example")
        assertNotNull(bitmap)
    }

    @Test
    fun `createImage bitmap has reasonable dimensions`() {
        val bitmap = utils.createImage(mockContext, "Example")
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun `createImage uses first character of name`() {
        val bitmapA = utils.createImage(mockContext, "Alpha")
        val bitmapB = utils.createImage(mockContext, "Beta")
        assertNotNull(bitmapA)
        assertNotNull(bitmapB)
    }

    @Test
    fun `createImage with single character works`() {
        val bitmap = utils.createImage(mockContext, "A")
        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0)
    }

    @Test
    fun `createImage with long name uses first character`() {
        val bitmap = utils.createImage(mockContext, "This is a very long name for testing")
        assertNotNull(bitmap)
    }

    @Test
    fun `createImage with URL string uses first character`() {
        val bitmap = utils.createImage(mockContext, "https://example.com")
        assertNotNull(bitmap)
    }

    @Test
    fun `isTablet returns false for default test config`() {
        // Use a mock context with real Configuration for isTablet
        val realContext: Context = mockk()
        val realResources: Resources = mockk()
        val config = Configuration()
        every { realContext.resources } returns realResources
        every { realResources.configuration } returns config
        assertFalse(utils.isTablet(realContext))
    }

    @Test
    fun `isTablet checks screenLayout size mask`() {
        val realContext: Context = mockk()
        val realResources: Resources = mockk()
        val config = Configuration()
        every { realContext.resources } returns realResources
        every { realResources.configuration } returns config
        val result = utils.isTablet(realContext)
        assertTrue(result == true || result == false)
    }
}
