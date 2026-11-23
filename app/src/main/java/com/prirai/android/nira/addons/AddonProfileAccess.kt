package com.prirai.android.nira.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.profile.BrowserProfile
import com.prirai.android.nira.browser.profile.ProfileIcons
import com.prirai.android.nira.browser.profile.profileIconTint
import com.prirai.android.nira.ui.components.NiraCardRow

/**
 * One grouped card listing every profile with a switch, plus a private-browsing row, deciding
 * where an extension is active. Shared by the install prompt and the installed-add-on details.
 */
@Composable
internal fun AddonProfileAccessCard(
    profiles: List<BrowserProfile>,
    isProfileEnabled: (BrowserProfile) -> Boolean,
    onProfileChange: (BrowserProfile, Boolean) -> Unit,
    privateAllowed: Boolean,
    onPrivateChange: (Boolean) -> Unit,
) {
    Column {
        profiles.forEachIndexed { index, profile ->
            val enabled = isProfileEnabled(profile)
            NiraCardRow(
                title = profile.name,
                isTopRow = index == 0,
                isBottomRow = false,
                showDivider = true,
                onClick = { onProfileChange(profile, !enabled) },
                leading = {
                    val background = if (profile.secondaryColor != 0) {
                        Color(profile.secondaryColor)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                    val tint = profileIconTint(Color(profile.color), background)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = ProfileIcons.iconFor(profile.emoji),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                trailing = { Switch(checked = enabled, onCheckedChange = null) },
            )
        }
        NiraCardRow(
            title = stringResource(R.string.mozac_feature_addons_settings_allow_in_private_browsing),
            isTopRow = profiles.isEmpty(),
            isBottomRow = true,
            showDivider = false,
            onClick = { onPrivateChange(!privateAllowed) },
            leading = {
                Icon(
                    painter = painterResource(mozilla.components.ui.icons.R.drawable.mozac_ic_private_mode_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailing = { Switch(checked = privateAllowed, onCheckedChange = null) },
        )
    }
}
