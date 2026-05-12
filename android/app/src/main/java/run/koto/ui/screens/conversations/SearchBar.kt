package run.koto.ui.screens.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import run.koto.ui.theme.KotoTheme

/**
 * AnimatedSearchBar — expands/collapses with no layout jump.
 *
 * Uses AnimatedVisibility with expandHorizontally/shrinkHorizontally
 * instead of AnimatedContent+SizeTransform which causes TopAppBar reflow jank.
 *
 * CL-04: Spring expand/collapse, auto-focus on expand, clear (X) button.
 */
@Composable
fun AnimatedSearchBar(
    expanded       : Boolean,
    query          : String,
    onQueryChange  : (String) -> Unit,
    onExpandToggle : () -> Unit,
    modifier       : Modifier = Modifier,
) {
    val colors = KotoTheme.colors

    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        // Search icon — visible when collapsed
        AnimatedVisibility(
            visible = !expanded,
            enter   = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
            exit    = fadeOut(spring(stiffness = Spring.StiffnessHigh)),
        ) {
            IconButton(onClick = onExpandToggle) {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = "Search conversations",
                    tint               = colors.onSurfaceLow,
                )
            }
        }

        // Expanded search field — expands from right
        AnimatedVisibility(
            visible = expanded,
            enter   = expandHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMediumLow,
                ),
                expandFrom = Alignment.End,
            ) + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
            exit = shrinkHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMedium,
                ),
                shrinkTowards = Alignment.End,
            ) + fadeOut(spring(stiffness = Spring.StiffnessHigh)),
        ) {
            SearchTextField(
                query         = query,
                onQueryChange = onQueryChange,
                onClose       = {
                    onQueryChange("")
                    onExpandToggle()
                },
            )
        }
    }
}

@Composable
private fun SearchTextField(
    query         : String,
    onQueryChange : (String) -> Unit,
    onClose       : () -> Unit,
) {
    val colors   = KotoTheme.colors
    val spacing  = KotoTheme.spacing
    val focusReq = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusReq.requestFocus()
    }

    BasicTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = Modifier
            .fillMaxWidth()
            .focusRequester(focusReq),
        singleLine    = true,
        textStyle     = KotoTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush   = SolidColor(colors.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(spacing.md))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = spacing.md),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = null,
                    tint               = colors.onSurfaceMuted,
                    modifier           = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text  = "Поиск...",
                            style = KotoTheme.typography.bodyLarge,
                            color = colors.onSurfaceMuted,
                        )
                    }
                    innerTextField()
                }
                // Close/clear button
                IconButton(
                    onClick  = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Close search",
                        tint               = colors.onSurfaceLow,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}
