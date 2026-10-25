package com.example.pathy

import com.example.pathy.data.HazardOptions
import androidx.compose.ui.res.stringResource
import com.example.pathy.ui.theme.PathyColors

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.example.pathy.data.Report
import com.example.pathy.data.ReportOptions
import com.example.pathy.data.ReportType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.delay

@Composable
fun ReportDialog(
    title: String,
    options: List<ReportOptions>,
    location: android.location.Location?,
    onOptionSelected: (Report) -> Unit,
    onCancel: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var selectedOption by remember { mutableStateOf<ReportOptions?>(null) }
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(color = 0xFF2E3B2E),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                ReportOptionsGrid(
                    options = options,
                    selectedOption = selectedOption,
                    onOptionSelected = { selectedOption = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.report_description_hint), color = Color.White) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.common_close), color = Color.White)
                    }
                    Button(onClick = {
                        if (selectedOption == null) {
                            Toast.makeText(context, context.getString(R.string.report_select_category), Toast.LENGTH_SHORT).show()
                        } else if (location == null) {
                            Toast.makeText(context, context.getString(R.string.report_location_unavailable), Toast.LENGTH_SHORT).show()
                        } else {
                            val option = selectedOption ?: return@Button
                            val report = Report(
                                type = if (HazardOptions.all.any { it.name == option.name }) ReportType.HAZARD else ReportType.POINT_OF_INTEREST,
                                category = option.name,
                                description = description,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                userId = user?.uid
                            )
                            // Stored and rewarded by the submitReport Cloud Function (points are server-side)
                            FirebaseFunctions.getInstance("us-central1")
                                .getHttpsCallable("submitReport")
                                .call(
                                    mapOf(
                                        "type" to report.type.name,
                                        "category" to report.category,
                                        "description" to (report.description ?: ""),
                                        "latitude" to report.latitude,
                                        "longitude" to report.longitude
                                    )
                                )
                                .addOnSuccessListener {
                                    onOptionSelected(report)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, context.getString(R.string.report_save_failed), Toast.LENGTH_SHORT).show()
                                }
                        }
                    }) {
                        Text(stringResource(R.string.common_ok), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportOptionsGrid(
    options: List<ReportOptions>,
    selectedOption: ReportOptions?,
    onOptionSelected: (ReportOptions) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(options) { option ->
            val isSelected = option == selectedOption
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(option.backgroundColor)
                    .then(
                        if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp))
                        else Modifier
                    )
                    .clickable { onOptionSelected(option) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(option.icon, contentDescription = stringResource(option.labelRes), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(option.labelRes),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ReportPointsAnimation(
    visible: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    var fadeOut by remember { mutableStateOf(false) }

    val animatedOffset by animateDpAsState(
        targetValue = if (startAnimation) (-200).dp else 0.dp,
        animationSpec = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
        label = "offsetY"
    )

    if (visible) {
        LaunchedEffect(Unit) {
            startAnimation = true
            delay(3000)
            fadeOut = true
            delay(1500)
            onDone()
        }

        val alpha by animateFloatAsState(
            targetValue = if (fadeOut) 0f else 1f,
            animationSpec = tween(durationMillis = 1500),
            label = "alpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 24.dp, bottom = 40.dp)
                .zIndex(10f)
        ) {
            Card(
                modifier = modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = animatedOffset)
                    .graphicsLayer { this.alpha = alpha }
                    .shadow(12.dp, RoundedCornerShape(40.dp)),
                shape = RoundedCornerShape(40.dp),
                colors = CardDefaults.cardColors(containerColor = PathyColors.Title)
            ) {
                Text(
                    text = stringResource(R.string.report_points_awarded),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
