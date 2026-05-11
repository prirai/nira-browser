package com.prirai.android.nira.ext

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import mozilla.components.support.utils.SafeIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for Android extension functions in the ext/ package.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class ExtensionFunctionsTest {

    @Test
    fun `getParcelableCompat returns value from bundle`() {
        val bundle = Bundle().apply {
            putParcelable("key", TestParcelable("test-value"))
        }
        val result: TestParcelable? = bundle.getParcelableCompat("key")
        assertEquals("test-value", result?.value)
    }

    @Test
    fun `getParcelableCompat returns null for missing key`() {
        val bundle = Bundle()
        val result: TestParcelable? = bundle.getParcelableCompat("nonexistent")
        assertNull(result)
    }

    @Test
    fun `getParcelableArrayListCompat returns list from bundle`() {
        val items = arrayListOf(TestParcelable("a"), TestParcelable("b"))
        val bundle = Bundle().apply { putParcelableArrayList("key", items) }
        val result: ArrayList<TestParcelable>? = bundle.getParcelableArrayListCompat("key")
        assertEquals(2, result?.size)
        assertEquals("a", result?.get(0)?.value)
    }

    @Test
    fun `getParcelableArrayListCompat returns null for missing key`() {
        val bundle = Bundle()
        val result: ArrayList<TestParcelable>? = bundle.getParcelableArrayListCompat("key")
        assertNull(result)
    }

    @Test
    fun `getParcelableExtraCompat returns value from intent`() {
        val intent = Intent().apply { putExtra("key", TestParcelable("intent-value")) }
        val result: TestParcelable? = intent.getParcelableExtraCompat("key")
        assertEquals("intent-value", result?.value)
    }

    @Test
    fun `getParcelableExtraCompat returns null for missing extra`() {
        val intent = Intent()
        val result: TestParcelable? = intent.getParcelableExtraCompat("key")
        assertNull(result)
    }

    @Test
    fun `getParcelableArrayListExtraCompat returns list from intent`() {
        val items = arrayListOf(TestParcelable("x"), TestParcelable("y"))
        val intent = Intent().apply { putParcelableArrayListExtra("key", items) }
        val result: ArrayList<TestParcelable>? = intent.getParcelableArrayListExtraCompat("key")
        assertEquals(2, result?.size)
    }

    @Test
    fun `getParcelableArrayListExtraCompat returns null for missing extra`() {
        val intent = Intent()
        val result: ArrayList<TestParcelable>? = intent.getParcelableArrayListExtraCompat("key")
        assertNull(result)
    }
}

data class TestParcelable(val value: String) : Parcelable {
    constructor(parcel: android.os.Parcel) : this(parcel.readString() ?: "")
    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) { parcel.writeString(value) }
    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TestParcelable> {
        override fun createFromParcel(parcel: android.os.Parcel) = TestParcelable(parcel)
        override fun newArray(size: Int) = arrayOfNulls<TestParcelable>(size)
    }
}
