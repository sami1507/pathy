package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.pathy.ProfileActivity
import com.example.pathy.R
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Public attendee info only: contact details are private and never shown to other users. */
data class AttendeeProfile(
    val uid: String,
    val username: String,
    val profileImageUrl: String? = null
)

@Composable
fun AttendeesScreen(eventId: String) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    var attendees by remember { mutableStateOf<List<AttendeeProfile>>(emptyList()) }

    LaunchedEffect(eventId) {
        val doc = db.collection("events").document(eventId).get().await()
        val attendeeIds = doc.get("attendees") as? List<String> ?: emptyList()

        val profiles = attendeeIds.mapNotNull { uid ->
            val userDoc = db.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                AttendeeProfile(
                    uid = uid,
                    username = userDoc.getString("name") ?: context.getString(R.string.unnamed_user),
                    profileImageUrl = userDoc.getString("profileImageUrl")
                )
            } else null
        }
        attendees = profiles
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.attendees_title),
            style = MaterialTheme.typography.headlineSmall,
            color = PathyColors.Primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_profile),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.width(50.dp))

            Text(
                text = stringResource(R.string.field_name),
                modifier = Modifier.weight(2.5f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.field_phone),
                modifier = Modifier.weight(3f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Divider(color = PathyColors.Primary, thickness = 1.dp)

        LazyColumn {
            items(attendees) { attendee ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = attendee.profileImageUrl?.takeIf { it.isNotBlank() },
                                placeholder = painterResource(R.drawable.default_pic_empty_male),
                                error = painterResource(R.drawable.default_pic_empty_male)
                            ),
                            contentDescription = stringResource(R.string.profile_image_desc),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val intent = Intent(context, ProfileActivity::class.java)
                                    intent.putExtra("userId", attendee.uid)
                                    context.startActivity(intent)
                                }
                        )
                    }

                    Spacer(modifier = Modifier.width(50.dp))

                    Text(
                        text = attendee.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(2.5f)
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.8.dp)
            }
        }
    }
}
