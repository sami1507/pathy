package com.example.pathy.screens

import com.example.pathy.data.Gender
import com.example.pathy.data.genderLabel
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.pathy.R
import com.example.pathy.data.AuthViewModel
import com.example.pathy.data.ProfileRepository.loadAndSaveProfileFromFirestore
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(
    onSuccessRegistration: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var gender by remember { mutableStateOf(Gender.MALE) }
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showVerificationDialog by remember { mutableStateOf(false) }
    val showToast: (String) -> Unit = {
        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    Scaffold(
        topBar = {
            Surface(
                color = PathyColors.AppBar,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.signup_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
                    .clickable { launcher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(selectedImageUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_full_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PathyColors.Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                )
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.field_email)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PathyColors.Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.field_password)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PathyColors.Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                )
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    passwordError = if (it != password) context.getString(R.string.signup_passwords_mismatch) else null
                },
                label = { Text(stringResource(R.string.field_confirm_password)) },
                isError = passwordError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PathyColors.Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                )
            )

            if (passwordError != null) {
                Text(
                    text = passwordError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.field_phone_optional)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PathyColors.Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                )
            )

            Text(stringResource(R.string.field_gender), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

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
                    if (!isValidEmail(email)) {
                        showToast(context.getString(R.string.error_invalid_email))
                        return@Button
                    }
                    if (email.isBlank() || password.isBlank() || name.isBlank()) {
                        showToast(context.getString(R.string.auth_fill_all_fields))
                        return@Button
                    }
                    if (password != confirmPassword) {
                        passwordError = context.getString(R.string.signup_passwords_mismatch)
                        return@Button
                    }
                    isLoading = true
                    FirebaseAuth.getInstance().signOut()
                    authViewModel.signUpWithEmail(
                        email = email,
                        password = password,
                        displayName = name,
                        gender = gender,
                        phone = phone,
                        imageUri = selectedImageUri,
                        createdAt = com.google.firebase.Timestamp.now(),
                        onSuccess = {
                            isLoading = false
                            showVerificationDialog = true
                        },
                        onError = {errorMessage ->
                            isLoading = false
                            when {
                                errorMessage.contains("email address is already in use", ignoreCase = true) -> {
                                    showToast(context.getString(R.string.signup_email_taken))
                                }
                                else -> {
                                    showToast(errorMessage)
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary)
            ) {
                Text(stringResource(R.string.signup_create_account), color = Color.White)
            }

            if (isLoading) {
                CircularProgressIndicator()
            }
        }
    }

    if (showVerificationDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.signup_verification_title)) },
            text = { Text(stringResource(R.string.signup_verification_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        showToast(context.getString(R.string.signup_verify_then_sign_in))
                        showVerificationDialog = false
                        coroutineScope.launch {
                            loadAndSaveProfileFromFirestore(context)
                        }
                        onSuccessRegistration()
                    }
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }
}

@Composable
fun GenderOption(
    gender: String,
    label: String,
    selectedGender: String,
    onSelect: () -> Unit,
    imageRes: Int
) {
    val isSelected = gender == selectedGender
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(if (isSelected) PathyColors.PrimaryLight else MaterialTheme.colorScheme.outlineVariant)
            .clickable { onSelect() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
