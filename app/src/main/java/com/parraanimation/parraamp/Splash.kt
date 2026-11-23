package com.parraanimation.parraamp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

// Pantalla de carga: logo de Fidelidac y marca Parra Software sobre negro.
// La transición hacia la app es un fundido (Crossfade) que se arma en MainActivity.
@Composable
fun SplashScreen() {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = null,
            modifier = Modifier
                .align(BiasAlignment(0f, -0.09f))
                .fillMaxWidth(0.42f)
                .graphicsLayer {
                    alpha = reveal.value
                    val s = 0.94f + 0.06f * reveal.value
                    scaleX = s
                    scaleY = s
                },
            contentScale = ContentScale.FillWidth
        )
        Image(
            painter = painterResource(R.drawable.splash_brand),
            contentDescription = null,
            modifier = Modifier
                .align(BiasAlignment(0f, 0.70f))
                .fillMaxWidth(0.29f)
                .graphicsLayer { alpha = reveal.value * 0.9f },
            contentScale = ContentScale.FillWidth
        )
    }
}
