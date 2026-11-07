package com.example.pathy

import com.example.pathy.ui.theme.setPathyContent
import com.example.pathy.i18n.PathyActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.screens.SignUpScreen

class SignUpActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPathyContent {
            SignUpScreen(
                onSuccessRegistration = {
                    startActivity(Intent(this, SignInActivity::class.java))
                    finish()
                }
            )
        }
    }
}