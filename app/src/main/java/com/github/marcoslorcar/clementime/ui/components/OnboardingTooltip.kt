package com.github.marcoslorcar.clementime.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.github.marcoslorcar.clementime.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingTooltip(
    text: String,
    title: String? = null,
    show: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        OnboardingTooltipPositionProvider(padding = 16.dp, density = density)
    }

    LaunchedEffect(show) {
        if (show) {
            tooltipState.show()
        } else {
            tooltipState.dismiss()
        }
    }

    TooltipBox(
        positionProvider = positionProvider,
        tooltip = {
            RichTooltip(
                modifier = Modifier.widthIn(max = 280.dp),
                title = title?.let { { Text(text = it, fontWeight = FontWeight.Bold) } },
                action = {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_got_it),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            ) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        state = tooltipState,
        content = content
    )
}

private class OnboardingTooltipPositionProvider(
    private val padding: Dp,
    private val density: androidx.compose.ui.unit.Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val margin = with(density) { padding.roundToPx() }
        
        // Horizontal: center on anchor
        var x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        
        // Boundary checks for X
        if (x < margin) x = margin
        if (x + popupContentSize.width > windowSize.width - margin) {
            x = windowSize.width - popupContentSize.width - margin
        }

        // Vertical: above anchor by default
        var y = anchorBounds.top - popupContentSize.height - margin
        
        // If not enough space above, show below
        if (y < margin) {
            y = anchorBounds.bottom + margin
        }
        
        // Final sanity check for bottom clipping
        if (y + popupContentSize.height > windowSize.height - margin) {
            y = windowSize.height - popupContentSize.height - margin
        }

        return IntOffset(x, y)
    }
}
