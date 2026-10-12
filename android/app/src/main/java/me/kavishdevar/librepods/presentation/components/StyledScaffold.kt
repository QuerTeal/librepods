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

package me.kavishdevar.librepods.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.ui.text.lerp
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.kavishdevar.librepods.R
import android.view.RoundedCorner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import me.kavishdevar.librepods.presentation.theme.AppleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.theme.sfProFamily
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import me.kavishdevar.librepods.presentation.theme.LocalOneUI
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import me.kavishdevar.librepods.presentation.theme.OneUIText
import com.google.android.material.oneui.floatingactioncontainer.FloatingAnimationConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledScaffold(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    title: String,
    showBackButton: Boolean = false,
    onNavigateBack: () -> Unit = {},
    actionButtons: List<@Composable (backdrop: LayerBackdrop) -> Unit> = emptyList(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable () -> Unit
) {
    if (LocalOneUI.current) {
        OneUIScaffold(modifier, visible, title, showBackButton, onNavigateBack, actionButtons, snackbarHostState, content)
        return
    }
    val isDarkTheme = isSystemInDarkTheme()
    val hazeState = rememberHazeState(blurEnabled = true)

    when (LocalDesignSystem.current) {
        DesignSystem.Material -> {
            // Each screen starts with its large title showing.
            val topBarState = remember(title) { TopAppBarState(-Float.MAX_VALUE, 0f, 0f) }
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
            val expandedTitle = MaterialTheme.typography.displaySmall
            val collapsedTitle = MaterialTheme.typography.titleSmall
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                    ) {
                        LargeFlexibleTopAppBar(
                            navigationIcon = {
                                if (showBackButton) {
                                    Row {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        FilledTonalIconButton(
                                            onClick = onNavigateBack,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow)),
                                            shape = IconButtonDefaults.mediumRoundShape
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Default.ArrowBack,
                                                contentDescription = "",
                                                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                                            )
                                        }
                                    }
                                }
                            },
                            title = {
                                AnimatedContent(
                                    targetState = title,
                                    transitionSpec = {
                                        (fadeIn(tween(durationMillis = 200, delayMillis = 80)) togetherWith fadeOut(tween(80)))
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "appBarTitle"
                                ) {
                                    Text(
                                        text = it,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                                        style = lerp(expandedTitle, collapsedTitle, scrollBehavior.state.collapsedFraction)
                                    )
                                }
                            },
                            actions = {
                                actionButtons.forEach { actionButton ->
                                    actionButton(rememberLayerBackdrop())
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            scrollBehavior = scrollBehavior
                        )
                    }
                },
            ) { paddingValues ->
                Box(
                    modifier = modifier
                        .then(if (visible) Modifier.padding(paddingValues) else Modifier)
                        .fillMaxSize()
                        .hazeSource(hazeState)
                ) {
                    content()
                }
            }
        }
        DesignSystem.Apple -> {
            // The page is a card with the display's own corners, as on iOS, so its edges disappear
            // into the screen's until a transition scales it.
            val pageShape = AppleShape(rememberDisplayCornerRadius())
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier
                    .then(
                        if (!isDarkTheme) Modifier.shadow(
                            elevation = 36.dp,
                            shape = pageShape,
                            ambientColor = Color.Black,
                            spotColor = Color.Black
                        ) else Modifier
                    )
                    .clip(pageShape)
            ) { paddingValues ->
                val topPadding = paddingValues.calculateTopPadding()
                val startPadding = paddingValues.calculateLeftPadding(LocalLayoutDirection.current)
                val endPadding = paddingValues.calculateRightPadding(LocalLayoutDirection.current)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = startPadding, end = endPadding)
                ) {
                    val backdrop = rememberLayerBackdrop()
                    val bgColor = MaterialTheme.colorScheme.surfaceContainer
                    AnimatedVisibility(
                        visible = showBackButton,
                        enter = AppleBarButtonEnter,
                        exit = AppleBarButtonExit,
                        modifier = Modifier
                            .zIndex(3f)
                            .padding(top = topPadding, start = 8.dp)
                            .align(Alignment.TopStart)
                    ) {
                        StyledIconButton(
                            onClick = onNavigateBack,
                            icon = "􀯶",
                            contentDescription = stringResource(R.string.back),
                            backdrop = backdrop
                        )
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier
                            .zIndex(2f)
                            .height(84.dp + topPadding)
                            .fillMaxWidth()
                            .layerBackdrop(backdrop)
                    ){
                        Box(
                            modifier = Modifier.hazeEffect(
                                state = hazeState,
                            ) {
                                // iOS's scroll-edge effect: the frosting fades out below the bar
                                // instead of ending on a hard line across the content.
                                mask = Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.72f to Color.Black,
                                    1f to Color.Transparent
                                )
                                backgroundColor = bgColor
                                tints = listOf(
                                    HazeTint(
                                        if (isDarkTheme) Color.Black.copy(0.55f) else Color(
                                            0xFFF2F2F7
                                        ).copy(alpha = 0.85f)
                                    )
                                )
                                blurRadius = 6.dp
                            }
                        ) {

                            Column(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.height(topPadding + 12.dp))
                                // The old title clears before the new one fades in, so the two never read as one.
                                AnimatedContent(
                                    targetState = title,
                                    transitionSpec = {
                                        (fadeIn(tween(durationMillis = 200, delayMillis = 80)) togetherWith fadeOut(tween(80)))
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "barTitle"
                                ) {
                                    Text(
                                        text = it,
                                        style = TextStyle(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDarkTheme) Color.White else Color.Black,
                                            fontFamily = sfProFamily
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 64.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Keyed by screen, so the buttons of the screen being left fade out as themselves
                    // while the next screen's fade in.
                    AnimatedContent(
                        targetState = title to (if (visible) actionButtons else emptyList()),
                        contentKey = { it.first },
                        // No clipping, or the glass buttons' shadows get cut square while they fade.
                        transitionSpec = { (AppleBarButtonEnter togetherWith AppleBarButtonExit).using(SizeTransform(clip = false)) },
                        contentAlignment = Alignment.TopEnd,
                        modifier = Modifier
                            .zIndex(3f)
                            .padding(top = topPadding, end = 8.dp)
                            .align(Alignment.TopEnd),
                        label = "barActions"
                    ) { (_, buttons) ->
                        Row {
                            buttons.forEach { actionButton ->
                                actionButton(backdrop)
                            }
                        }
                    }

                    Box(
                        modifier = modifier
                            .hazeSource(hazeState)
                            .fillMaxSize()
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/** Height of the One UI toolbar row under the status bar. */
internal val OneUIToolbarHeight = 68.dp

/**
 * The space a screen leaves above its content. Apple and One UI draw their toolbars over the
 * content, which scrolls underneath; Material's scaffold already places content below its app bar,
 * so it only gets [material].
 */
@Composable
fun screenTopPadding(material: Dp = 0.dp): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return when {
        LocalOneUI.current -> statusBar + OneUIToolbarHeight
        LocalDesignSystem.current == DesignSystem.Material -> material
        else -> statusBar + 64.dp
    }
}

/** The space a screen leaves below its content; see [screenTopPadding]. */
@Composable
fun screenBottomPadding(material: Dp = 0.dp): Dp {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return when {
        LocalOneUI.current -> navigationBar + 12.dp
        LocalDesignSystem.current == DesignSystem.Material -> material
        else -> navigationBar + 12.dp
    }
}

/**
 * One UI 9 screen frame, matched to a recording of the system Settings app: the toolbar stays put
 * while the content scrolls underneath it. As soon as the content leaves the top, the title fades
 * out and the back chevron (and any action) gains a round floating background in place; a soft
 * page-colored fade covers the status bar. Scrolling back to the top reverses it.
 */
@Composable
private fun OneUIScaffold(
    modifier: Modifier,
    visible: Boolean,
    title: String,
    showBackButton: Boolean,
    onNavigateBack: () -> Unit,
    actionButtons: List<@Composable (backdrop: LayerBackdrop) -> Unit>,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit
) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    // Save alongside the screen's restored ScrollState; only consumed distance changes position.
    // Unconsumed motion can come from a nested control and does not prove the page is at its top.
    var scrollOffset by rememberSaveable(title) { mutableFloatStateOf(0f) }
    var scrolled by rememberSaveable(title) { mutableStateOf(false) }
    val enterThreshold = with(LocalDensity.current) { 1.dp.toPx() }
    val exitThreshold = enterThreshold / 2f
    val scrollConnection = remember(title, enterThreshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                scrollOffset = (scrollOffset - consumed.y).coerceAtLeast(0f)
                if (scrollOffset <= exitThreshold) scrolled = false
                else if (scrollOffset >= enterThreshold) scrolled = true
                return Offset.Zero
            }
        }
    }
    val floatingAnimation = remember { FloatingAnimationConfig() }
    val titleAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0f else 1f,
        animationSpec = tween(if (scrolled) 100 else 150),
        label = "toolbarTitle"
    )
    val floating by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(floatingAnimation.backgroundAlphaAnimationDuration.toInt(), easing = LinearEasing),
        label = "toolbarFloating"
    )
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .nestedScroll(scrollConnection)
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }

        if (visible) {
            // Measured: opaque over the status bar, clear about 14dp below it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBar + 14.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to background,
                            0.2f to background.copy(alpha = 0.9f),
                            0.55f to background.copy(alpha = 0.55f),
                            0.75f to background.copy(alpha = 0.25f),
                            1f to background.copy(alpha = 0f)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(OneUIToolbarHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    OneUIToolbarButton(floating = floating, onClick = onNavigateBack) {
                        Icon(OneUIBackChevron, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    text = title,
                    style = OneUIText.toolbarTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (showBackButton) 2.dp else 12.dp)
                        .graphicsLayer { alpha = titleAlpha }
                )
                actionButtons.forEach { actionButton ->
                    OneUIToolbarButton(floating = floating) { actionButton(rememberLayerBackdrop()) }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

/**
 * A One UI toolbar button. At rest it's a bare icon; while the content is scrolled ([floating]
 * = 1) the SESL projection surface fades in behind it, without moving or scaling the icon.
 */
@Composable
private fun OneUIToolbarButton(
    floating: Float,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        NativeOneUIToolbarSurface(floating, Modifier.matchParentSize())
        // Keep touch clipping separate from the surface, so it cannot crop its native shadow.
        Box(
            modifier = Modifier.matchParentSize().clip(CircleShape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

// iOS 26 bar buttons materialise: a quick fade with a small springy scale, not a pop from nothing.
private val AppleBarButtonEnter =
    fadeIn(tween(durationMillis = 180, delayMillis = 40)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f))
private val AppleBarButtonExit =
    fadeOut(tween(120)) + scaleOut(targetScale = 0.8f, animationSpec = tween(120))

/** The corner radius of the device's display, so a full-screen page can match it (52dp if unknown). */
@Composable
private fun rememberDisplayCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        val radiusPx = context.display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        if (radiusPx > 0) with(density) { radiusPx.toDp() } else 52.dp
    }
}
