package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.screens.SettingsScreen
import com.example.pathy.ui.screens.SignInForm
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sign-in and settings, drawn in every language, plus the behaviour that matters on the form. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class AccountScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun signIn(email: String = "ahmad@example.com", password: String = "", loading: Boolean = false) = SignInForm(
        email = email, onEmailChange = {},
        password = password, onPasswordChange = {},
        keepSignedIn = true, onKeepSignedInChange = {},
        isLoading = loading,
        onForgotPassword = {}, onSignIn = {}, onGoogleSignIn = {}, onCreateAccount = {}
    )

    @Test fun signInEnglish() = rule.captureTo("signin_en", false) { signIn(password = "hunter22") }

    @Test fun signInDark() = rule.captureTo("signin_en_dark", true) { signIn(password = "hunter22") }

    @Test fun signInLoading() = rule.captureTo("signin_en_loading", false) { signIn(loading = true) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun signInArabic() = rule.captureTo("signin_ar", false) { signIn(email = "ahmad@example.com", password = "hunter22") }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun signInHebrew() = rule.captureTo("signin_he", false) { signIn(email = "ahmad@example.com", password = "hunter22") }

    @Test fun settingsEnglish() = rule.captureTo("settings_en", false) { SettingsScreen(onBack = {}) }

    @Test fun settingsDark() = rule.captureTo("settings_en_dark", true) { SettingsScreen(onBack = {}) }

    @Test fun passwordIsHiddenUntilTheEyeIsPressed() {
        rule.setContent { TEMPATHYTheme { signIn(password = "hunter22") } }
        rule.onNodeWithText("hunter22").assertDoesNotExist()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.password_show)).performClick()
        rule.onNodeWithText("hunter22").assertExists()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.password_hide)).performClick()
        rule.onNodeWithText("hunter22").assertDoesNotExist()
    }

    @Test fun mainActionsAreEasyToTap() {
        var signIns by mutableStateOf(0)
        rule.setContent {
            TEMPATHYTheme {
                SignInForm(
                    email = "", onEmailChange = {}, password = "", onPasswordChange = {},
                    keepSignedIn = false, onKeepSignedInChange = {}, isLoading = false,
                    onForgotPassword = {}, onSignIn = { signIns++ }, onGoogleSignIn = {}, onCreateAccount = {}
                )
            }
        }
        // The hero heading also says "Sign in", so pick the node that can be clicked.
        val button = rule.onNode(hasText(rule.activity.getString(R.string.signin_title)) and hasClickAction())
        button.assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals(1, signIns) }
    }
}
