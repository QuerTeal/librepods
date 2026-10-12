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

package me.kavishdevar.librepods.presentation.screens

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.screenBottomPadding
import me.kavishdevar.librepods.presentation.components.screenTopPadding
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import java.text.Collator
import java.util.Locale

/** Per-app language, backed by the platform's per-app locale setting (Android 13+). */
object AppLanguage {
    // Used when the manifest's generated locale config can't be read.
    private val FALLBACK_TAGS = listOf("en", "de", "es", "fr", "ko", "pt", "tr", "uk", "vi", "zh-CN", "zh-TW")

    fun supportedLocales(context: Context): List<Locale> {
        val fromConfig = runCatching {
            val config = LocaleConfig(context)
            if (config.status == LocaleConfig.STATUS_SUCCESS) config.supportedLocales else null
        }.getOrNull()
        val locales = fromConfig?.let { list -> (0 until list.size()).map { list[it] } }
            ?: FALLBACK_TAGS.map(Locale::forLanguageTag)
        val collator = Collator.getInstance(Locale.ROOT)
        return locales.sortedWith(compareBy(collator) { nativeName(it) })
    }

    /** The app-specific locale, or null when following the system language. */
    fun current(context: Context): Locale? {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0]
    }

    /** The device language, unaffected by the app-specific override. */
    fun system(context: Context): Locale =
        context.getSystemService(LocaleManager::class.java).systemLocales.let { if (it.isEmpty) Locale.getDefault() else it[0] }

    fun set(context: Context, locale: Locale?) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            locale?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()
    }

    fun nativeName(locale: Locale): String =
        locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }

    fun displayName(locale: Locale, inLocale: Locale): String =
        locale.getDisplayName(inLocale).replaceFirstChar { it.titlecase(inLocale) }

    fun summary(context: Context): String =
        current(context)?.let(::nativeName) ?: context.getString(R.string.system_default)

    fun matches(selected: Locale?, candidate: Locale): Boolean =
        selected != null && selected.toLanguageTag() == candidate.toLanguageTag()
}

@Composable
fun LanguageScreen() {
    val context = LocalContext.current
    // Recompose with the new resources once the platform applies the locale change.
    val uiLocale = LocalConfiguration.current.locales[0]
    val systemLocale = AppLanguage.system(context)
    val locales = remember { AppLanguage.supportedLocales(context) }
    var selected by remember { mutableStateOf(AppLanguage.current(context)) }

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = screenTopPadding(material = 0.dp)
    val bottomPadding = screenBottomPadding(material = 16.dp)

    fun choose(locale: Locale?) {
        if (locale?.toLanguageTag() == selected?.toLanguageTag()) return
        selected = locale
        AppLanguage.set(context, locale)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        StyledList {
            StyledListItem(
                name = stringResource(R.string.system_default),
                description = AppLanguage.displayName(systemLocale, uiLocale),
                orientation = ListItemOrientation.Vertical,
                selected = selected == null,
                onClick = { choose(null) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        StyledList(description = stringResource(R.string.language_description)) {
            locales.forEach { locale ->
                val nativeName = AppLanguage.nativeName(locale)
                val localizedName = AppLanguage.displayName(locale, uiLocale)
                StyledListItem(
                    name = nativeName,
                    description = localizedName.takeIf { it != nativeName },
                    orientation = ListItemOrientation.Vertical,
                    selected = AppLanguage.matches(selected, locale),
                    onClick = { choose(locale) }
                )
            }
        }

        Spacer(modifier = Modifier.height(bottomPadding))
    }
}
