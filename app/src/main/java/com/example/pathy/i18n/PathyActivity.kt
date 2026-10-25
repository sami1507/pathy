package com.example.pathy.i18n

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * Base class for every PATHY activity. It applies the user's language in `attachBaseContext`, which
 * is what makes the language setting work on a plain ComponentActivity before Android 13.
 */
abstract class PathyActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }
}
