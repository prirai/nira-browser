package com.prirai.android.nira.browser.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.prirai.android.nira.R

@Composable
fun ProfileCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int, emoji: String) -> Unit
) {
    var profileName by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var selectedEmojiIndex by remember { mutableIntStateOf(0) }
    
    val colors = BrowserProfile.PROFILE_COLORS
    val emojis = BrowserProfile.PROFILE_EMOJIS
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
            ) {
                Text(
                    text = "Create Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("Work, Personal, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Choose Style",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
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
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
fun CombinedEmojiColorOption(
    emoji: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.2f))
            .then(
                if (selected) {
                    Modifier.border(3.dp, color, RoundedCornerShape(16.dp))
                } else {
                    Modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineMedium,
            fontSize = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp)
        )
    }
}

@Composable
fun ColorOption(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(2.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                }
            )
            .clickable(onClick = onClick)
    )
}

@Composable
fun EmojiOption(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun ProfileEditDialog(
    profile: BrowserProfile,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: Int, emoji: String) -> Unit,
    onDelete: () -> Unit
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
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Edit Profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !profile.isDefault
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Choose Style",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!profile.isDefault) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
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
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
