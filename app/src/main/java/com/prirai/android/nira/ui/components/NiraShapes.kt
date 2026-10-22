package com.prirai.android.nira.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * The shape behind a state icon (empty, error, message), distinct from the theme's
 * rounded rectangles so it reads as a container, not a card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun niraStateShape(): Shape = MaterialShapes.Cookie9Sided.toShape()
