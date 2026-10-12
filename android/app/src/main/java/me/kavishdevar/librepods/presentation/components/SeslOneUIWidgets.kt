// SPDX-License-Identifier: GPL-3.0-or-later
package me.kavishdevar.librepods.presentation.components

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.presentation.MaterialIcons
import me.kavishdevar.librepods.presentation.theme.OneUIText

/** The surrounding setting row owns the single accessible selection and click target. */
@Composable
internal fun SeslChoiceIndicator(checked: Boolean, radio: Boolean, modifier: Modifier = Modifier) {
    val context = rememberSeslContext()
    key(context, radio) {
        AndroidView(modifier = modifier.size(24.dp, 48.dp), factory = {
            (if (radio) AppCompatRadioButton(context) else AppCompatCheckBox(context)).apply {
                text = ""
                setPadding(0, 0, 0, 0)
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }, update = { it.isChecked = checked })
    }
}

/** Native SESL button draws and handles touch; Compose content preserves arbitrary caller labels. */
@Composable
internal fun SeslButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, container: Color, foreground: Color, content: @Composable RowScope.() -> Unit) {
    val context = rememberSeslContext()
    val callback by rememberUpdatedState(onClick)
    Box(modifier.heightIn(min = 52.dp).alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        key(context) {
            AndroidView(modifier = Modifier.matchParentSize(), factory = {
                AppCompatButton(context).apply {
                    text = ""
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    setOnClickListener { callback() }
                }
            }, update = {
                it.isEnabled = enabled
                ViewCompat.setBackgroundTintList(it, ColorStateList.valueOf(container.toArgb()))
            })
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            CompositionLocalProvider(LocalContentColor provides foreground, LocalTextStyle provides OneUIText.button) { content() }
        }
    }
}

private class SeslStateEditText(context: Context) : AppCompatEditText(context) {
    var syncing = false
    var onUserText: (String, Int, Int) -> Unit = { _, _, _ -> }
    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(text: Editable?) {
                if (!syncing) onUserText(text?.toString().orEmpty(), selectionStart, selectionEnd)
            }
        })
    }
}

