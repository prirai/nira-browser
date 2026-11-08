@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.browser.profile

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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

@Composable
fun ProfileCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int, emoji: String) -> Unit,
    onImport: (() -> Unit)? = null
) {
    var profileName by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var selectedEmojiIndex by remember { mutableIntStateOf(0) }
    
    val colors = BrowserProfile.PROFILE_COLORS
    val emojis = BrowserProfile.PROFILE_EMOJIS
    
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        CombinedEmojiColorOption(
                            emoji = emojis[index],
                            color = Color(colors[index % colors.size]),
                            selected = selectedEmojiIndex == index,
                            onClick = {
                                selectedEmojiIndex = index
                                selectedColorIndex = index % colors.size
                            }
                        )
                    }
                }

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
                            colors[selectedColorIndex],
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CombinedEmojiColorOption(
    emoji: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressScale(interactionSource)
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            // One selection language, the same as focused tab pills: the
            // selected option is the primaryContainer, resting options are
            // fully transparent - no tinted fill, no accent stroke. The
            // icon's muted color carries the palette identity.
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                interactionSource = interactionSource,
            ),
        contentAlignment = Alignment.Center
    ) {
        // Icon avatar, not emoji - the stored key maps through ProfileIcons.
        // The tint is the muted, theme-adaptive variant of the palette
        // color, never the raw saturated accent.
        Icon(
            imageVector = ProfileIcons.iconFor(emoji),
            contentDescription = null,
            tint = color.mutedAdaptive(),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun ProfileEditDialog(
    profile: BrowserProfile,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int, emoji: String) -> Unit,
    onDelete: () -> Unit,
    onExport: (() -> Unit)? = null
) {
    var profileName by remember { mutableStateOf(profile.name) }
    var selectedColorIndex by remember { 
        mutableIntStateOf(BrowserProfile.PROFILE_COLORS.indexOf(profile.color).coerceAtLeast(0))
    }
    var selectedEmojiIndex by remember { 
        mutableIntStateOf(BrowserProfile.PROFILE_EMOJIS.indexOf(profile.emoji).coerceAtLeast(0))
    }
    
    val colors = BrowserProfile.PROFILE_COLORS
    val emojis = BrowserProfile.PROFILE_EMOJIS
    
    NiraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        CombinedEmojiColorOption(
                            emoji = emojis[index],
                            color = Color(colors[index % colors.size]),
                            selected = selectedEmojiIndex == index,
                            onClick = {
                                selectedEmojiIndex = index
                                selectedColorIndex = index % colors.size
                            }
                        )
                    }
                }

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
                            colors[selectedColorIndex],
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
