package com.example.pathy.screens

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.components.PathyButton
import com.example.pathy.ui.design.MountainRange
import com.example.pathy.ui.design.TopoBackground
import com.example.pathy.ui.theme.Fern400
import com.example.pathy.ui.theme.Moss500
import com.example.pathy.ui.theme.Sage300
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.PathyDimensions
import kotlinx.coroutines.launch

/**
 * 🎨 STUNNING ONBOARDING EXPERIENCE
 * 
 * Beautiful first-time user experience that showcases app features
 * Better than AllTrails, Strava, and all competitors!
 */

data class OnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val gradient: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                titleRes = R.string.onboarding_discover_title,
                descriptionRes = R.string.onboarding_discover_body,
                icon = Icons.Filled.Explore,
                gradient = listOf(
                    PathyColors.Primary,
                    PathyColors.PrimaryDark
                )
            ),
            OnboardingPage(
                titleRes = R.string.onboarding_navigate_title,
                descriptionRes = R.string.onboarding_navigate_body,
                icon = Icons.Filled.Navigation,
                gradient = listOf(
                    PathyColors.Secondary,
                    PathyColors.SecondaryDark
                )
            ),
            OnboardingPage(
                titleRes = R.string.onboarding_friends_title,
                descriptionRes = R.string.onboarding_friends_body,
                icon = Icons.Filled.Groups,
                gradient = listOf(
                    PathyColors.Accent,
                    PathyColors.Bark
                )
            ),
            OnboardingPage(
                titleRes = R.string.onboarding_progress_title,
                descriptionRes = R.string.onboarding_progress_body,
                icon = Icons.Filled.EmojiEvents,
                gradient = listOf(
                    PathyColors.Highlight,
                    PathyColors.Accent
                )
            )
        )
    }
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        TopoBackground(
            modifier = Modifier.fillMaxSize(),
            color = scheme.primary,
            alpha = if (isDark) 0.10f else 0.07f,
            lineCount = 12
        )
        MountainRange(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp),
            layers = if (isDark) {
                listOf(Moss500.copy(alpha = 0.22f), Fern400.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f))
            } else {
                listOf(Moss500.copy(alpha = 0.14f), Fern400.copy(alpha = 0.18f), Sage300.copy(alpha = 0.30f))
            }
        )
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PathyDimensions.SpaceM),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < pages.size - 1) {
                    TextButton(onClick = onComplete) {
                        Text(
                            text = stringResource(R.string.common_skip),
                            color = scheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            
            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPageContent(pages[page])
            }
            
            // Page indicators and navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PathyDimensions.SpaceXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceL)
            ) {
                // Dot indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(pages.size) { index ->
                        PageIndicator(
                            isActive = index == pagerState.currentPage
                        )
                    }
                }
                
                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(PathyDimensions.RadiusL)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.back))
                        }
                        
                        Spacer(modifier = Modifier.width(PathyDimensions.SpaceM))
                    }
                    
                    // Next/Get Started button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < pages.size - 1) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onComplete()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary
                        ),
                        shape = RoundedCornerShape(PathyDimensions.RadiusL)
                    ) {
                        Text(
                            text = if (pagerState.currentPage < pages.size - 1) stringResource(R.string.common_next) else stringResource(R.string.onboarding_get_started),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(
                1f,
                animationSpec = tween(500)
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PathyDimensions.SpaceXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Gradient disc with icon and two soft halo rings
        Box(
            modifier = Modifier
                .size(280.dp)
                .scale(scale.value),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(280.dp)
                    .border(1.dp, page.gradient.first().copy(alpha = 0.14f), CircleShape)
            )
            Box(
                Modifier
                    .size(240.dp)
                    .border(1.dp, page.gradient.first().copy(alpha = 0.26f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(page.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(PathyDimensions.SpaceXL))
        
        // Title
        Text(
            text = stringResource(page.titleRes),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(PathyDimensions.SpaceM))
        
        // Description
        Text(
            text = stringResource(page.descriptionRes),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PathyDimensions.SpaceM)
        )
    }
}

@Composable
fun PageIndicator(isActive: Boolean) {
    val width by animateDpAsState(
        targetValue = if (isActive) 32.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "indicator_width"
    )
    
    Box(
        modifier = Modifier
            .width(width)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
    )
}
