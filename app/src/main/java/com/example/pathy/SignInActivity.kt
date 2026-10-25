package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.ui.screens.SignInScreen
import com.example.pathy.ui.theme.TEMPATHYTheme

class SignInActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                SignInScreen(
                    onSuccessLogin = {
                        startActivity(Intent(this, ProfileActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}