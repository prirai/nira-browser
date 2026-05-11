package com.prirai.android.nira.browser.home.compose

import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import com.prirai.android.nira.browser.shortcuts.ShortcutDao
import com.prirai.android.nira.browser.shortcuts.ShortcutEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for HomeViewModel — the ViewModel for the homepage screen.
 *
 * Uses a StandardTestDispatcher for deterministic coroutine execution.
 * The init block launches loadShortcuts() and loadBookmarks() via
 * viewModelScope.launch which uses withContext(Dispatchers.IO), so tests
 * that assert post-load state advance the dispatcher in two passes:
 * first to start the launch and then after the real IO thread completes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private val mockBookmarkManager: BookmarkManager = mockk(relaxed = true)
    private val mockShortcutDao: ShortcutDao = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockBookmarkManager.root } returns BookmarkFolderItem(null, null, -1)
        every { mockShortcutDao.getAll() } returns emptyList()
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.coroutineContext.cancel()
        }
        Dispatchers.resetMain()
    }

    /** Advance the test dispatcher, letting real IO threads complete between passes. */
    private fun advanceWithPump() {
        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `initial shortcuts list is empty`() {
        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        assertTrue(viewModel.shortcuts.value.isEmpty())
    }

    @Test
    fun `initial bookmarks list is empty`() {
        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        assertTrue(viewModel.bookmarks.value.isEmpty())
    }

    @Test
    fun `initial add shortcut dialog is hidden`() {
        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        assertFalse(viewModel.showAddShortcutDialog.value)
    }

    @Test
    fun `initial bookmark section is expanded`() {
        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        assertTrue(viewModel.isBookmarkSectionExpanded.value)
    }

    @Test
    fun `loadShortcuts fetches from ShortcutDao`() {
        val shortcuts = listOf(
            ShortcutEntity(uid = 1, url = "https://example.com", title = "Example"),
            ShortcutEntity(uid = 2, url = "https://test.com", title = "Test")
        )
        every { mockShortcutDao.getAll() } returns shortcuts

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        assertEquals(2, viewModel.shortcuts.value.size)
        assertEquals("https://example.com", viewModel.shortcuts.value[0].url)
        assertEquals("Example", viewModel.shortcuts.value[0].title)
    }

    @Test
    fun `loadShortcuts limits to 12 items`() {
        val manyShortcuts = (1..20).map {
            ShortcutEntity(uid = it, url = "https://site$it.com", title = "Site $it")
        }
        every { mockShortcutDao.getAll() } returns manyShortcuts

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        assertEquals(12, viewModel.shortcuts.value.size)
    }

    @Test
    fun `loadShortcuts handles null title gracefully`() {
        every { mockShortcutDao.getAll() } returns listOf(
            ShortcutEntity(uid = 1, url = "https://example.com", title = null)
        )

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        assertEquals("", viewModel.shortcuts.value[0].title)
    }

    @Test
    fun `loadShortcuts handles null url gracefully`() {
        every { mockShortcutDao.getAll() } returns listOf(
            ShortcutEntity(uid = 1, url = null, title = "No URL")
        )

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        assertEquals("", viewModel.shortcuts.value[0].url)
    }

    @Test
    fun `loadShortcuts sets empty list on exception`() {
        every { mockShortcutDao.getAll() } throws RuntimeException("DB error")

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        assertTrue(viewModel.shortcuts.value.isEmpty())
    }

    @Test
    fun `loadBookmarks reads from BookmarkManager root`() {
        val root = BookmarkFolderItem(null, null, -1)
        root.add(BookmarkSiteItem("Site 1", "https://site1.com", -1))
        root.add(BookmarkSiteItem("Site 2", "https://site2.com", -1))
        every { mockBookmarkManager.root } returns root

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        assertEquals(2, viewModel.bookmarks.value.size)
        assertFalse(viewModel.bookmarks.value[0].isFolder)
    }

    @Test
    fun `loadBookmarks includes folders`() {
        val root = BookmarkFolderItem(null, null, -1)
        root.add(BookmarkFolderItem("My Folder", null, -1))
        root.add(BookmarkSiteItem("Site", "https://site.com", -1))
        every { mockBookmarkManager.root } returns root

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        assertEquals(2, viewModel.bookmarks.value.size)
        assertTrue(viewModel.bookmarks.value[0].isFolder)
        assertEquals("My Folder", viewModel.bookmarks.value[0].title)
    }

    @Test
    fun `loadBookmarks handles empty root gracefully`() {
        every { mockBookmarkManager.root } returns BookmarkFolderItem(null, null, -1)

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        assertTrue(viewModel.bookmarks.value.isEmpty())
    }

    @Test
    fun `showAddShortcutDialog sets dialog visible`() {
        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        viewModel.showAddShortcutDialog()
        assertTrue(viewModel.showAddShortcutDialog.value)
    }

    @Test
    fun `hideAddShortcutDialog sets dialog hidden`() {
        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        viewModel.showAddShortcutDialog()
        assertTrue(viewModel.showAddShortcutDialog.value)
        viewModel.hideAddShortcutDialog()
        assertFalse(viewModel.showAddShortcutDialog.value)
    }

    @Test
    fun `toggleBookmarkSection collapses expanded section`() {
        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        viewModel.toggleBookmarkSection()
        assertFalse(viewModel.isBookmarkSectionExpanded.value)
    }

    @Test
    fun `toggleBookmarkSection toggles back and forth`() {
        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        viewModel.toggleBookmarkSection()
        viewModel.toggleBookmarkSection()
        assertTrue(viewModel.isBookmarkSectionExpanded.value)
    }

    /** Pump repeatedly to handle nested withContext(IO) chains. */
    private fun pumpUntilSettled(repetitions: Int = 4) {
        repeat(repetitions) { advanceWithPump() }
    }

    @Test
    fun `addShortcut inserts via ShortcutDao and refreshes`() {
        every { mockShortcutDao.getAll() } returns emptyList()

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        every { mockShortcutDao.getAll() } returns listOf(
            ShortcutEntity(uid = 1, url = "https://example.com", title = "Example")
        )

        viewModel.addShortcut("https://example.com", "Example")
        pumpUntilSettled()

        verify { mockShortcutDao.insertAll(any<ShortcutEntity>()) }
        assertEquals(1, viewModel.shortcuts.value.size)
    }

    @Test
    fun `deleteShortcut removes via ShortcutDao and refreshes`() {
        val existing = ShortcutEntity(uid = 1, url = "https://example.com", title = "Example")
        every { mockShortcutDao.getAll() } returns listOf(existing)
        every { mockShortcutDao.loadAllByIds(intArrayOf(1)) } returns listOf(existing)

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        every { mockShortcutDao.getAll() } returns emptyList()

        viewModel.deleteShortcut(ShortcutItem(id = 1, title = "Example", url = "https://example.com"))
        pumpUntilSettled()

        verify { mockShortcutDao.delete(existing) }
        assertTrue(viewModel.shortcuts.value.isEmpty())
    }

    @Test
    fun `deleteShortcut handles missing entity gracefully`() {
        every { mockShortcutDao.getAll() } returns emptyList()
        every { mockShortcutDao.loadAllByIds(intArrayOf(999)) } returns emptyList()

        viewModel = HomeViewModel(mockBookmarkManager, mockShortcutDao)
        pumpUntilSettled()

        viewModel.deleteShortcut(ShortcutItem(id = 999, title = "", url = ""))
        pumpUntilSettled()
    }
}
