package me.kavishdevar.librepods.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.components.ConfirmationDialog
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.StyledScaffold
import me.kavishdevar.librepods.presentation.components.screenBottomPadding
import me.kavishdevar.librepods.presentation.components.screenTopPadding

/** Only presentation: registration mutations are supplied by the host after an explicit action. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun CompanionManagementContent(onEnable: () -> Unit, onRemove: () -> Unit, onBack: () -> Unit) {
    val confirmation = remember { mutableStateOf(false) }
    val backdrop = rememberLayerBackdrop()
    StyledScaffold(title = stringResource(R.string.companion_registration), showBackButton = true,
        onNavigateBack = onBack) {
        Box(Modifier.fillMaxSize()) {
            // A sibling backdrop avoids recording the glass dialog inside its own source.
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainer).layerBackdrop(backdrop))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(screenTopPadding()))
                StyledList(description = stringResource(R.string.companion_manage_description)) {
                    StyledListItem(name = stringResource(R.string.companion_enable_detection), onClick = onEnable)
                    StyledListItem(name = stringResource(R.string.companion_remove_registration),
                        onClick = { confirmation.value = true })
                }
                Spacer(Modifier.height(screenBottomPadding()))
            }
            ConfirmationDialog(showDialog = confirmation, title = stringResource(R.string.companion_remove_registration),
                message = stringResource(R.string.companion_remove_confirm),
                confirmText = stringResource(R.string.companion_remove_registration),
                dismissText = stringResource(android.R.string.cancel), backdrop = backdrop,
                onConfirm = { confirmation.value = false; onRemove() })
        }
    }
}
