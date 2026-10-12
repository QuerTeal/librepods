package me.kavishdevar.librepods.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.ListItemOrientation
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem

/** Uses the list scope's rows so every entry shares the group's corners and dividers. */
@Composable
internal fun GeneralSettingsSection(
    languageSummary: String,
    registered: Boolean,
    detectionFailed: Boolean,
    navigateToLanguage: () -> Unit,
    navigateToFeatureStatus: () -> Unit,
    manageCompanion: () -> Unit
) {
    StyledList(title = stringResource(R.string.general_settings_header)) {
        StyledListItem(name = stringResource(R.string.language), description = languageSummary,
            onClick = navigateToLanguage)
        StyledListItem(name = stringResource(R.string.feature_status_title),
            description = stringResource(R.string.feature_status_description),
            onClick = navigateToFeatureStatus, orientation = ListItemOrientation.Vertical)
        StyledListItem(name = stringResource(R.string.companion_registration),
            description = stringResource(when {
                !registered -> R.string.companion_registration_description
                detectionFailed -> R.string.companion_detection_failed
                else -> R.string.companion_registration_active
            }), onClick = manageCompanion, orientation = ListItemOrientation.Vertical)
    }
}
