package com.example.clementime.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A custom modifier that applies a smooth gradient fade-out to the top and bottom
 * edges of a scrollable list (LazyColumn) to signal that there is scrollable content
 * beyond the current viewport. The fading edge disappears dynamically when the user
 * reaches the beginning/end of the list.
 */
fun Modifier.fadingEdges(
    state: LazyListState,
    topEdgeHeight: Dp = 16.dp,
    bottomEdgeHeight: Dp = 24.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()

        val topFadeHeightPx = topEdgeHeight.toPx()
        val bottomFadeHeightPx = bottomEdgeHeight.toPx()

        // Draw Top Fade
        if (state.canScrollBackward && topFadeHeightPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topFadeHeightPx
                ),
                blendMode = BlendMode.DstIn
            )
        }

        // Draw Bottom Fade
        if (state.canScrollForward && bottomFadeHeightPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomFadeHeightPx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
