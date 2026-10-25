package com.example.pathy.ui.components

import com.example.pathy.ui.theme.PathyColors

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎨 MODERN PREMIUM COMPONENTS
 * 
 * Ultra-beautiful UI components with:
 * - Glass morphism effects
 * - Smooth animations
 * - Gradient backgrounds
 * - Premium feel
 * - Better than ANY competitor!
 */

// ============================================
// 🌟 GLASS MORPHISM CARD
// ============================================

@Composable
fun GlassMorphismCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    borderColor: Color = Color.White.copy(alpha = 0.3f),
    blurRadius: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = onClick?.let {
        modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) { it() }
    } ?: modifier
    
    Box(
        modifier = clickableModifier
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

// ============================================
// 🎯 GRADIENT ACTION BUTTON
// ============================================

@Composable
fun GradientActionButton(
    text: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var pressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = if (enabled) gradient else listOf(Color.Gray, Color.DarkGray)
                )
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                pressed = true
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(150)
            pressed = false
        }
    }
}

// ============================================
// 💎 PREMIUM STAT CARD
// ============================================

@Composable
fun PremiumStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    gradient: List<Color>,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stat_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(gradient)
            )
    ) {
        // Glow effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Color.White.copy(alpha = glowAlpha * 0.2f)
                )
        )
        
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with glow
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ============================================
// ⭐ ACHIEVEMENT BADGE
// ============================================

@Composable
fun AchievementBadge(
    title: String,
    icon: ImageVector,
    progress: Float, // 0.0 to 1.0
    totalProgress: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = gradient.map { it.copy(alpha = 0.1f) }
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(gradient)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                brush = Brush.horizontalGradient(gradient)
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = totalProgress,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

