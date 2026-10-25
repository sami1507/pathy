package com.example.pathy.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.R
import com.example.pathy.SignInActivity
import com.example.pathy.core.trail.ConditionLevel
import com.example.pathy.core.trail.ConditionStatus
import com.example.pathy.core.trail.ConditionSummary
import com.example.pathy.data.TrailData
import com.example.pathy.data.conditions.TrailConditionRepository
import com.example.pathy.i18n.RelativeTime
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * "Is it open?" for one trail: what the map data says (marks, seasonal, how old), what hikers reported
 * lately, and a way to add a report. Nothing here is a promise that the trail is passable; it says where
 * the information comes from and how old it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailStatusSection(trail: TrailData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { TrailConditionRepository() }
    var summary by remember { mutableStateOf<ConditionSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(trail.id) {
        loading = true
        summary = repository.summary(trail.id)
        loading = false
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.cond_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)

            TrailChips(trail.info)

            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                ConditionLine(summary)
            }

            OutlinedButton(
                onClick = {
                    if (FirebaseAuth.getInstance().currentUser == null) {
                        Toast.makeText(context, context.getString(R.string.cond_sign_in), Toast.LENGTH_SHORT).show()
                        context.startActivity(Intent(context, SignInActivity::class.java))
                    } else {
                        showSheet = true
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.cond_report_button)) }

            Text(
                stringResource(R.string.cond_disclaimer),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            ReportForm(
                onSend = { status, note ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    scope.launch {
                        val ok = uid != null && repository.report(trail.id, uid, status, note)
                        Toast.makeText(
                            context,
                            context.getString(if (ok) R.string.cond_thanks else R.string.cond_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (ok) {
                            showSheet = false
                            summary = repository.summary(trail.id)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ConditionLine(summary: ConditionSummary?) {
    val context = LocalContext.current
    if (summary == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cond_no_reports), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val ago = RelativeTime.format(context, summary.atMs)
    val text = stringResource(
        when (summary.latest) {
            ConditionStatus.OPEN -> R.string.cond_open
            ConditionStatus.MUDDY -> R.string.cond_muddy
            ConditionStatus.BLOCKED -> R.string.cond_blocked
            ConditionStatus.CLOSED -> R.string.cond_closed
            ConditionStatus.NOT_FOUND -> R.string.cond_not_found
        },
        ago
    )
    val (icon, color) = when (summary.level) {
        ConditionLevel.GOOD -> Icons.Filled.CheckCircle to PathyColors.Success
        ConditionLevel.CAUTION -> Icons.Filled.Warning to PathyColors.Warning
        ConditionLevel.PROBLEM -> Icons.Filled.Error to PathyColors.Error
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.size(22.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        if (summary.total > 1) {
            Text(
                stringResource(R.string.cond_agree, summary.agreeing, summary.total),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 30.dp)
            )
        }
    }
}

@Composable
private fun ReportForm(onSend: (ConditionStatus, String) -> Unit) {
    var selected by remember { mutableStateOf<ConditionStatus?>(null) }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val options = listOf(
        ConditionStatus.OPEN to R.string.cond_opt_open,
        ConditionStatus.MUDDY to R.string.cond_opt_muddy,
        ConditionStatus.BLOCKED to R.string.cond_opt_blocked,
        ConditionStatus.CLOSED to R.string.cond_opt_closed,
        ConditionStatus.NOT_FOUND to R.string.cond_opt_missing
    )
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cond_report_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.selectableGroup()) {
            options.forEach { (status, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(selected = selected == status, onClick = { selected = status }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == status, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(label), fontSize = 15.sp)
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 200) note = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.cond_note_hint)) },
            maxLines = 3
        )
        Button(
            onClick = {
                val choice = selected ?: return@Button
                sending = true
                onSend(choice, note)
            },
            enabled = selected != null && !sending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(stringResource(R.string.cond_send)) }
    }
}
