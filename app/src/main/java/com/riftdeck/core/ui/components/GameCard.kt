package com.riftdeck.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun GameCard(
    game: Game,
    artworkDescription: String,
    favoriteDescription: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    up: FocusRequester? = null,
    down: FocusRequester? = null,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val shape = CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp)

    Column(
        modifier = modifier
            .width(width)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (up != null) this.up = up
                if (down != null) this.down = down
                if (left != null) this.left = left
                if (right != null) this.right = right
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .background(if (focused) colors.surfaceElevated else colors.surface, shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) colors.focusBorder else colors.outline,
                shape = shape,
            )
            .semantics {
                selected = focused
                if (game.favorite) stateDescription = favoriteDescription
            }
            .controllerClickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            GameArtwork(
                game = game,
                contentDescription = artworkDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
            )
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(colors.primary)
                        .size(8.dp),
                )
            }
            if (game.favorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(colors.background)
                        .border(1.dp, colors.tertiary)
                        .padding(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.tertiary)
                            .size(7.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = game.title,
                modifier = Modifier.weight(1f),
                color = colors.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = game.releaseYear?.toString().orEmpty(),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
