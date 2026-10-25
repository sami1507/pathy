package com.example.pathy.ui.screens

import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pathy.LoginPreferences
import com.example.pathy.R
import com.example.pathy.data.AuthViewModel
import com.example.pathy.SignUpActivity
import com.example.pathy.data.ProfileRepository.loadAndSaveProfileFromFirestore
import com.example.pathy.data.resolveEmailFromInput
import com.example.pathy.ui.theme.PathyColors
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SignInScreen(
    onSuccessLogin: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val loginPrefs = remember { LoginPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keepSignedIn by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var navigateToProfile by remember { mutableStateOf(false) }
    val showToast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                authViewModel.firebaseAuthWithGoogle(
                    idToken,
                    onSuccess = {
                        navigateToProfile = true
                        coroutineScope.launch {
                            val auth = FirebaseAuth.getInstance()
                            val user = auth.currentUser
                            if (user != null) {
                                try {
                                    user.reload().await()
                                    user.getIdToken(true).await()
                                    val refreshedUser = auth.currentUser
                                    if (refreshedUser != null && refreshedUser.isEmailVerified) {
                                        authViewModel.ensureUserProfileExists(
                                            user = refreshedUser,
                                            onComplete = {
                                                coroutineScope.launch {
                                                    try {
                                                        loadAndSaveProfileFromFirestore(context)
                                                        loginPrefs.setKeepSignedIn(keepSignedIn)
                                                        showToast(context.getString(R.string.signin_google_success))
                                                        onSuccessLogin()
                                                    } catch (e: Exception) {
                                                        showToast(context.getString(R.string.profile_load_error, e.message))
                                                    }
                                                }
                                            },
                                            onError = {
                                                isLoading = false
                                                showToast(context.getString(R.string.profile_create_error, it.message))
                                            }
                                        )
                                    } else {
                                        auth.signOut()
                                    }
                                } catch (e: Exception) {
                                    auth.signOut()
                                }
                            } else {
                                auth.signOut()
                            }


                        }
                    },
                    onError = { errorMsg ->
                        isLoading = false
                        when {
                            errorMsg.contains("There is no user") || errorMsg.contains("no user record") -> {
                                showToast(context.getString(R.string.signin_no_user))
                            }

                            errorMsg.contains("The password is invalid") || errorMsg.contains("password is incorrect") -> {
                                showToast(context.getString(R.string.signin_wrong_password))
                            }

                            errorMsg.contains("network error", ignoreCase = true) -> {
                                showToast(context.getString(R.string.error_network_retry))
                            }

                            else -> {
                                showToast(context.getString(R.string.error_generic_with_message, errorMsg))
                            }
                        }
                    }
                )

            }
        } catch (e: ApiException) {
            showToast(context.getString(R.string.auth_google_failed))
        }
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
                        text = stringResource(R.string.signin_title),
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

            @Composable
            fun cardField(label: String, content: @Composable () -> Unit) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        content()
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            cardField(stringResource(R.string.field_email)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PathyColors.Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            cardField(stringResource(R.string.field_password)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PathyColors.Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = keepSignedIn,
                        onCheckedChange = { keepSignedIn = it },
                        colors = CheckboxDefaults.colors(checkedColor = PathyColors.Primary)
                    )
                    Text(stringResource(R.string.signin_remember_me))
                }

                TextButton(onClick = {
                    authViewModel.forgotPassword(
                        email = email,
                        onSuccess = { showToast(context.getString(R.string.reset_email_sent)) },
                        onError = { showToast(it) }
                    )
                }) {
                    Text(stringResource(R.string.signin_forgot_password), color = PathyColors.Secondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))


            Button(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true

                        val resolvedEmail = resolveEmailFromInput(email.trim())
                        if (resolvedEmail == null) {
                            isLoading = false
                            showToast(context.getString(R.string.signin_invalid_identifier))
                            return@launch
                        }

                        authViewModel.signInWithEmail(
                            email = resolvedEmail,
                            password = password,
                            onSuccess = {
                                coroutineScope.launch {
                                    isLoading = false
                                    val auth = FirebaseAuth.getInstance()
                                    try {
                                        auth.currentUser?.reload()?.await()
                                        val refreshedUser = auth.currentUser

                                        if (refreshedUser != null && refreshedUser.isEmailVerified) {
                                            authViewModel.ensureUserProfileExists(
                                                user = refreshedUser,
                                                onComplete = {
                                                    coroutineScope.launch {
                                                        try {
                                                            loadAndSaveProfileFromFirestore(context)
                                                            loginPrefs.setKeepSignedIn(keepSignedIn)
                                                            showToast(context.getString(R.string.signin_success))
                                                            onSuccessLogin()
                                                        } catch (e: Exception) {
                                                            showToast(context.getString(R.string.profile_load_error, e.message))
                                                        }
                                                    }
                                                },
                                                onError = {
                                                    showToast(context.getString(R.string.profile_load_error, it.message))
                                                }
                                            )
                                        } else {
                                            auth.signOut()
                                            showToast(context.getString(R.string.signin_email_unverified))
                                        }
                                    } catch (e: Exception) {
                                        showToast(context.getString(R.string.signin_general_error, e.message))
                                        auth.signOut()
                                    }
                                }
                            },
                            onError = {
                                isLoading = false
                                showToast(it)
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary)
            ) {
                Text(stringResource(R.string.signin_title), color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    isLoading = true
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("916819227228-7b0t3tsvcklpilkmp5hjgr0ieu6ktet1.apps.googleusercontent.com")
                        .requestEmail()
                        .build()

                    val googleSignInClient = GoogleSignIn.getClient(context, gso)

                    googleSignInClient.signOut().addOnCompleteListener {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.SecondaryDark)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = stringResource(R.string.google_logo_desc),
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.signin_with_google), color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val intent = Intent(context, SignUpActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Secondary)
            ) {
                Text(stringResource(R.string.signin_new_user), color = Color.White)
            }
        }

        if (isLoading) {
            CoolLoadingOverlay(stringResource(R.string.common_just_a_moment))
        }
    }
}
@Composable
fun CoolLoadingOverlay(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PathyColors.Night.copy(alpha = 0.5f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

