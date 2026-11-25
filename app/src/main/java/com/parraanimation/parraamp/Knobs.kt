package com.parraanimation.parraamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val BrassLight = Color(0xFFE2C37E)
private val BrassDark = Color(0xFFB48C4A)
private val KnobLogoGold = Color(0xFFD4B14E)

private fun angleAt(p: Offset, w: Int, h: Int): Double {
    val dx = (p.x - w / 2f).toDouble()
    val dy = (h / 2f - p.y).toDouble()
    return Math.toDegrees(atan2(dx, dy))
}

// Rueda de latón con aro estriado. Se gira con el dedo en círculo; doble toque la devuelve a su posición inicial.
@Composable
private fun FxKnob(
    label: String,
    initial: Float,
    min: Float,
    max: Float,
    default: Float,
    bipolar: Boolean,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    var v by remember { mutableFloatStateOf(initial) }
    var touched by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(v) {
        if (touched) {
            showing = true
            delay(900)
            showing = false
        }
    }

    val angle = if (bipolar) v / max * 135f else v / max * 270f
    val perDeg = if (bipolar) max / 135f else max / 270f

    Box(
        Modifier.size(116.dp)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    touched = true
                    v = default
                    onChange(default)
                })
            }
            .pointerInput(Unit) {
                var last = 0.0
                detectDragGestures(
                    onDragStart = { o ->
                        last = angleAt(o, size.width, size.height)
                        touched = true
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val a = angleAt(change.position, size.width, size.height)
                        var d = a - last
                        if (d > 180) d -= 360
                        if (d < -180) d += 360
                        last = a
                        val nv = (v + d.toFloat() * perDeg).coerceIn(min, max)
                        if (nv != v) {
                            v = nv
                            onChange(nv)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) { drawKnob(angle) }
        Text(
            text = if (showing) format(v) else label,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Color.White,
                fontSize = if (showing) 17.sp else 20.sp,
                fontWeight = FontWeight.ExtraBold,
                shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 4f)
            )
        )
    }
}

private fun DrawScope.drawKnob(angleDeg: Float) {
    val c = center
    val r = size.minDimension / 2f
    // sombra inferior: da volumen
    drawCircle(Color(0xFF1B1B1B), radius = r, center = Offset(c.x, c.y + r * 0.05f))
    // base del aro
    drawCircle(Color(0xFF262626), radius = r * 0.97f, center = c)
    // estrías plateadas con luz desde arriba a la izquierda
    val ticks = 96
    for (i in 0 until ticks) {
        val a = i * 2.0 * PI / ticks
        val sx = sin(a).toFloat()
        val cy = -cos(a).toFloat()
        val light = 0.5f + 0.5f * cos(a - 5.5).toFloat()
        val col = lerp(Color(0xFF4A4A4A), Color(0xFFEAEAEA), light)
        drawLine(
            col,
            Offset(c.x + sx * r * 0.84f, c.y + cy * r * 0.84f),
            Offset(c.x + sx * r * 0.97f, c.y + cy * r * 0.97f),
            strokeWidth = 1.5.dp.toPx()
        )
    }
    // hueco oscuro entre el aro y la cara
    drawCircle(Color(0xFF0D0D0D), radius = r * 0.86f, center = c)
    // cara de latón
    val faceR = r * 0.80f
    drawCircle(
        brush = Brush.linearGradient(
            listOf(BrassLight, BrassDark),
            start = Offset(c.x - faceR, c.y - faceR),
            end = Offset(c.x + faceR, c.y + faceR)
        ),
        radius = faceR,
        center = c
    )
    drawCircle(
        Color(0x22FFFFFF), radius = faceR * 0.96f,
        center = Offset(c.x - faceR * 0.08f, c.y - faceR * 0.1f),
        style = Stroke(width = 2.dp.toPx())
    )
    drawCircle(Color(0xFF6E5528), radius = faceR, center = c, style = Stroke(1.dp.toPx()))
    // marca de posición
    rotate(angleDeg, pivot = c) {
        drawLine(
            Color(0xFF141414),
            Offset(c.x, c.y - faceR * 0.97f),
            Offset(c.x, c.y - faceR * 0.24f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun fmtDb(v: Float): String = String.format(Locale.US, "%+.1f dB", v)
private fun fmtPct(v: Float): String = "${(v * 100).roundToInt()}%"

// Las cuatro ruedas con el logo fijo al centro.
// woofer = graves, treble = agudos, reverb = reverberación, atmos = amplificación (ensanchado) estéreo.
@Composable
fun FxPanel(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 45.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FxKnob(
                    label = "woofer", initial = FidelidacFx.bassDb, min = -10f, max = 10f, default = 0f,
                    bipolar = true, format = ::fmtDb, onChange = { FidelidacFx.setBass(it) }
                )
                FxKnob(
                    label = "treble", initial = FidelidacFx.trebleDb, min = -10f, max = 10f, default = 0f,
                    bipolar = true, format = ::fmtDb, onChange = { FidelidacFx.setTreble(it) }
                )
            }
            Spacer(Modifier.height(29.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FxKnob(
                    label = "reverb", initial = FidelidacFx.reverb, min = 0f, max = 1f, default = 0f,
                    bipolar = false, format = ::fmtPct, onChange = { FidelidacFx.setReverb(it) }
                )
                FxKnob(
                    label = "atmos", initial = FidelidacFx.stereo, min = 0f, max = 1f, default = 0f,
                    bipolar = false, format = ::fmtPct, onChange = { FidelidacFx.setStereo(it) }
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.home_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(KnobLogoGold),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.align(Alignment.Center).width(73.dp)
        )
    }
}
