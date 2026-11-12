package cz.hqsnet.wifinavi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Barva podle sily signalu: cervena (-95 dBm) ... zelena (-45 dBm). */
fun rssiColor(rssi: Float): Color {
    val t = ((rssi + 95f) / 50f).coerceIn(0f, 1f)
    return Color.hsv(t * 120f, 0.85f, 0.85f)
}

fun rssiColor(rssi: Int): Color = rssiColor(rssi.toFloat())

fun rssiLabel(rssi: Int): String = when {
    rssi >= -55 -> "Výborný"
    rssi >= -67 -> "Dobrý (video/VoIP OK)"
    rssi >= -75 -> "Slabší"
    rssi >= -85 -> "Špatný"
    else -> "Nepoužitelný"
}

fun fmt1(v: Double): String = String.format("%.1f", v)

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Jednoduchy carovy graf; null = vypadek (cervena znacka). */
@Composable
fun Spark(
    values: List<Float?>,
    minV: Float,
    maxV: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val dx = size.width / (values.size - 1)
        val path = Path()
        var open = false
        values.forEachIndexed { i, v ->
            if (v == null) {
                open = false
                drawLine(
                    Color.Red.copy(alpha = 0.7f),
                    Offset(i * dx, size.height - 6f),
                    Offset(i * dx, size.height),
                    strokeWidth = 3f,
                )
            } else {
                val y = size.height * (1f - ((v - minV) / (maxV - minV)).coerceIn(0f, 1f))
                if (!open) {
                    path.moveTo(i * dx, y)
                    open = true
                } else {
                    path.lineTo(i * dx, y)
                }
            }
        }
        drawPath(path, color, style = Stroke(width = 3f))
    }
}
