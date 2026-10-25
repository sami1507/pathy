package com.example.pathy.components

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pathy.ui.theme.PathyColors

/**
 * Pulsing Scout button. [onScoutClick] opens the full Scout chat screen (ScoutActivity).
 */
@Composable
fun ScoutFloatingButton(
    modifier: Modifier = Modifier,
    onScoutClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scout_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onScoutClick,
            containerColor = PathyColors.Secondary,
            contentColor = Color.White,
            modifier = Modifier
                .scale(scale)
                .size(64.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = stringResource(R.string.home_scout),
                modifier = Modifier.size(28.dp)
            )
        }

        // Small dot showing Scout is available
        Surface(
            modifier = Modifier
                .offset(x = 48.dp, y = (-4).dp)
                .size(14.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = PathyColors.Primary
        ) {}
    }
}
