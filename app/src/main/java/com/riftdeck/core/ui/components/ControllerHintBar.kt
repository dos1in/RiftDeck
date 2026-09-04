package com.riftdeck.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Immutable
data class ControllerHint(
    val key: String,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun ControllerHintBar(
    hints: List<ControllerHint>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(width = 1.dp, color = colors.outline)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { hint ->
            val shape = CutCornerShape(topEnd = 7.dp, bottomStart = 7.dp)
            Row(
                modifier = Modifier
                    .focusProperties { canFocus = false }
                    .clip(shape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = hint.label,
                        onClick = hint.onClick,
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CutCornerShape(topEnd = 5.dp, bottomStart = 5.dp))
                        .background(colors.surfaceElevated)
                        .border(
                            width = 1.dp,
                            color = colors.textSecondary,
                            shape = CutCornerShape(topEnd = 5.dp, bottomStart = 5.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = hint.key,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = hint.label,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
