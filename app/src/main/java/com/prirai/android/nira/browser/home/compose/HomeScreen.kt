@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.home.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import com.prirai.android.nira.ui.components.mutedAdaptive
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import com.prirai.android.nira.ui.components.NiraCardRow
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.tabs.compose.FaviconImageFromUrl
import com.prirai.android.nira.settings.HomepageChoice
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.components.pressScale
import mozilla.components.browser.state.state.BrowserState

data class ProfileInfo(
    val id: String,
    val name: String,
    val emoji: String,
    val isPrivate: Boolean = false,
    val color: Int = 0,
    val secondaryColor: Int = 0
)

data class JumpBackInItem(
    val id: String,
    val url: String,
    val title: String,
    val tabId: String? = null,
)

data class RecentlyClosedItem(
    val id: String,
    val url: String,
    val title: String
)

private fun String.isHomepageCardUrl(): Boolean {
    return isNotBlank() && !startsWith("about:") && !startsWith("chrome:")
}

fun BrowserState.jumpBackInItems(
    historyItems: List<JumpBackInItem> = emptyList(),
    limit: Int = 4,
): List<JumpBackInItem> {
    val openTabs = tabs
        .filter { tab ->
            !tab.content.private &&
                tab.id != selectedTabId &&
                tab.content.url.isHomepageCardUrl()
        }
        .sortedByDescending { it.lastAccess }
        .map { tab ->
            JumpBackInItem(
                id = tab.id,
                url = tab.content.url,
                title = tab.content.title.ifBlank { tab.content.url },
                tabId = tab.id,
            )
        }

    val usedUrls = openTabs.map { it.url }.toMutableSet()
    closedTabs.forEach { usedUrls.add(it.url) }

    val fromHistory = historyItems.filter { item ->
        item.url.isHomepageCardUrl() && usedUrls.add(item.url)
    }

    return (openTabs + fromHistory).take(limit)
}

fun BrowserState.recentlyClosedItems(limit: Int = 4): List<RecentlyClosedItem> {
    return closedTabs
        .filter { tab -> !tab.private && tab.url.isHomepageCardUrl() }
        .take(limit)
        .map { tab ->
            RecentlyClosedItem(
                id = tab.id,
                url = tab.url,
                title = tab.title.ifBlank { tab.url }
            )
        }
}

@Composable
fun HomeScreen(
    isPrivateMode: Boolean,
    shortcuts: List<ShortcutItem>,
    currentProfile: ProfileInfo,
    onProfileClick: () -> Unit,
    onShortcutClick: (ShortcutItem) -> Unit,
    onShortcutDelete: (ShortcutItem) -> Unit,
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    jumpBackInItems: List<JumpBackInItem> = emptyList(),
    recentlyClosedItems: List<RecentlyClosedItem> = emptyList(),
    onJumpBackInClick: (JumpBackInItem) -> Unit = {},
    onRecentlyClosedClick: (RecentlyClosedItem) -> Unit = {},
    backgroundImageUrl: String? = null,
    isToolbarAtTop: Boolean = false,
    homepageType: Int = 0,
    wallpaperDimmed: Boolean = true,
    modifier: Modifier = Modifier
) {
    isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.background

    // Get window insets for edge-to-edge support
    val windowInsets = WindowInsets.systemBars
    val topPadding = with(LocalDensity.current) { windowInsets.getTop(this).toDp() }
    val bottomPadding = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }

    // Add toolbar height when toolbar is at top (approximately 56dp for toolbar)
    val toolbarHeight = if (isToolbarAtTop) 56.dp else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        // Background image layer
        if (backgroundImageUrl != null && backgroundImageUrl.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backgroundImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.homepage_background),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Add semi-transparent overlay for better content readability (controlled by preference)
            if (wallpaperDimmed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor.copy(alpha = 0.85f))
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backgroundImageUrl == null || backgroundImageUrl.isEmpty()) {
                        Modifier.background(backgroundColor)
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = topPadding + toolbarHeight + 16.dp,  // Add system bar + toolbar padding
                end = 16.dp,
                bottom = bottomPadding + 100.dp  // Add navigation bar padding + toolbar space
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo section with profile switcher (hide for BLANK_PAGE)
            if (homepageType != HomepageChoice.BLANK_PAGE.ordinal) {
                item {
                    LogoSection(
                        isPrivateMode = isPrivateMode,
                        currentProfile = currentProfile,
                        onProfileClick = onProfileClick
                    )
                }
            }

            // Show shortcuts, bookmarks, and recents only for default VIEW mode and in normal mode
            if (!isPrivateMode && homepageType == HomepageChoice.VIEW.ordinal) {
                // Jump Back In section
                if (jumpBackInItems.isNotEmpty()) {
                    item {
                        JumpBackInSection(
                            items = jumpBackInItems,
                            onClick = onJumpBackInClick
                        )
                    }
                }

                // Recently Closed section
                if (recentlyClosedItems.isNotEmpty()) {
                    item {
                        RecentlyClosedSection(
                            items = recentlyClosedItems,
                            onClick = onRecentlyClosedClick
                        )
                    }
                }

                // Shortcuts section
                item {
                    ShortcutsSection(
                        shortcuts = shortcuts,
                        onShortcutClick = onShortcutClick,
                        onShortcutDelete = onShortcutDelete,
                    )
                }

                item {
                    LibraryLinksRow(
                        onHistoryClick = onHistoryClick,
                        onBookmarksClick = onBookmarksClick,
                    )
                }
            } else if (isPrivateMode && homepageType != HomepageChoice.BLANK_PAGE.ordinal) {
                // Private browsing info (only if not blank page)
                item {
                    PrivateBrowsingInfo()
                }
            }
        }
    }
}

