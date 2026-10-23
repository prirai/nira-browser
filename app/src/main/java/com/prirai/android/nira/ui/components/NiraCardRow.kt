package com.prirai.android.nira.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One row of a grouped card list — the idiom every Nira list surface shares:
 * rows stack edge-to-edge inside one `surfaceContainerHigh` card, only the
 * first and last rows keep the corner radius, and a divider is drawn between
 * rows. The direction-agnostic [isTopRow]/[isBottomRow] carry the VISUAL
 * position, so a reversed layout (rendered bottom-up) maps its last adapter
 * position to the top row.
 *
 * The awesome bar's suggestion rows and the tab-history chart both render
 * with this composable so the two list surfaces read as one design.
 *
 * @param onClick when null the row renders disabled (the current-page row of
 *   the history chart); a row that acts carries the ripple.
 */
@Composable
fun NiraCardRow(
    title: String,
    isTopRow: Boolean,
    isBottomRow: Boolean,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 24.dp,
    dividerIndent: Dp = 48.dp,
    minRowHeight: Dp = 48.dp,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(
        topStart = if (isTopRow) cornerRadius else 0.dp,
        topEnd = if (isTopRow) cornerRadius else 0.dp,
        bottomStart = if (isBottomRow) cornerRadius else 0.dp,
        bottomEnd = if (isBottomRow) cornerRadius else 0.dp,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minRowHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )

                trailing?.invoke()
            }

            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = dividerIndent),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
