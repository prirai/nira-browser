@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.profile

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.components.pressScale
import com.prirai.android.nira.ui.components.mutedAdaptive
import com.prirai.android.nira.browser.profile.ProfileIcons
import com.prirai.android.nira.R
import com.prirai.android.nira.theme.ColorConstants

@Composable
fun ProfileCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int, secondaryColor: Int, emoji: String) -> Unit,
    onImport: (() -> Unit)? = null
) {
    var profileName by remember { mutableStateOf("") }
    var primaryColor by remember { mutableIntStateOf(ColorConstants.Profiles.NEUTRAL_COLOR) }
    var secondaryColor by remember { mutableIntStateOf(0) }
    var selectedEmojiIndex by remember { mutableIntStateOf(0) }
    
    val emojis = BrowserProfile.PROFILE_EMOJIS
    
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_profile)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    placeholder = { Text(stringResource(R.string.profile_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = stringResource(R.string.choose_style),
                    style = MaterialTheme.typography.titleSmallEmphasized
                )

                ProfileStylePicker(
                    selectedEmojiIndex = selectedEmojiIndex,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    onEmojiSelected = { selectedEmojiIndex = it },
                    onPrimaryChange = { primaryColor = it },
                    onSecondaryChange = { secondaryColor = it }
                )

                if (onImport != null) {
                    TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.import_zip))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (profileName.isNotBlank()) {
                        onConfirm(
                            profileName.trim(),
                            primaryColor,
                            secondaryColor,
                            emojis[selectedEmojiIndex]
                        )
                    }
                },
                enabled = profileName.isNotBlank()
            ) {
                Text(stringResource(R.string.create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Icon grid (neutral tint) followed by the dual-colour picker; the primary colour tints the selected icon. */
@Composable
private fun ProfileStylePicker(
    selectedEmojiIndex: Int,
    primaryColor: Int,
    secondaryColor: Int,
    onEmojiSelected: (Int) -> Unit,
    onPrimaryChange: (Int) -> Unit,
    onSecondaryChange: (Int) -> Unit
) {
    val emojis = BrowserProfile.PROFILE_EMOJIS
    val selectedTint = Color(primaryColor).mutedAdaptive()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(emojis.size) { index ->
                ProfileIconOption(
                    emoji = emojis[index],
                    selected = selectedEmojiIndex == index,
                    selectedTint = selectedTint,
                    onClick = { onEmojiSelected(index) }
                )
            }
        }

        Text(
            text = stringResource(R.string.profile_color),
            style = MaterialTheme.typography.titleSmallEmphasized
        )

        ProfileColorPicker(
            primary = primaryColor,
            secondary = secondaryColor,
            onPrimaryChange = onPrimaryChange,
            onSecondaryChange = onSecondaryChange
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileIconOption(
    emoji: String,
    selected: Boolean,
    selectedTint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressScale(interactionSource)
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                interactionSource = interactionSource,
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ProfileIcons.iconFor(emoji),
            contentDescription = null,
            tint = if (selected) selectedTint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun ProfileEditDialog(
    profile: BrowserProfile,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int, secondaryColor: Int, emoji: String) -> Unit,
    onDelete: () -> Unit,
    onExport: (() -> Unit)? = null
) {
    var profileName by remember { mutableStateOf(profile.name) }
    var primaryColor by remember { mutableIntStateOf(profile.color) }
    var secondaryColor by remember { mutableIntStateOf(profile.secondaryColor) }
    var selectedEmojiIndex by remember { 
        mutableIntStateOf(BrowserProfile.PROFILE_EMOJIS.indexOf(profile.emoji).coerceAtLeast(0))
    }
    
    val emojis = BrowserProfile.PROFILE_EMOJIS
    
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_profile)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !profile.isDefault
                )

                Text(
                    text = stringResource(R.string.choose_style),
                    style = MaterialTheme.typography.titleSmallEmphasized
                )

                ProfileStylePicker(
                    selectedEmojiIndex = selectedEmojiIndex,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    onEmojiSelected = { selectedEmojiIndex = it },
                    onPrimaryChange = { primaryColor = it },
                    onSecondaryChange = { secondaryColor = it }
                )

                if (onExport != null) {
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_profile_zip))
                    }
                }

                if (!profile.isDefault) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (profileName.isNotBlank()) {
                        onConfirm(
                            profileName.trim(),
                            primaryColor,
                            secondaryColor,
                            emojis[selectedEmojiIndex]
                        )
                    }
                },
                enabled = profileName.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
