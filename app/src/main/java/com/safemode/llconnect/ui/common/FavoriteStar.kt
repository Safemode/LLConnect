package com.safemode.llconnect.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Material's minimum touch target, and so the width a row gives up to carry a star. */
val FAVORITE_SLOT = 48.dp

/**
 * A visible star toggle for marking something a favorite, shared so the gesture is identical
 * wherever it appears. Filled and tinted when set; a quiet outline otherwise.
 *
 * [itemName] only reaches a screen reader, where "Set … as favorite" repeated down a list says
 * nothing about which row is about to change.
 */
@Composable
fun FavoriteStar(
    isFavorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    itemName: String,
    modifier: Modifier = Modifier,
) {
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = onFavoriteChange,
        modifier = modifier.size(FAVORITE_SLOT),
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = if (isFavorite) {
                "Remove $itemName as favorite"
            } else {
                "Set $itemName as favorite"
            },
            tint = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
    }
}
