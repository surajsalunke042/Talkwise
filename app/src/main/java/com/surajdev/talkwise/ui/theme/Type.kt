package com.surajdev.talkwise.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.surajdev.talkwise.R

// Both fonts ship as single variable files; each weight is an instance on the weight axis.
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, vararg weights: Int) = FontFamily(
    weights.map { weight ->
        Font(
            resId = resId,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

private val Sora = variable(R.font.sora, 300, 400, 600)
private val Inter = variable(R.font.inter, 400, 500, 600)

object Type {
    /** Screen headlines. */
    val Display = TextStyle(fontFamily = Sora, fontWeight = FontWeight(300), fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.02).em)

    /** What the tutor is saying right now. */
    val Utterance = TextStyle(fontFamily = Sora, fontWeight = FontWeight(400), fontSize = 24.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em)

    val Title = TextStyle(fontFamily = Sora, fontWeight = FontWeight(600), fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em)
    val Number = TextStyle(fontFamily = Sora, fontWeight = FontWeight(300), fontSize = 30.sp, lineHeight = 34.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp)
    val BodyStrong = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 24.sp)
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp)

    /** Small capitals-style labels; pass upper-case text. */
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight(500), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em)
}
