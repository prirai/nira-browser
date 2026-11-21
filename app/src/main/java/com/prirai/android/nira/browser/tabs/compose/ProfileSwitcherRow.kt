@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.luminance
import com.prirai.android.nira.browser.profile.profileIconTint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileIcons
import com.prirai.android.nira.ui.components.pressScale

/**
 * The profile switcher the tab sheet carries at its bottom: one pill row on
 * `surfaceContainerHigh` - a pill per profile (Material icon avatar in a
 * tonal circle + name), the Private pill, and the add-profile pill.
 *
 * Material 3 Expressive language: the active pill is its `primaryContainer`
 * container with the emphasized label and the avatar circle filled; resting
 * pills are plain `surface` with `onSurfaceVariant` content. Every pill
 * gives the theme's press-scale feedback. Long-pressing a profile pill
 * opens its edit dialog.
 */
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
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
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
                    icon = ProfileIcons.iconFor(profile.emoji),
                    iconTint = Color(profile.color),
                    background = profile.secondaryColor.takeIf { it != 0 }?.let { Color(it) },
                    label = profile.name,
                    active = !isPrivateMode && profile.id == currentProfileId,
                    onClick = { onProfileClick(profile) },
                    onLongClick = { onProfileLongClick(profile) },
                )
            }

            ProfilePill(
                icon = Icons.Filled.Shield,
                iconTint = Color(com.prirai.android.nira.theme.ColorConstants.PrivateMode.PURPLE),
                background = null,
                label = stringResource(R.string.private_mode_short),
                active = isPrivateMode,
                onClick = onPrivateClick,
                onLongClick = null,
            )

            AddProfilePill(onClick = onAddClick)
        }
    }
}

/**
 * One profile pill, styled like the homepage profile pill: the secondary colour is the pill
 * background and the primary colour tints the icon. Resting pills are just the icon in a
 * circle; the focused pill expands to reveal the profile name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfilePill(
    icon: ImageVector,
    iconTint: Color,
    background: Color?,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val fallback = MaterialTheme.colorScheme.surfaceContainerHighest
    val container by animateColorAsState(
        targetValue = background ?: fallback,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "profilePillContainer",
    )
    val tint = profileIconTint(iconTint, container)
    val textColor = com.prirai.android.nira.browser.profile.profileLabelColor(container)

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = container,
        modifier = Modifier.pressScale(interactionSource),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = interactionSource,
                )
                .height(44.dp)
                .padding(horizontal = 11.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (active) null else label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            AnimatedVisibility(
                visible = active,
                enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, end = 5.dp),
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
            .size(44.dp)
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
                contentDescription = stringResource(R.string.create_profile),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