@Composable
fun LogoSection(
    isPrivateMode: Boolean,
    currentProfile: ProfileInfo,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Profile pill (clickable) - Material icon, not emoji. The container is the muted,
        // theme-adaptive variant of the profile color; the icon tint follows its luminance.
        val primary = if (currentProfile.color != 0) Color(currentProfile.color) else MaterialTheme.colorScheme.primary
        val pillColor = currentProfile.secondaryColor.takeIf { it != 0 }?.let { Color(it) }
            ?: MaterialTheme.colorScheme.surfaceContainerHigh
        val iconTint = com.prirai.android.nira.browser.profile.profileIconTint(primary, pillColor)
        val textColor = com.prirai.android.nira.browser.profile.profileLabelColor(pillColor)
        Surface(
            shape = CircleShape,
            color = pillColor,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onProfileClick)
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = com.prirai.android.nira.browser.profile.ProfileIcons.iconFor(currentProfile.emoji),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = currentProfile.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Private badge
        if (isPrivateMode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.private_browsing),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource, pressedScale = 0.98f),
        // The same pill the real search fields wear, so the homepage affordance
        // reads as the same control as NiraSearchField before it is tapped.
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(interactionSource = interactionSource, onClick = onSearchClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.search),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ShortcutsSection(
    shortcuts: List<ShortcutItem>,
    onShortcutClick: (ShortcutItem) -> Unit,
    onShortcutDelete: (ShortcutItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.favorites),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grid
        if (shortcuts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.add_go_to_sites),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                shortcuts.chunked(4).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { shortcut ->
                            ShortcutItem(
                                shortcut = shortcut,
                                onClick = { onShortcutClick(shortcut) },
                                onLongClick = { onShortcutDelete(shortcut) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(4 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutItem(
    shortcut: ShortcutItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .aspectRatio(1f)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (shortcut.icon != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(shortcut.icon)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    FaviconImageFromUrl(
                        url = shortcut.url,
                        title = shortcut.title,
                        size = 32.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = shortcut.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun LibraryLinksRow(
    onHistoryClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LibraryLinkButton(
            label = stringResource(R.string.action_history),
            icon = mozilla.components.ui.icons.R.drawable.mozac_ic_history_24,
            onClick = onHistoryClick,
            modifier = Modifier.weight(1f)
        )
        LibraryLinkButton(
            label = stringResource(R.string.action_bookmarks),
            icon = mozilla.components.ui.icons.R.drawable.mozac_ic_bookmark_24,
            onClick = onBookmarksClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LibraryLinkButton(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .pressScale(interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun JumpBackInSection(
    items: List<JumpBackInItem>,
    onClick: (JumpBackInItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.jump_back_in),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val visible = items.take(4)
        visible.forEachIndexed { index, item ->
            NiraCardRow(
                title = item.title,
                isTopRow = index == 0,
                isBottomRow = index == visible.lastIndex,
                showDivider = index != visible.lastIndex,
                onClick = { onClick(item) },
                minRowHeight = 56.dp,
                leading = {
                    FaviconImageFromUrl(
                        url = item.url,
                        title = item.title,
                        size = 24.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun RecentlyClosedSection(
    items: List<RecentlyClosedItem>,
    onClick: (RecentlyClosedItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recently_closed),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val visible = items.take(4)
        visible.forEachIndexed { index, item ->
            NiraCardRow(
                title = item.title,
                isTopRow = index == 0,
                isBottomRow = index == visible.lastIndex,
                showDivider = index != visible.lastIndex,
                onClick = { onClick(item) },
                minRowHeight = 56.dp,
                leading = {
                    FaviconImageFromUrl(
                        url = item.url,
                        title = item.title,
                        size = 24.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun PrivateBrowsingInfo(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.private_browsing),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.primary
            )

            PrivateInfoSection(
                title = stringResource(R.string.private_browsing_does),
                description = stringResource(R.string.private_browsing_does_desc)
            )

            PrivateInfoSection(
                title = stringResource(R.string.private_downloads_bookmarks),
                description = stringResource(R.string.private_downloads_bookmarks_desc)
            )

            PrivateInfoSection(
                title = stringResource(R.string.private_activity_visible),
                description = stringResource(R.string.private_activity_visible_desc)
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.private_browsing_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun PrivateInfoSection(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}

