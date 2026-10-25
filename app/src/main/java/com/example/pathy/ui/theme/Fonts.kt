package com.example.pathy.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.pathy.R

/**
 * PATHY's typeface: Rubik (SIL OFL 1.1), one variable font that carries Latin, Hebrew and Arabic.
 *
 * One family for all three scripts keeps weights, x-height and stroke contrast matched inside a
 * single sentence, so a trail named "Wadi Rum" inside an Arabic or Hebrew line does not switch to a
 * different-looking fallback font. The rounded terminals also echo the rounded cards and chips.
 * The font file lives in res/font/rubik.ttf; its license is in assets/licenses/Rubik-OFL.txt.
 */
@OptIn(ExperimentalTextApi::class)
val PathyFontFamily: FontFamily = FontFamily(
    listOf(
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold
    ).map { weight ->
        Font(
            resId = R.font.rubik,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)
