package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.ui.components.pressScale

/**
 * The profile switcher the tab sheet carries at its bottom: one pill row on
 * `surfaceContainerHigh` - a pill per profile (emoji + name), the Private
 * pill, and the add-profile pill. The active profile pill speaks through its
 * `primaryContainer` container and emphasized label; every pill gives the
 * theme's press-scale feedback. Long-pressing a profile pill opens its edit
 * dialog.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileSwitcherRow(
    profiles: List<BrowserProfile>,
    currentProfileId: String,
    isPrivateMode: Boolean,
    onProfileClick: (BrowserProfile) -> Unit,
    onProfileLongClick: (BrowserProfile) -> Unit,
    onPrivateClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            profiles.forEach { profile ->
                ProfilePill(
                    emoji = profile.emoji,
                    label = profile.name,
                    active = !isPrivateMode && profile.id == currentProfileId,
                    tint = Color(profile.color),
                    onClick = { onProfileClick(profile) },
                    onLongClick = { onProfileLongClick(profile) },
                )
            }

            ProfilePill(
                emoji = "🕵️",
                label = "Private",
                active = isPrivateMode,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onPrivateClick,
                onLongClick = null,
            )

            AddProfilePill(onClick = onAddClick)
        }
    }
}

/**
 * One profile pill. The active state is its `primaryContainer` container
 * with the emphasized label - no accent stroke (the group-color tint only
 * colors the inactive pill's emoji, functional identity like group colors).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfilePill(
    emoji: String,
    label: String,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier.pressScale(interactionSource),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = interactionSource,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleSmall,
            )
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style =
                        if (active) {
                            MaterialTheme.typography.labelLargeEmphasized
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        tint
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The add-profile pill: a circular surface pill with the add glyph. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddProfilePill(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .size(40.dp)
            .pressScale(interactionSource),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    interactionSource = interactionSource,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(com.prirai.android.nira.R.string.create_profile),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
