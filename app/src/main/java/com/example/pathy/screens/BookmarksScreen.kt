package com.example.pathy.screens

import com.example.pathy.i18n.relativeTimeAgo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List as ListIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.bookmarks.Bookmark
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.design.pathyTopBarSubtitleColor
import com.example.pathy.ui.theme.PathyColors

/**
 * 🔖 BOOKMARKS SCREEN
 * 
 * Beautiful bookmarks/favorites
 * - Grid/List view
 * - Custom lists
 * - Quick actions
 * - Better than AllTrails favorites!
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (String) -> Unit = {},
    onRemoveBookmark: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.bookmarks_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = pluralStringResource(R.plurals.saved_trails_count, bookmarks.size, bookmarks.size),
                            fontSize = 12.sp,
                            color = pathyTopBarSubtitleColor()
                        )
                    }
                },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewMode = if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                    }) {
                        Icon(
                            if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ListIcon else Icons.Filled.GridView,
                            stringResource(R.string.bookmarks_toggle_view)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Create custom list */ },
                containerColor = PathyColors.Primary
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.bookmarks_create_list), tint = Color.White)
            }
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            EmptyBookmarksState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bookmarks) { bookmark ->
                    BookmarkCard(
                        bookmark = bookmark,
                        onClick = { onBookmarkClick(bookmark.trailId) },
                        onRemove = { onRemoveBookmark(bookmark.trailId) }
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Trail icon
                Surface(
                    shape = CircleShape,
                    color = PathyColors.Primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Hiking,
                            contentDescription = null,
                            tint = PathyColors.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Trail info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookmark.trailName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = stringResource(R.string.bookmark_saved_ago, relativeTimeAgo(bookmark.bookmarkedAt)),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Remove button
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.bookmarks_remove),
                    tint = PathyColors.Error
                )
            }
        }
    }
}

@Composable
fun EmptyBookmarksState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.bookmarks_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = stringResource(R.string.bookmarks_empty_body),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private enum class ViewMode {
    GRID, LIST
}
