package com.example.pathy

import com.example.pathy.data.reportCategoryLabel
import androidx.compose.ui.res.stringResource
import com.example.pathy.ui.theme.PathyColors

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pathy.data.HazardOptions
import com.example.pathy.data.PointOfInterestOptions
import com.example.pathy.data.Report

@Composable
fun ReportDialogsAndSheets(
    context: Context,
    location: android.location.Location?,
    showReportSheet: Boolean,
    openHazardReportDialog: Boolean,
    openPointOfInterestDialog: Boolean,
    onDismissReportSheet: () -> Unit,
    onDismissHazard: () -> Unit,
    onDismissPoi: () -> Unit,
    onOpenHazardDialog: () -> Unit,
    onOpenPoiDialog: () -> Unit,
    onOptionSelected: (Report) -> Unit
) {
    if (showReportSheet) {
        ReportCategorySheet(
            onDismiss = onDismissReportSheet,
            onHazardClick = {
                onDismissReportSheet()
                onOpenHazardDialog()
            },
            onPoiClick = {
                onDismissReportSheet()
                onOpenPoiDialog()
            }
        )
    }

    if (openHazardReportDialog) {
        ReportDialog(
            title = stringResource(R.string.report_hazard_title),
            options = HazardOptions.all,
            location = location,
            onOptionSelected = { report ->
                Toast.makeText(context, context.getString(R.string.report_sent_toast, reportCategoryLabel(context, report.category)), Toast.LENGTH_SHORT).show()
                onDismissHazard()
                onOptionSelected(report)
            },
            onCancel = onDismissHazard
        )
    }

    if (openPointOfInterestDialog) {
        ReportDialog(
            title = stringResource(R.string.poi_title),
            options = PointOfInterestOptions.all,
            location = location,
            onOptionSelected = {
                Toast.makeText(context, context.getString(R.string.report_selected_toast, reportCategoryLabel(context, it.category)), Toast.LENGTH_SHORT).show()
                onDismissPoi()
                onOptionSelected(it)
            },
            onCancel = onDismissPoi
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCategorySheet(onDismiss: () -> Unit, onHazardClick: () -> Unit, onPoiClick: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = PathyColors.AppBar,
        tonalElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PathyColors.Error)
                    .clickable { onHazardClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = stringResource(R.string.report_hazard_title),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(stringResource(R.string.report_hazard_short), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PathyColors.Secondary)
                    .clickable { onPoiClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = stringResource(R.string.poi_report_desc),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(stringResource(R.string.poi_short), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
