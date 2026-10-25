package com.example.pathy.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.PathyDimensions

/**
 * 🎨 PREMIUM UI COMPONENTS LIBRARY
 * 
 * Beautiful, reusable components with consistent design
 * Better than any trail app on the market!
 */

// ============================================
// 🔘 BUTTONS
// ============================================

@Composable
fun PathyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: ButtonVariant = ButtonVariant.PRIMARY
) {
    val colors = when (variant) {
        ButtonVariant.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = PathyColors.Primary,
            contentColor = Color.White
        )
        ButtonVariant.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = PathyColors.Secondary,
            contentColor = Color.White
        )
        ButtonVariant.OUTLINE -> ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = PathyColors.Primary
        )
        ButtonVariant.TEXT -> ButtonDefaults.textButtonColors(
            contentColor = PathyColors.Primary
        )
        ButtonVariant.DANGER -> ButtonDefaults.buttonColors(
            containerColor = PathyColors.Error,
            contentColor = Color.White
        )
    }
    
    val scale = remember { Animatable(1f) }
    
    LaunchedEffect(enabled) {
        if (!enabled) {
            scale.animateTo(0.95f, animationSpec = tween(150))
        } else {
            scale.animateTo(1f, animationSpec = tween(150))
        }
    }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .height(PathyDimensions.ButtonHeight)
            .scale(scale.value),
        enabled = enabled && !loading,
        colors = colors,
        shape = RoundedCornerShape(PathyDimensions.RadiusL),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (variant == ButtonVariant.OUTLINE || variant == ButtonVariant.TEXT) 0.dp else 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

enum class ButtonVariant {
    PRIMARY, SECONDARY, OUTLINE, TEXT, DANGER
}

// ============================================
// 🏷️ BADGES & CHIPS
// ============================================

@Composable
fun PathyBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    backgroundColor: Color = PathyColors.Primary,
    textColor: Color = Color.White
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PathyDimensions.RadiusS),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = PathyDimensions.SpaceS,
                vertical = 4.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = textColor
                )
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

// ============================================
// 📊 STATS DISPLAY
// ============================================

@Composable
fun PathyStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = PathyColors.Primary,
    trend: Trend? = null
) {
    val scale = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    
    Card(
        modifier = modifier.scale(scale.value),
        shape = RoundedCornerShape(PathyDimensions.RadiusM),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = PathyDimensions.ElevationXS)
    ) {
        Column(
            modifier = Modifier.padding(PathyDimensions.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
        ) {
            // Icon
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Value with trend
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                trend?.let {
                    Icon(
                        imageVector = if (it == Trend.UP) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (it == Trend.UP) PathyColors.Success else PathyColors.Error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Label
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

enum class Trend {
    UP, DOWN
}

// ============================================
// 🏷️ TAGS
// ============================================

@Composable
fun PathyTagRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    color: Color = PathyColors.Primary
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
    ) {
        tags.forEach { tag ->
            PathyBadge(
                text = tag,
                backgroundColor = color.copy(alpha = 0.15f),
                textColor = color
            )
        }
    }
}

// ============================================
// 📏 LINEAR PROGRESS (for EnhancedProfileScreen)
// ============================================

@Composable
fun PathyLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = PathyColors.Primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "linear_progress"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(PathyDimensions.RadiusFull))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color, color.copy(alpha = 0.8f))
                    )
                )
                .clip(RoundedCornerShape(PathyDimensions.RadiusFull))
        )
    }
}
