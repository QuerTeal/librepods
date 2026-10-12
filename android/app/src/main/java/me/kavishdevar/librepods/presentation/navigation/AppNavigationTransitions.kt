package me.kavishdevar.librepods.presentation.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** The destination covers the departing page; on return the departing page stays in front. */
internal fun appPushTransition(apple: Boolean): ContentTransform = if (apple) {
    slideInHorizontally(tween(350, easing = AppleNavigationEasing)) { it } togetherWith
        slideOutHorizontally(tween(350, easing = AppleNavigationEasing)) { -it / 4 }
} else {
    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 }
}

internal fun appPopTransition(apple: Boolean): ContentTransform = if (apple) {
    (slideInHorizontally(tween(350, easing = AppleNavigationEasing)) { -it / 4 } togetherWith
        slideOutHorizontally(tween(350, easing = AppleNavigationEasing)) { it })
        .apply { targetContentZIndex = -1f }
} else {
    slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
}

private val AppleNavigationEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
