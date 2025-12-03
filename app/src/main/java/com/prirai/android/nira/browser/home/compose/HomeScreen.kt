package com.prirai.android.nira.browser.home.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prirai.android.nira.R

data class ProfileInfo(
    val id: String,
    val name: String,
    val emoji: String,
    val isPrivate: Boolean = false
)

@Composable
fun HomeScreen(
    isPrivateMode: Boolean,
    shortcuts: List<ShortcutItem>,
    bookmarks: List<BookmarkItem>,
    isBookmarkExpanded: Boolean,
    onShortcutClick: (ShortcutItem) -> Unit,
    onShortcutDelete: (ShortcutItem) -> Unit,
    onShortcutAdd: () -> Unit,
    onBookmarkClick: (BookmarkItem) -> Unit,
    onBookmarkToggle: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarksButtonClick: () -> Unit,
    onExtensionsClick: () -> Unit,
    onTabCountClick: () -> Unit,
    onMenuClick: () -> Unit,
    tabCount: Int,
    currentProfile: ProfileInfo,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.background
    
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo section with profile selector
            item {
                LogoSection(
                    isPrivateMode = isPrivateMode,
                    currentProfile = currentProfile,
                    onProfileClick = onProfileClick
                )
            }
            
            // Centered search bar
            item {
                SearchBar(onSearchClick = onSearchClick)
            }
            
            // Only show shortcuts and bookmarks in normal mode
            if (!isPrivateMode) {
                // Shortcuts section
                item {
                    ShortcutsSection(
                        shortcuts = shortcuts,
                        onShortcutClick = onShortcutClick,
                        onShortcutDelete = onShortcutDelete,
                        onAddClick = onShortcutAdd
                    )
                }
                
                // Bookmarks section
                item {
                    BookmarksSection(
                        bookmarks = bookmarks,
                        isExpanded = isBookmarkExpanded,
                        onBookmarkClick = onBookmarkClick,
                        onToggle = onBookmarkToggle
                    )
                }
            } else {
                // Private browsing info
                item {
                    PrivateBrowsingInfo()
                }
            }
        }
        
        // Bottom toolbar
        HomeBottomToolbar(
            onBookmarksClick = onBookmarksButtonClick,
            onSearchClick = onSearchClick,
            onExtensionsClick = onExtensionsClick,
            onTabCountClick = onTabCountClick,
            onMenuClick = onMenuClick,
            tabCount = tabCount,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App icon
            Icon(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "App Icon",
                modifier = Modifier.size(80.dp),
                tint = Color.Unspecified
            )
            
            // App name
            Text(
                text = "Nira",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            // Profile emoji selector (clickable)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onProfileClick)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = currentProfile.emoji,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 24.sp
                    )
                }
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
                        text = "Private Browsing",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSearchClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Search or enter address",
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
    onAddClick: () -> Unit,
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
                text = "Favorites",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Shortcut",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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
                    text = "Add shortcuts by bookmarking sites",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 400.dp),
                userScrollEnabled = false
            ) {
                items(shortcuts) { shortcut ->
                    ShortcutItem(
                        shortcut = shortcut,
                        onClick = { onShortcutClick(shortcut) },
                        onLongClick = { onShortcutDelete(shortcut) }
                    )
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
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Favicon or letter
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
                    // Show first letter or favicon from domain
                    val domain = try {
                        java.net.URL(shortcut.url).host
                    } catch (e: Exception) {
                        null
                    }
                    
                    if (domain != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("https://www.google.com/s2/favicons?domain=$domain&sz=128")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            error = painterResource(id = R.drawable.ic_language),
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Text(
                            text = shortcut.title.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        Text(
            text = shortcut.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun BookmarksSection(
    bookmarks: List<BookmarkItem>,
    isExpanded: Boolean,
    onBookmarkClick: (BookmarkItem) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section header with expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        // Grid - only show when expanded
        if (isExpanded) {
            Spacer(modifier = Modifier.height(12.dp))
            
            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No bookmarks yet. Add bookmarks while browsing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Use a FlowRow or simple grid layout for non-scrollable bookmarks
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Group bookmarks into rows of 4
                    bookmarks.chunked(4).forEach { rowBookmarks ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowBookmarks.forEach { bookmark ->
                                Box(modifier = Modifier.weight(1f)) {
                                    BookmarkItem(
                                        bookmark = bookmark,
                                        onClick = { onBookmarkClick(bookmark) }
                                    )
                                }
                            }
                            // Add empty boxes to fill the row if needed
                            repeat(4 - rowBookmarks.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkItem(
    bookmark: BookmarkItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Icon for folder or site
        Surface(
            shape = if (bookmark.isFolder) RoundedCornerShape(12.dp) else CircleShape,
            color = if (bookmark.isFolder) 
                MaterialTheme.colorScheme.tertiaryContainer 
            else 
                MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (bookmark.isFolder) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    // Show favicon
                    val domain = try {
                        java.net.URL(bookmark.url).host
                    } catch (e: Exception) {
                        null
                    }
                    
                    if (domain != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("https://www.google.com/s2/favicons?domain=$domain&sz=128")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            error = painterResource(id = R.drawable.ic_language),
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Text(
                            text = bookmark.title.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        Text(
            text = bookmark.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
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
                text = "Private Browsing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            PrivateInfoSection(
                title = "What Private Browsing Does",
                description = "Your browsing history, cookies, and site data won't be saved after you close all private tabs."
            )
            
            PrivateInfoSection(
                title = "Downloads and Bookmarks",
                description = "Files you download and bookmarks you create will be kept even after closing private tabs."
            )
            
            PrivateInfoSection(
                title = "Your Activity is Still Visible To",
                description = "Websites you visit, your internet service provider, and your network administrator can still see your browsing activity."
            )
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
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
                        text = "Note: Private browsing doesn't make you anonymous online. For enhanced privacy, consider using a VPN.",
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onSave: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Shortcut")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = false
                    },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com") },
                    isError = urlError,
                    supportingText = if (urlError) {
                        { Text("Please enter a valid URL") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("My Website") },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (url.isBlank()) {
                        urlError = true
                        return@TextButton
                    }
                    
                    // Validate URL
                    val validUrl = try {
                        val finalUrl = if (url.startsWith("http")) url else "https://$url"
                        java.net.URL(finalUrl)
                        finalUrl
                    } catch (e: Exception) {
                        urlError = true
                        return@TextButton
                    }
                    
                    val finalTitle = title.ifBlank {
                        try {
                            java.net.URL(validUrl).host.replace("www.", "")
                        } catch (e: Exception) {
                            "Untitled"
                        }
                    }
                    
                    onSave(validUrl, finalTitle)
                }
            ) {
                androidx.compose.material3.Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Cancel")
            }
        }
    )
}

@Composable
fun HomeBottomToolbar(
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    onExtensionsClick: () -> Unit,
    onTabCountClick: () -> Unit,
    onMenuClick: () -> Unit,
    tabCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bookmarks button
            IconButton(onClick = onBookmarksClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_bookmark),
                    contentDescription = "Bookmarks",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Extensions button
            IconButton(onClick = onExtensionsClick) {
                Icon(
                    painter = painterResource(id = R.drawable.mozac_ic_extension_24),
                    contentDescription = "Extensions",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Search button (center)
            IconButton(onClick = onSearchClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ios_search),
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Tab count button
            IconButton(onClick = onTabCountClick) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .border(
                            width = 2.5.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(4.dp)
                        )
                ) {
                    // Tab count text
                    Text(
                        text = if (tabCount > 99) "∞" else tabCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp
                    )
                }
            }
            
            // Menu button
            IconButton(onClick = onMenuClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ios_menu),
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileSelector(
    currentProfile: ProfileInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = currentProfile.emoji,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 24.sp
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentProfile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (currentProfile.isPrivate) "Private Browsing" else "Current Profile",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Change Profile",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

