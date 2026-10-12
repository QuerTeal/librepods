package me.kavishdevar.librepods.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Keeps choice labels readable when users enlarge text or use a narrow window. */
@Composable
internal fun OneUIChoiceGrid(
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    if (count == 0) return
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fittingColumns = (maxWidth / (88.dp * scale)).toInt().coerceIn(1, count)
        val columns = if (count == 4 && fittingColumns == 3) 2 else fittingColumns
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until count).toList().chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth()) {
                    indices.forEach { index -> content(index, Modifier.weight(1f)) }
                    repeat(columns - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
