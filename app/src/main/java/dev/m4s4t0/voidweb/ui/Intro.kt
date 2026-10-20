package dev.m4s4t0.voidweb.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidweb.Sounds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Opening animation (~1.4 s): the spark ignites and its arms whip round inside the globe, then the flame-fox
 * races round it from snout to tail, and everything fades into the browser.
 */
@Composable
fun Intro(onDone: () -> Unit) {
    val spin = remember { Animatable(-720f) }
    val reach = remember { Animatable(0.1f) }
    val glow = remember { Animatable(0.4f) }
    val globe = remember { Animatable(0f) }
    val wrap = remember { Animatable(0f) }
    val zoom = remember { Animatable(0.8f) }
    val title = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        Sounds.play(Sounds.Kind.OPEN)
        coroutineScope {
            listOf(
                async { spin.animateTo(0f, tween(1150, easing = CubicBezierEasing(0.08f, 0.75f, 0.12f, 1f))) },
                async { reach.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) },
                async {
                    glow.animateTo(
                        1f,
                        keyframes {
                            durationMillis = 1100
                            0.4f at 0
                            1.7f at 520 using LinearEasing
                            1f at 1100 using FastOutSlowInEasing
                        },
                    )
                },
                async { globe.animateTo(1f, tween(380, delayMillis = 120)) },
                async { wrap.animateTo(1f, tween(620, delayMillis = 360, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f))) },
                async { zoom.animateTo(1f, tween(950, easing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f))) },
                async { title.animateTo(1f, tween(420, delayMillis = 600)) },
            ).awaitAll()
        }
        fade.animateTo(0f, tween(260))
        onDone()
    }
    Box(Modifier.fillMaxSize().alpha(fade.value).background(Void.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WebLogo(Modifier.size(220.dp).scale(zoom.value), spin = spin.value, reach = reach.value, glow = glow.value, wrap = wrap.value, globe = globe.value)
            Spacer(Modifier.height(14.dp))
            Text("Void Web", color = Void.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.alpha(title.value))
        }
    }
}
