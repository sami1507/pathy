package com.example.pathy.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.pathy.R

/**
 * The gender field is stored as a language-independent code. Older sign-ups saved the Hebrew word
 * instead, so [normalize] accepts those too. Only the label shown to the user is translated.
 */
object Gender {
    const val MALE = "male"
    const val FEMALE = "female"

    fun normalize(raw: String?): String = when (raw?.trim()?.lowercase()) {
        FEMALE, "נקבה", "אישה", "أنثى" -> FEMALE
        else -> MALE
    }
}

@Composable
fun genderLabel(raw: String?): String = stringResource(
    if (Gender.normalize(raw) == Gender.FEMALE) R.string.gender_female else R.string.gender_male
)
