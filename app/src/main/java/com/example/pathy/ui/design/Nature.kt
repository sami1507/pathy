package com.example.pathy.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pathy.ui.theme.Bark700
import com.example.pathy.ui.theme.Forest700
import com.example.pathy.ui.theme.Forest800
import com.example.pathy.ui.theme.Fern400
import com.example.pathy.ui.theme.Moss500
import com.example.pathy.ui.theme.Moss600
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.Sage100
import com.example.pathy.ui.theme.Sand400
import kotlin.math.sin

/**
 * Nature visual language for PATHY: contour lines, mountain silhouettes and soft cards.
 * Everything is drawn in code, so there are no image assets to ship or to load.
 */

/** Thin contour lines, like a topographic map. Sits behind content. */
@Composable
fun TopoBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    alpha: Float = 0.10f,
    lineCount: Int = 9
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.dp.toPx())
        for (i in 0 until lineCount) {
            val baseY = h * (i + 1f) / (lineCount + 1f)
            val amplitude = h * 0.035f
            val path = Path()
            val steps = 10
            path.moveTo(0f, baseY + sin(i * 0.7f) * amplitude)
            for (s in 1..steps) {
                val x = w * s / steps
                val prevX = w * (s - 1f) / steps
                val y = baseY + sin(s * 1.1f + i * 0.6f) * amplitude
                path.quadraticBezierTo((prevX + x) / 2f, y - amplitude * 0.8f, x, y)
            }
            drawPath(path, color.copy(alpha = alpha), style = stroke)
        }
    }
}

/** Layered mountain silhouettes along the bottom edge of a hero. */
@Composable
fun MountainRange(
    modifier: Modifier = Modifier,
    layers: List<Color> = listOf(
        Moss500.copy(alpha = 0.30f),
        Fern400.copy(alpha = 0.40f),
        Color.White.copy(alpha = 0.14f)
    )
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val peaks = listOf(
            // (x fraction, y fraction) pairs for one ridge line
            0.00f to 0.62f, 0.14f to 0.30f, 0.30f to 0.55f, 0.46f to 0.18f,
            0.62f to 0.50f, 0.78f to 0.28f, 0.92f to 0.48f, 1.00f to 0.36f
        )
        layers.forEachIndexed { index, color ->
            val shift = index * 0.09f
            val path = Path()
            path.moveTo(0f, h)
            peaks.forEachIndexed { p, (xf, yf) ->
                val y = h * ((yf + shift * (index + 1) * 0.5f).coerceAtMost(0.95f))
                val x = w * xf
                if (p == 0) path.lineTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, color)
        }
    }
}

/**
 * Screen header with a forest gradient, contour lines and mountains.
 * [content] is placed below the title (for example stat tiles or chips).
 */
@Composable
fun MountainHero(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(Forest800, Moss600)))
    ) {
        TopoBackground(modifier = Modifier.matchParentSize(), alpha = 0.10f)
        MountainRange(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            eyebrow?.let {
                Text(
                    text = it.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Sand400
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            content?.let {
                Spacer(modifier = Modifier.height(8.dp))
                it()
            }
        }
    }
}

/**
 * Colored difficulty or trail-type label.
 * - Default: tinted pill for cards and surfaces. Colors brighten in dark theme for contrast.
 * - [onHero]: translucent white pill with a colored dot, for use on the dark [MountainHero].
 */
@Composable
fun NatureTag(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onHero: Boolean = false
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val textColor = when {
        onHero -> Color.White
        isDark -> lerp(color, Color.White, 0.35f)
        else -> color
    }
    val background = when {
        onHero -> Color.White.copy(alpha = 0.18f)
        isDark -> color.copy(alpha = 0.22f)
        else -> color.copy(alpha = 0.14f)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (onHero) color else textColor))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

/** Small icon + value + label card, used for distance, time and similar stats. */
@Composable
fun NatureStatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Sage100),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Moss600, modifier = Modifier.size(20.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Section title with an optional action on the trailing side. */
@Composable
fun NatureSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction).padding(4.dp)
            )
        }
    }
}

/** Surface card with a soft border and no heavy shadow. Clickable when [onClick] is set. */
@Composable
fun NatureCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = shape, clip = false)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * The one main thing to do on a screen: a wide forest-green button with a round icon.
 * Use it once per screen; secondary actions are [NatureActionTile]s.
 */
@Composable
fun NaturePrimaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Moss600
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Forest700, Moss600)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f)
        )
    }
}

/** Card with a tinted round icon above a short label. Two or three of these sit in a row. */
@Composable
fun NatureActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NatureCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Preview-only helper, kept here so the screenshot test uses the same colors as the app. */
internal val DesignBrandStrip: List<Color> = listOf(Forest800, Moss600, Fern400, Sand400, Bark700)