/** Synchronizes only changed text, preserving the native IME composition and selection while typing. */
@Composable
internal fun SeslTextField(state: TextFieldState, modifier: Modifier, placeholder: String, singleLine: Boolean, focusRequester: FocusRequester?, label: String?) {
    val context = rememberSeslContext()
    val currentState by rememberUpdatedState(state)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Column(modifier) {
        label?.let { Text(it, style = OneUIText.rowSummary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            key(context) {
                AndroidView(modifier = Modifier.weight(1f).heightIn(min = if (singleLine) 56.dp else 120.dp)
                    .then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester)), factory = {
                    SeslStateEditText(context).apply {
                        setOnFocusChangeListener { view, focused ->
                            if (focused) view.post {
                                if (view.hasFocus()) (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                    .showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                        setTextSize(18f)
                        setSingleLine(singleLine)
                        if (!singleLine) minLines = 3
                        imeOptions = if (singleLine) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NONE
                        onUserText = { text, start, end ->
                            currentState.edit {
                                replace(0, length, text)
                                selection = TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
                            }
                        }
                    }
                }, update = { editor ->
                    editor.setTextColor(textColor)
                    editor.setHintTextColor(hintColor)
                    editor.hint = placeholder
                    editor.contentDescription = label
                    if (editor.text.toString() != state.text.toString()) {
                        editor.syncing = true
                        editor.setText(state.text.toString())
                        editor.setSelection(state.selection.start.coerceIn(0, state.text.length), state.selection.end.coerceIn(0, state.text.length))
                        editor.syncing = false
                    }
                })
            }
            if (singleLine && state.text.isNotEmpty()) IconButton(onClick = { state.clearText() }, Modifier.size(48.dp)) {
                Icon(MaterialIcons.cancel, androidx.compose.ui.res.stringResource(me.kavishdevar.librepods.R.string.clear), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** SESL owns the modal window, native buttons, dismissal, and keyboard handling. */
@Composable
internal fun SeslDialog(onDismissRequest: () -> Unit, title: String?, message: String?, buttons: List<OneUIDialogButton>, content: (@Composable ColumnScope.() -> Unit)?) {
    val context = rememberSeslContext()
    val composition = rememberCompositionContext()
    val lifecycle = LocalLifecycleOwner.current
    val savedState = LocalView.current.findViewTreeSavedStateRegistryOwner()
    val dismiss by rememberUpdatedState(onDismissRequest)
    val currentButtons by rememberUpdatedState(buttons)
    val currentContent by rememberUpdatedState(content)
    val contentColor by rememberUpdatedState(MaterialTheme.colorScheme.onSurface)
    val composeView = remember(context) {
        ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(savedState)
            setParentCompositionContext(composition)
            setContent { CompositionLocalProvider(LocalContentColor provides contentColor) { Column { currentContent?.invoke(this) } } }
        }
    }
    val slots = listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL)
    val dialog = remember(context) {
        AlertDialog.Builder(context).apply {
            if (title != null) setTitle(title)
            if (message != null) setMessage(message)
            if (content != null) setView(composeView)
            buttons.forEachIndexed { index, button ->
                when (slots[index]) {
                    AlertDialog.BUTTON_NEGATIVE -> setNegativeButton(button.text, null)
                    AlertDialog.BUTTON_POSITIVE -> setPositiveButton(button.text, null)
                    else -> setNeutralButton(button.text, null)
                }
            }
        }.create()
    }
    DisposableEffect(dialog) {
        dialog.setOnDismissListener { dismiss() }
        dialog.show()
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        slots.forEachIndexed { index, slot -> dialog.getButton(slot)?.setOnClickListener { currentButtons.getOrNull(index)?.takeIf { it.enabled }?.onClick?.invoke() } }
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            composeView.disposeComposition()
        }
    }
    SideEffect {
        dialog.setTitle(title)
        if (message != null) dialog.setMessage(message)
        buttons.forEachIndexed { index, button -> dialog.getButton(slots[index])?.apply {
            text = button.text
            isEnabled = button.enabled
            if (button.color != Color.Unspecified) setTextColor(button.color.toArgb())
        } }
    }
}

/** The SESL material implementation owns sheet dragging, the modal window, and Back. */
@Composable
internal fun OneUIBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val context = rememberSeslContext(R.style.Theme_LibrePods_OneUI9_BottomSheet)
    val composition = rememberCompositionContext()
    val lifecycle = LocalLifecycleOwner.current
    val savedState = LocalView.current.findViewTreeSavedStateRegistryOwner()
    val dismiss by rememberUpdatedState(onDismiss)
    val currentContent by rememberUpdatedState(content)
    val contentColor by rememberUpdatedState(MaterialTheme.colorScheme.onSurface)
    val sheetColor by rememberUpdatedState(MaterialTheme.colorScheme.surfaceContainerHighest)
    val view = remember(context) {
        ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(savedState)
            setParentCompositionContext(composition)
            setContent { CompositionLocalProvider(LocalContentColor provides contentColor) { currentContent() } }
        }
    }
    val dialog = remember(context) { BottomSheetDialog(context, R.style.Theme_LibrePods_OneUI9_BottomSheetDialog).apply { setContentView(view) } }
    DisposableEffect(dialog) {
        dialog.setOnDismissListener { dismiss() }
        dialog.show()
        dialog.behavior.skipCollapsed = true
        dialog.behavior.setShouldRemoveExpandedCorners(false)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            view.disposeComposition()
        }
    }
    SideEffect {
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            ViewCompat.setBackgroundTintList(it, ColorStateList.valueOf(sheetColor.toArgb()))
        }
    }
}
