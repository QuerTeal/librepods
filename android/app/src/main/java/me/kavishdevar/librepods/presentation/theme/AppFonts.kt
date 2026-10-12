/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.presentation.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import me.kavishdevar.librepods.R
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily

/**
 * SF Pro has no Hangul (or other CJK) glyphs, so on its own Korean text drops to whatever the
 * system default font is and looks mismatched next to SF Pro. This builds a single typeface where
 * SF Pro supplies Latin text and SF Symbols, and Pretendard supplies Hangul, glyph by glyph.
 *
 * SF Pro only ships a Regular weight (heavier weights are synthesized, as before), while
 * Pretendard is a variable font, so Hangul gets real weights.
 */
object AppFonts {
    private val PRETENDARD_WEIGHTS = (100..900 step 100).toList()

    @Volatile
    private var base: Typeface? = null

    fun base(context: Context): Typeface = base ?: synchronized(this) {
        base ?: build(context.applicationContext).also { base = it }
    }

    fun typeface(context: Context, weight: Int = 400, italic: Boolean = false): Typeface =
        Typeface.create(base(context), weight, italic)

    /**
     * Pretendard alone at a real [weight], for bold headings that mix Latin and Hangul: SF Pro's
     * synthesized bold comes out lighter than Pretendard's Hangul beside it.
     */
    fun pretendard(context: Context, weight: Int): Typeface {
        val font = PlatformFont.Builder(context.resources, R.font.pretendard_variable)
            .setWeight(weight)
            .setFontVariationSettings("'wght' $weight")
            .build()
        return Typeface.CustomFallbackBuilder(PlatformFontFamily.Builder(font).build())
            .setSystemFallback("sans-serif")
            .build()
    }

    private fun build(context: Context): Typeface {
        val res = context.resources
        val sfPro = PlatformFontFamily.Builder(PlatformFont.Builder(res, R.font.sf_pro).build()).build()

        val pretendardFonts = PRETENDARD_WEIGHTS.map { weight ->
            PlatformFont.Builder(res, R.font.pretendard_variable)
                .setWeight(weight)
                .setFontVariationSettings("'wght' $weight")
                .build()
        }
        val pretendard = PlatformFontFamily.Builder(pretendardFonts.first())
            .apply { pretendardFonts.drop(1).forEach { addFont(it) } }
            .build()

        return Typeface.CustomFallbackBuilder(sfPro)
            .addCustomFallback(pretendard)
            .setSystemFallback("sans-serif")
            .build()
    }
}

private object AppFontLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        AppFonts.typeface(context, font.weight.weight, font.style == FontStyle.Italic)

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

private class AppFont(
    override val weight: FontWeight,
    override val style: FontStyle
) : AndroidFont(FontLoadingStrategy.Blocking, AppFontLoader, FontVariation.Settings()) {
    override fun toString() = "AppFont(weight=$weight, style=$style)"
}

/**
 * SF Pro with a Pretendard fallback for Hangul. One entry per weight so Compose passes the
 * requested weight through to the platform instead of synthesizing it.
 */
val sfProFamily = FontFamily(
    (100..900 step 100).flatMap { weight ->
        listOf(FontStyle.Normal, FontStyle.Italic).map { style -> AppFont(FontWeight(weight), style) }
    }
)
