package com.ntop.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Official Android blur: Compose `Modifier.blur` renders through
// android.graphics.RenderEffect (API 31+, our minSdk) on the render thread.
// No third-party backdrop library, no CPU bitmap copies.
@Composable
fun Modifier.blurEffect(
    blurRadius: Dp,
    edgeTreatment: BlurredEdgeTreatment = BlurredEdgeTreatment.Unbounded,
): Modifier = this.blur(blurRadius, edgeTreatment)

@Composable
fun BlurSurface(
    blurRadius: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.blurEffect(blurRadius)) {
        content()
    }
}
