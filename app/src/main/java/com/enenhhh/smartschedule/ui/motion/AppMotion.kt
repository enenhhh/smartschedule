package com.enenhhh.smartschedule.ui.motion

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.enenhhh.smartschedule.ui.theme.ThemeState

/** One motion policy for gestures, navigation and continuous decoration. */
@Composable
fun motionEnabled(): Boolean = !ThemeState.reduceMotion && ValueAnimator.areAnimatorsEnabled()

/** Expressive press / release: transform only, so neighbouring rows never jump. */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val enabled = motionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.978f else 1f,
        animationSpec = if (enabled) spring(dampingRatio = 0.72f, stiffness = 550f) else snap(),
        label = "press-release"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
