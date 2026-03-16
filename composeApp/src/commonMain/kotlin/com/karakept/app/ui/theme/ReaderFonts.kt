package com.karakept.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.karakept.app.data.model.ReaderFontFamily
import karakept.composeapp.generated.resources.Res
import karakept.composeapp.generated.resources.jetbrainsmono_bold
import karakept.composeapp.generated.resources.jetbrainsmono_regular
import karakept.composeapp.generated.resources.lora_bold
import karakept.composeapp.generated.resources.lora_bolditalic
import karakept.composeapp.generated.resources.lora_italic
import karakept.composeapp.generated.resources.lora_regular
import karakept.composeapp.generated.resources.merriweather_bold
import karakept.composeapp.generated.resources.merriweather_bolditalic
import karakept.composeapp.generated.resources.merriweather_italic
import karakept.composeapp.generated.resources.merriweather_regular
import karakept.composeapp.generated.resources.notosans_bold
import karakept.composeapp.generated.resources.notosans_bolditalic
import karakept.composeapp.generated.resources.notosans_italic
import karakept.composeapp.generated.resources.notosans_regular
import karakept.composeapp.generated.resources.opendyslexic_bold
import karakept.composeapp.generated.resources.opendyslexic_bolditalic
import karakept.composeapp.generated.resources.opendyslexic_italic
import karakept.composeapp.generated.resources.opendyslexic_regular
import org.jetbrains.compose.resources.Font

/**
 * Resolves a [ReaderFontFamily] to a Compose [FontFamily] using bundled font resources.
 * Must be called from a @Composable scope because [Font] from Compose Resources is @Composable.
 */
@Composable
fun ReaderFontFamily.rememberFontFamily(): FontFamily {
    return when (this) {
        ReaderFontFamily.SYSTEM -> FontFamily.Default
        ReaderFontFamily.MERRIWEATHER -> FontFamily(
            Font(Res.font.merriweather_regular, FontWeight.Normal),
            Font(Res.font.merriweather_bold, FontWeight.Bold),
            Font(Res.font.merriweather_italic, FontWeight.Normal, FontStyle.Italic),
            Font(Res.font.merriweather_bolditalic, FontWeight.Bold, FontStyle.Italic),
        )
        ReaderFontFamily.LORA -> FontFamily(
            Font(Res.font.lora_regular, FontWeight.Normal),
            Font(Res.font.lora_bold, FontWeight.Bold),
            Font(Res.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
            Font(Res.font.lora_bolditalic, FontWeight.Bold, FontStyle.Italic),
        )
        ReaderFontFamily.NOTO_SANS -> FontFamily(
            Font(Res.font.notosans_regular, FontWeight.Normal),
            Font(Res.font.notosans_bold, FontWeight.Bold),
            Font(Res.font.notosans_italic, FontWeight.Normal, FontStyle.Italic),
            Font(Res.font.notosans_bolditalic, FontWeight.Bold, FontStyle.Italic),
        )
        ReaderFontFamily.JETBRAINS_MONO -> FontFamily(
            Font(Res.font.jetbrainsmono_regular, FontWeight.Normal),
            Font(Res.font.jetbrainsmono_bold, FontWeight.Bold),
        )
        ReaderFontFamily.OPEN_DYSLEXIC -> FontFamily(
            Font(Res.font.opendyslexic_regular, FontWeight.Normal),
            Font(Res.font.opendyslexic_bold, FontWeight.Bold),
            Font(Res.font.opendyslexic_italic, FontWeight.Normal, FontStyle.Italic),
            Font(Res.font.opendyslexic_bolditalic, FontWeight.Bold, FontStyle.Italic),
        )
    }
}
