package cz.hqsnet.wifinavi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
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

/** Hodnota z textoveho pole (carka i tecka jako desetinny oddelovac). */
fun String.num(): Double? = replace(',', '.').toDoubleOrNull()

@Composable
fun NumField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            onChange(v.filter { ch -> ch.isDigit() || (decimal && (ch == '.' || ch == ',')) }.take(9))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}

/** Rada tlacitek pro vyber jedne moznosti. */
@Composable
fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            if (i == selected) Button(onClick = { onSelect(i) }) { Text(o) }
            else OutlinedButton(onClick = { onSelect(i) }) { Text(o) }
        }
    }
}

@Composable
fun SpeedView(s: SpeedState) {
    if (s.error != null) {
        Text("Chyba: ${s.error}", color = MaterialTheme.colorScheme.error)
        return
    }
    if (s.running || s.done) {
        Text(
            (if (s.done) "Výsledek: " else "Probíhá: ") + fmt1(s.mbps) + " Mbit/s" +
                " · " + fmt1(s.bytes / 1_048_576.0) + " MB za " + fmt1(s.seconds) + " s",
        )
        if (s.done && s.samples.size > 2) {
            Text(
                "Kolísání propustnosti (směr. odchylka vzorků po 0,5 s): ±" +
                    fmt1(NetCalc.stdDev(s.samples)) + " Mbit/s · max " + fmt1(s.samples.max()) + " Mbit/s",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val top = ((s.samples.maxOrNull() ?: 10.0) * 1.2).toFloat().coerceAtLeast(5f)
        Spark(
            s.samples.map { it.toFloat() }, 0f, top, MaterialTheme.colorScheme.primary,
            Modifier.fillMaxWidth().height(70.dp).padding(top = 8.dp),
        )
    }
}
