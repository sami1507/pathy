package com.example.pathy.ui.screens

import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pathy.LoginPreferences
import com.example.pathy.R
import com.example.pathy.data.AuthViewModel
import com.example.pathy.SignUpActivity
import com.example.pathy.data.ProfileRepository.loadAndSaveProfileFromFirestore
import com.example.pathy.data.resolveEmailFromInput
import com.example.pathy.ui.design.MountainHero
import com.example.pathy.ui.theme.PathyColors
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
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
            // The button spun forever after a failed or cancelled attempt. Status 10 means this build's
            // signing certificate is not registered in Firebase, so the code is worth keeping in the log.
            isLoading = false
            Log.w("SignIn", "Google sign-in ended with status ${e.statusCode}")
            if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                showToast(context.getString(R.string.auth_google_failed))
            }
        }
    }

    SignInForm(
        email = email,
        onEmailChange = { email = it },
        password = password,
        onPasswordChange = { password = it },
        keepSignedIn = keepSignedIn,
        onKeepSignedInChange = { keepSignedIn = it },
        isLoading = isLoading,
        onForgotPassword = {
            authViewModel.forgotPassword(
                email = email,
                onSuccess = { showToast(context.getString(R.string.reset_email_sent)) },
                onError = { showToast(it) }
            )
        },
        onSignIn = {
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
        onGoogleSignIn = {
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
        onCreateAccount = {
            context.startActivity(Intent(context, SignUpActivity::class.java))
        }
    )
}

/** The sign-in screen without any account logic, so it can be drawn and tested on its own. */
@Composable
fun SignInForm(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    keepSignedIn: Boolean,
    onKeepSignedInChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onForgotPassword: () -> Unit,
    onSignIn: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onCreateAccount: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    val fieldShape = RoundedCornerShape(14.dp)
    val buttonShape = RoundedCornerShape(16.dp)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            MountainHero(
                title = stringResource(R.string.signin_title),
                eyebrow = stringResource(R.string.app_name),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_email)) },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    shape = fieldShape
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_password)) },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(if (showPassword) R.string.password_hide else R.string.password_show)
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSignIn() }),
                    shape = fieldShape
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(
                            checked = keepSignedIn,
                            onCheckedChange = onKeepSignedInChange,
                            colors = CheckboxDefaults.colors(checkedColor = PathyColors.Primary)
                        )
                        Text(stringResource(R.string.signin_remember_me), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = onForgotPassword) {
                        Text(stringResource(R.string.signin_forgot_password), color = MaterialTheme.colorScheme.primary)
                    }
                }

                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = buttonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary)
                ) {
                    Text(stringResource(R.string.signin_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = onGoogleSignIn,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = buttonShape
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = stringResource(R.string.google_logo_desc),
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.signin_with_google), style = MaterialTheme.typography.titleMedium)
                }

                TextButton(
                    onClick = onCreateAccount,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        stringResource(R.string.signin_new_user),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
