package com.example.pathy

import android.content.Intent
import android.os.Bundle
import com.example.pathy.i18n.PathyActivity

/**
 * Relaunches the app from its main screen so that every screen is rebuilt in the newly chosen
 * language. The language itself is already stored by [com.example.pathy.i18n.AppLocale.setChoice].
 */
class RestartApp : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}
