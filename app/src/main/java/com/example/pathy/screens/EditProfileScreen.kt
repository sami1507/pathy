package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.data.Gender
import com.example.pathy.data.genderLabel
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.data.ProfileDataStore
import com.example.pathy.data.ProfileInfo
import com.example.pathy.data.ProfileRepository
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(
    profile: ProfileInfo,
    onSaveSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProfileDataStore(context) }
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(profile.name) }
    var email by remember { mutableStateOf(profile.email) }
    var phone by remember { mutableStateOf(profile.phone) }
    var gender by remember { mutableStateOf(Gender.normalize(profile.gender)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = PathyColors.AppBar,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.profile_edit_desc),
                        style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.field_email)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.field_phone)) },
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.field_gender_plain), style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GenderOption(
                    gender = Gender.MALE,
                    label = genderLabel(Gender.MALE),
                    selectedGender = gender,
                    onSelect = { gender = Gender.MALE },
                    imageRes = R.drawable.default_pic_empty_male
                )
                GenderOption(
                    gender = Gender.FEMALE,
                    label = genderLabel(Gender.FEMALE),
                    selectedGender = gender,
                    onSelect = { gender = Gender.FEMALE },
                    imageRes = R.drawable.default_pic_empty_female
                )
            }

            Button(
                onClick = {
                    val updatedProfile = profile.copy(
                        name = name,
                        email = email,
                        phone = phone,
                        gender = gender
                    )

                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@Button
                    coroutineScope.launch {
                        store.saveName(name)
                        store.saveEmail(email)
                        store.savePhone(phone)
                        store.saveGender(gender)
                        store.saveImageUri(profile.imageUri)

                        ProfileRepository.UpdateProfileToFirestore(
                            userId = uid,
                            profile = updatedProfile,
                            onSuccess = { onSaveSuccess() },
                            onFailure = { e -> Log.e("SaveProfile", "Error: ${e.message}") }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary)
            ) {
                Text(stringResource(R.string.edit_profile_save), color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
