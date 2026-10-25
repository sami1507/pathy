package com.example.pathy.screens

import com.example.pathy.i18n.RelativeTime
import android.content.Context
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.design.pathyTopBarSubtitleColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.ai.ChatMessage
import com.example.pathy.ai.ScoutAI
import com.example.pathy.ai.ScoutQuickReplies
import com.example.pathy.ai.ScoutResponse
import com.example.pathy.components.PathyButton
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.PathyDimensions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🤖 SCOUT CHAT SCREEN
 * 
 * Beautiful AI assistant interface with:
 * - Smooth animations
 * - Quick reply buttons
 * - Typing indicators
 * - Message bubbles
 * - Suggestions
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutChatScreen(
    onBack: () -> Unit = {},
    onNavigateToTrails: () -> Unit = {},
    onNavigateToMap: () -> Unit = {}
) {
    val context = LocalContext.current
    val scout = remember { ScoutAI(context) }
    val scope = rememberCoroutineScope()
    
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isTyping by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var showQuickReplies by remember { mutableStateOf(true) }
    
    val listState = rememberLazyListState()
    
    // Welcome message
    LaunchedEffect(Unit) {
        messages = listOf(
            ChatMessage(
                role = "assistant",
                content = context.getString(R.string.scout_greeting),
                timestamp = System.currentTimeMillis()
            )
        )
    }
    
    // Auto-scroll to bottom when new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            ScoutTopBar(onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(PathyDimensions.SpaceM),
                verticalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceM)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
                
                // Typing indicator
                if (isTyping) {
                    item {
                        TypingIndicator()
                    }
                }
            }
            
            // Quick replies
            AnimatedVisibility(
                visible = showQuickReplies && messages.size == 1,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                QuickRepliesSection(
                    onQuickReply = { question ->
                        showQuickReplies = false
                        messageText = question
                    }
                )
            }
            
            // Input area
            MessageInputArea(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        val userMessage = ChatMessage(
                            role = "user",
                            content = messageText,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        messages = messages + userMessage
                        val question = messageText
                        messageText = ""
                        showQuickReplies = false
                        isTyping = true
                        
                        scope.launch {
                            try {
                                val response = scout.chat(question)
                                val assistantMessage = ChatMessage(
                                    role = "assistant",
                                    content = response.message,
                                    timestamp = response.timestamp
                                )
                                messages = messages + assistantMessage
                            } catch (e: Exception) {
                                messages = messages + ChatMessage(
                                    role = "assistant",
                                    content = context.getString(R.string.scout_oops),
                                    timestamp = System.currentTimeMillis()
                                )
                            } finally {
                                isTyping = false
                            }
                        }
                    }
                },
                isTyping = isTyping
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
            ) {
                // Scout avatar
                Surface(
                    shape = CircleShape,
                    color = PathyColors.Primary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Column {
                    Text(
                        text = "Scout",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PathyColors.Success,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Text(
                            text = stringResource(R.string.scout_online),
                            fontSize = 12.sp,
                            color = pathyTopBarSubtitleColor()
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            IconButton(onClick = { /* Clear chat */ }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.scout_clear_chat)
                )
            }
        },
        colors = pathyTopBarColors()
    )
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    
    val scale = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale.value),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Scout avatar
            Surface(
                shape = CircleShape,
                color = PathyColors.Primary.copy(alpha = 0.2f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Explore,
                        contentDescription = null,
                        tint = PathyColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(PathyDimensions.SpaceS))
        }
        
        // Message bubble
        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) PathyDimensions.RadiusL else 4.dp,
                    topEnd = if (isUser) 4.dp else PathyDimensions.RadiusL,
                    bottomStart = PathyDimensions.RadiusL,
                    bottomEnd = PathyDimensions.RadiusL
                ),
                color = if (isUser) PathyColors.Primary else MaterialTheme.colorScheme.surface,
                shadowElevation = PathyDimensions.ElevationXS
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(PathyDimensions.SpaceM),
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            
            // Timestamp
            Text(
                text = formatTimestamp(LocalContext.current, message.timestamp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = PathyDimensions.SpaceS,
                    vertical = 4.dp
                )
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
    ) {
        // Scout avatar
        Surface(
            shape = CircleShape,
            color = PathyColors.Primary.copy(alpha = 0.2f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Explore,
                    contentDescription = null,
                    tint = PathyColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Surface(
            shape = RoundedCornerShape(PathyDimensions.RadiusL),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = PathyDimensions.ElevationXS
        ) {
            Row(
                modifier = Modifier.padding(PathyDimensions.SpaceM),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { index ->
                    val infiniteTransition = rememberInfiniteTransition(label = "typing")
                    val offset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -10f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = index * 200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_$index"
                    )
                    
                    Surface(
                        shape = CircleShape,
                        color = PathyColors.Primary,
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = offset.dp)
                    ) {}
                }
            }
        }
    }
}

@Composable
fun QuickRepliesSection(onQuickReply: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(PathyDimensions.SpaceM)
    ) {
        Text(
            text = stringResource(R.string.scout_quick_questions),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = PathyDimensions.SpaceS)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
        ) {
            items(ScoutQuickReplies.QUICK_QUESTIONS.take(6)) { questionRes ->
                val question = stringResource(questionRes)
                QuickReplyChip(
                    text = question,
                    onClick = { onQuickReply(question) }
                )
            }
        }
    }
}

@Composable
fun QuickReplyChip(text: String, onClick: () -> Unit) {
    val scale = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy
            )
        )
    }
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PathyDimensions.RadiusFull),
        color = PathyColors.PrimaryContainer,
        modifier = Modifier.scale(scale.value)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = PathyDimensions.SpaceM,
                vertical = PathyDimensions.SpaceS
            ),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = PathyColors.Primary
        )
    }
}

@Composable
fun MessageInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isTyping: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = PathyDimensions.ElevationS
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PathyDimensions.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
        ) {
            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.scout_input_hint)) },
                shape = RoundedCornerShape(PathyDimensions.RadiusFull),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PathyColors.Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                enabled = !isTyping,
                maxLines = 3
            )
            
            // Send button
            FloatingActionButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp),
                containerColor = if (text.isNotBlank()) PathyColors.Primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (text.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = if (isTyping) Icons.Filled.HourglassEmpty else Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.common_send)
                )
            }
        }
    }
}

/** "Just now" / "5 minutes ago" for the first hour, then the clock time, then date and time. */
private fun formatTimestamp(context: Context, timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val locale = Locale.getDefault()
    fun clock(skeleton: String) =
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .format(Date(timestamp))
    return when {
        diff < 3_600_000L -> RelativeTime.format(context, timestamp)
        diff < 86_400_000L -> clock("jm")
        else -> clock("MMMd jm")
    }
}
