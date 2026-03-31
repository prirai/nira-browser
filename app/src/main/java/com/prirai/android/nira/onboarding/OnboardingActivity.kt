package com.prirai.android.nira.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.SearchEngineList
import com.prirai.android.nira.components.toolbar.ToolbarPosition
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.ThemeChoice
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeMode()
        userPreferences = UserPreferences(this)
        setContent {
            NiraTheme {
                OnboardingScreen(
                    userPreferences = userPreferences,
                    onComplete = { completeOnboarding() }
                )
            }
        }
    }

    private fun completeOnboarding() {
        userPreferences.firstLaunch = false
        val intent = Intent(this, BrowserActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun OnboardingScreen(
    userPreferences: UserPreferences,
    onComplete: () -> Unit
) {
    val pageCount = 8
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    var appThemeChoice by remember { mutableStateOf(userPreferences.appThemeChoice) }
    var webThemeChoice by remember { mutableStateOf(userPreferences.webThemeChoice) }
    var searchEngineChoice by remember { mutableStateOf(userPreferences.searchEngineChoice) }
    var toolbarPosition by remember { mutableStateOf(userPreferences.toolbarPosition) }
    var showTabGroupBar by remember { mutableStateOf(userPreferences.showTabGroupBar) }
    var showContextualToolbar by remember { mutableStateOf(userPreferences.showContextualToolbar) }
    var toolbarIconSize by remember { mutableStateOf(userPreferences.toolbarIconSize) }
    var trackingProtection by remember { mutableStateOf(userPreferences.trackingProtection) }
    var safeBrowsing by remember { mutableStateOf(userPreferences.safeBrowsing) }
    var searchSuggestions by remember { mutableStateOf(userPreferences.searchSuggestionsEnabled) }

    val isLastPage = pagerState.currentPage == pageCount - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> ThemePage(
                    appThemeChoice = appThemeChoice,
                    webThemeChoice = webThemeChoice,
                    onAppThemeSelected = { choice ->
                        appThemeChoice = choice
                        userPreferences.appThemeChoice = choice
                        when (choice) {
                            ThemeChoice.LIGHT.ordinal -> {
                                webThemeChoice = ThemeChoice.LIGHT.ordinal
                                userPreferences.webThemeChoice = ThemeChoice.LIGHT.ordinal
                            }
                            ThemeChoice.SYSTEM.ordinal -> {
                                webThemeChoice = ThemeChoice.SYSTEM.ordinal
                                userPreferences.webThemeChoice = ThemeChoice.SYSTEM.ordinal
                            }
                        }
                    },
                    onWebThemeSelected = { choice ->
                        webThemeChoice = choice
                        userPreferences.webThemeChoice = choice
                    }
                )
                2 -> SearchEnginePage(
                    selectedIndex = searchEngineChoice,
                    onEngineSelected = { index ->
                        searchEngineChoice = index
                        userPreferences.searchEngineChoice = index
                    }
                )
                3 -> ToolbarPositionPage(
                    toolbarPosition = toolbarPosition,
                    onToolbarPositionSelected = { position ->
                        toolbarPosition = position
                        userPreferences.toolbarPosition = position
                    }
                )
                4 -> TabBarPage(
                    showTabBar = showTabGroupBar,
                    onTabBarVisibilityChanged = { visible ->
                        showTabGroupBar = visible
                        userPreferences.showTabGroupBar = visible
                    }
                )
                5 -> ContextualToolbarPage(
                    showContextualToolbar = showContextualToolbar,
                    toolbarIconSize = toolbarIconSize,
                    onContextualToolbarChanged = { show ->
                        showContextualToolbar = show
                        userPreferences.showContextualToolbar = show
                    },
                    onIconSizeChanged = { size ->
                        toolbarIconSize = size
                        userPreferences.toolbarIconSize = size
                    }
                )
                6 -> PrivacyPage(
                    trackingProtection = trackingProtection,
                    safeBrowsing = safeBrowsing,
                    searchSuggestions = searchSuggestions,
                    onTrackingProtectionChanged = { v ->
                        trackingProtection = v
                        userPreferences.trackingProtection = v
                    },
                    onSafeBrowsingChanged = { v ->
                        safeBrowsing = v
                        userPreferences.safeBrowsing = v
                    },
                    onSearchSuggestionsChanged = { v ->
                        searchSuggestions = v
                        userPreferences.searchSuggestionsEnabled = v
                    }
                )
                7 -> GetStartedPage(
                    appThemeChoice = appThemeChoice,
                    searchEngineIndex = searchEngineChoice,
                    trackingProtection = trackingProtection,
                    onStart = onComplete
                )
                else -> WelcomePage()
            }
        }

        OnboardingBottomBar(
            currentPage = pagerState.currentPage,
            pageCount = pageCount,
            isLastPage = isLastPage,
            onSkip = onComplete,
            onNext = {
                scope.launch {
                    pagerState.animateScrollToPage(
                        pagerState.currentPage + 1,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                }
            },
            onStart = onComplete
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    pageCount: Int,
    isLastPage: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated pill-style page indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val isActive = index == currentPage
                    val width by animateDpAsState(
                        targetValue = if (isActive) 28.dp else 8.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .size(width, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = isLastPage,
                transitionSpec = {
                    (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
                },
                label = "bottom_buttons"
            ) { lastPage ->
                if (lastPage) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Browsing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSkip) {
                            Text("Skip")
                        }
                        FilledTonalButton(
                            onClick = onNext,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Page 1: Welcome ────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to Nira",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A fast, private browser",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        listOf(
            "• Built on Mozilla's powerful GeckoView engine",
            "• Tab groups to stay organised",
            "• Firefox Account sync across devices",
            "• Powerful extension support",
            "• Multiple profiles for different identities",
            "• Download manager with progress tracking"
        ).forEach { bullet ->
            Text(
                text = bullet,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ─── Page 2: Theme ──────────────────────────────────────────────────────────

@Composable
private fun ThemePage(
    appThemeChoice: Int,
    webThemeChoice: Int,
    onAppThemeSelected: (Int) -> Unit,
    onWebThemeSelected: (Int) -> Unit
) {
    val appThemeOptions = listOf(
        Triple(ThemeChoice.LIGHT.ordinal, "Light", Icons.Rounded.LightMode),
        Triple(ThemeChoice.DARK.ordinal, "Dark", Icons.Rounded.DarkMode),
        Triple(ThemeChoice.SYSTEM.ordinal, "System Default", Icons.Rounded.SettingsBrightness)
    )
    val webThemeOptions = listOf(
        Triple(ThemeChoice.LIGHT.ordinal, "Light pages", Icons.Rounded.LightMode),
        Triple(ThemeChoice.DARK.ordinal, "Dark pages", Icons.Rounded.DarkMode),
        Triple(ThemeChoice.SYSTEM.ordinal, "Follow system", Icons.Rounded.SettingsBrightness)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingPageHeader(
            icon = Icons.Rounded.LightMode,
            title = "Choose Your Theme",
            subtitle = "Personalise how Nira Browser looks"
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "App Theme",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 8.dp)
        )

        OnboardingOptionGroup {
            appThemeOptions.forEachIndexed { index, (value, label, icon) ->
                OnboardingOptionItem(
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (appThemeChoice == value)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    title = label,
                    selected = appThemeChoice == value,
                    onClick = { onAppThemeSelected(value) }
                )
                if (index < appThemeOptions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = appThemeChoice == ThemeChoice.DARK.ordinal,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Web Page Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 8.dp)
                )
                OnboardingOptionGroup {
                    webThemeOptions.forEachIndexed { index, (value, label, icon) ->
                        OnboardingOptionItem(
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (webThemeChoice == value)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            title = label,
                            selected = webThemeChoice == value,
                            onClick = { onWebThemeSelected(value) }
                        )
                        if (index < webThemeOptions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─── Page 3: Search Engine ──────────────────────────────────────────────────

@Composable
private fun SearchEnginePage(
    selectedIndex: Int,
    onEngineSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val engines = remember { SearchEngineList(context).getEngines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingPageHeader(
            icon = Icons.Rounded.Search,
            title = "Pick a Search Engine",
            subtitle = "Choose your default search for browsing"
        )

        Spacer(modifier = Modifier.height(28.dp))

        OnboardingOptionGroup {
            engines.forEachIndexed { index, engine ->
                OnboardingOptionItem(
                    icon = {
                        androidx.compose.foundation.Image(
                            painter = BitmapPainter(engine.icon.asImageBitmap()),
                            contentDescription = engine.name,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                    },
                    title = engine.name,
                    selected = selectedIndex == index,
                    highlightIcon = false,
                    onClick = { onEngineSelected(index) }
                )
                if (index < engines.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─── Page 4: Privacy ────────────────────────────────────────────────────────

@Composable
private fun PrivacyPage(
    trackingProtection: Boolean,
    safeBrowsing: Boolean,
    searchSuggestions: Boolean,
    onTrackingProtectionChanged: (Boolean) -> Unit,
    onSafeBrowsingChanged: (Boolean) -> Unit,
    onSearchSuggestionsChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingPageHeader(
            icon = Icons.Rounded.PrivacyTip,
            title = "Privacy by Default",
            subtitle = "Nira protects you as you browse"
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                listOf(
                    Pair(Icons.Rounded.Block, "Tracker & ad blocking"),
                    Pair(Icons.Rounded.Fingerprint, "Fingerprint protection"),
                    Pair(Icons.Rounded.Shield, "Enhanced Tracking Protection"),
                    Pair(Icons.Rounded.Security, "Strict HTTPS enforcement")
                ).forEach { (icon, text) ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Customise Settings",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 8.dp)
        )

        OnboardingOptionGroup {
            OnboardingOptionItem(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = "Tracking Protection",
                subtitle = "Block trackers and third-party cookies",
                selected = trackingProtection,
                highlightIcon = false,
                onClick = { onTrackingProtectionChanged(!trackingProtection) },
                trailing = {
                    Switch(checked = trackingProtection, onCheckedChange = onTrackingProtectionChanged)
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            OnboardingOptionItem(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = "Safe Browsing",
                subtitle = "Warn about dangerous sites",
                selected = safeBrowsing,
                highlightIcon = false,
                onClick = { onSafeBrowsingChanged(!safeBrowsing) },
                trailing = {
                    Switch(checked = safeBrowsing, onCheckedChange = onSafeBrowsingChanged)
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            OnboardingOptionItem(
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = "Search Suggestions",
                subtitle = "Show suggestions as you type",
                selected = searchSuggestions,
                highlightIcon = false,
                onClick = { onSearchSuggestionsChanged(!searchSuggestions) },
                trailing = {
                    Switch(checked = searchSuggestions, onCheckedChange = onSearchSuggestionsChanged)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─── Page 5: Get Started ────────────────────────────────────────────────────

@Composable
private fun GetStartedPage(
    appThemeChoice: Int,
    searchEngineIndex: Int,
    trackingProtection: Boolean,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    val engines = remember { SearchEngineList(context).getEngines() }
    val engineName = engines.getOrNull(searchEngineIndex)?.name ?: "DuckDuckGo"

    val themeName = when (appThemeChoice) {
        ThemeChoice.LIGHT.ordinal -> "Light"
        ThemeChoice.DARK.ordinal -> "Dark"
        else -> "System Default"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Hero icon
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "You're all set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Here's what you configured",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                SummaryRow(
                    icon = Icons.Rounded.LightMode,
                    label = "Theme",
                    value = themeName
                )
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow(
                    icon = Icons.Rounded.Search,
                    label = "Search Engine",
                    value = engineName
                )
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow(
                    icon = Icons.Rounded.Shield,
                    label = "Tracking Protection",
                    value = if (trackingProtection) "Enabled" else "Disabled"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start Browsing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Pages: Toolbar, Tab Bar, Contextual Toolbar ─────────────────────────────

@Composable
private fun ToolbarPositionPage(
    toolbarPosition: Int,
    onToolbarPositionSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingPageHeader(
            icon = Icons.Rounded.SettingsBrightness,
            title = "Toolbar Position",
            subtitle = "Choose where the address bar appears"
        )

        Spacer(modifier = Modifier.height(28.dp))

        OnboardingOptionGroup {
            listOf(
                Triple(ToolbarPosition.TOP.ordinal, "Top", "Address bar at the top of the screen"),
                Triple(ToolbarPosition.BOTTOM.ordinal, "Bottom", "Address bar at the bottom of the screen")
            ).forEachIndexed { index, (value, label, description) ->
                OnboardingOptionItem(
                    icon = { ToolbarPositionPreview(isTop = value == ToolbarPosition.TOP.ordinal) },
                    title = label,
                    subtitle = description,
                    selected = toolbarPosition == value,
                    onClick = { onToolbarPositionSelected(value) }
                )
                if (index == 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ToolbarPositionPreview(isTop: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp)) {
            if (!isTop) Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            )
            if (isTop) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabBarPage(
    showTabBar: Boolean,
    onTabBarVisibilityChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingPageHeader(
            icon = Icons.Rounded.Star,
            title = "Tab Bar",
            subtitle = "Quick-access bar for switching tab groups"
        )

        Spacer(modifier = Modifier.height(28.dp))

        OnboardingOptionGroup {
            listOf(
                Triple(true, "Show Tab Bar", "Switch between tab groups at a glance"),
                Triple(false, "Hide Tab Bar", "More screen space while browsing")
            ).forEachIndexed { index, (value, label, description) ->
                OnboardingOptionItem(
                    icon = { TabBarPreview(visible = value) },
                    title = label,
                    subtitle = description,
                    selected = showTabBar == value,
                    onClick = { onTabBarVisibilityChanged(value) }
                )
                if (index == 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TabBarPreview(visible: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Spacer(modifier = Modifier.weight(1f))
            if (visible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextualToolbarPage(
    showContextualToolbar: Boolean,
    toolbarIconSize: Float,
    onContextualToolbarChanged: (Boolean) -> Unit,
    onIconSizeChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingPageHeader(
            icon = Icons.Rounded.Shield,
            title = "Contextual Toolbar",
            subtitle = "Toolbar shown while browsing pages"
        )

        Spacer(modifier = Modifier.height(28.dp))

        OnboardingOptionGroup {
            listOf(
                Triple(true, "Show Contextual Toolbar", "Quick-access browser actions while browsing"),
                Triple(false, "Hide Contextual Toolbar", "More vertical space for page content")
            ).forEachIndexed { index, (value, label, description) ->
                OnboardingOptionItem(
                    icon = { ContextualToolbarPreview(visible = value) },
                    title = label,
                    subtitle = description,
                    selected = showContextualToolbar == value,
                    onClick = { onContextualToolbarChanged(value) }
                )
                if (index == 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showContextualToolbar,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Text(
                    text = "Icon Size",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 8.dp)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dotSize = (12 * toolbarIconSize).dp
                                repeat(4) {
                                    Box(
                                        modifier = Modifier
                                            .size(dotSize)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(
                            value = toolbarIconSize,
                            onValueChange = onIconSizeChanged,
                            valueRange = 0.8f..1.5f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Small",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Large",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ContextualToolbarPreview(visible: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (visible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

// ─── Shared list-item helpers ─────────────────────────────────────────────────

@Composable
private fun OnboardingOptionGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column { content() }
    }
}

@Composable
private fun OnboardingOptionItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    highlightIcon: Boolean = true,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (highlightIcon && selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun OnboardingPageHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp)
        )
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
