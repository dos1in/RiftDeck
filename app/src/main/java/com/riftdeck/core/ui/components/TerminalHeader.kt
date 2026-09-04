package com.riftdeck.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.riftdeck.R
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun TerminalHeader(
    section: String,
    status: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ">",
                color = colors.primary,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "/ $section",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(colors.success)
                    .padding(4.dp),
            )
            Text(
                text = status,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
