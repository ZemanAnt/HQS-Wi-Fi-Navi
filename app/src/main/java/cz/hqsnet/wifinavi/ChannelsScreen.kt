package cz.hqsnet.wifinavi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow

private val palette = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFD81B60), Color(0xFF6D4C41),
)

@Composable
fun ChannelsScreen(vm: WifiVM) {
    val aps by vm.aps.collectAsState()
    val a24 = aps.filter { it.freq < 3000 }
    val a5 = aps.filter { it.freq in 3000..5899 }
    val a6 = aps.filter { it.freq >= 5900 }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { BandChart("2,4 GHz", a24, 2400, 2490) }
        item {
            SectionCard("Orientační doporučení 2,4 GHz") {
                if (a24.isEmpty()) {
                    Text("Žádné sítě v pásmu 2,4 GHz.")
                } else {
                    // Odhad podle překryvu s okolními AP, ne přímé měření šumu.
                    val score = listOf(1, 6, 11).associateWith { channel ->
                        val candidateCenter = (2407 + channel * 5).toDouble()
                        a24.sumOf { ap ->
                            val half = ap.widthMhz / 2.0
                            val overlap = (minOf(candidateCenter + 10, ap.centerFreq + half) -
                                maxOf(candidateCenter - 10, ap.centerFreq - half)).coerceAtLeast(0.0)
                            overlap / 20.0 * 10.0.pow((ap.rssi + 100) / 10.0)
                        }
                    }
                    val best = score.minByOrNull { it.value }?.key
                    Text("Nejméně obsazený z kanálů 1 / 6 / 11: $best")
                    Text("Jde o odhad z viditelných sítí a jejich RSSI, nikoli o měření spektrálního rušení.",
                        style = MaterialTheme.typography.bodySmall)
                    val counts = a24.groupingBy { it.channel }.eachCount().toSortedMap()
                    Text(
                        "Počet sítí na primárním kanálu: " +
                            counts.entries.joinToString(" · ") { "ch${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { BandChart("5 GHz", a5, 5150, 5895) }
        item {
            SectionCard("Obsazení 5 GHz") {
                if (a5.isEmpty()) {
                    Text("Žádné sítě v pásmu 5 GHz.")
                } else {
                    val counts = a5.groupingBy { it.channel }.eachCount().toSortedMap()
                    Text(
                        counts.entries.joinToString(" · ") { "ch${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (a6.isNotEmpty()) {
            item { BandChart("6 GHz", a6, 5925, 7125) }
        }
    }
}

private fun channelLabels(f0: Int): List<Pair<Int, Int>> = when {
    f0 < 3000 -> (1..13).map { it to (2407 + 5 * it) }
    f0 < 5900 -> listOf(36, 44, 52, 60, 100, 108, 116, 124, 132, 140, 149, 157, 165)
        .map { it to (5000 + 5 * it) }
    else -> (1..233 step 16).map { it to (5950 + 5 * it) }
}

@Composable
fun BandChart(title: String, list: List<Ap>, f0: Int, f1: Int) {
    val tm = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = onSurface.copy(alpha = 0.15f)
    val labelStyle = TextStyle(fontSize = 9.sp, color = onSurface.copy(alpha = 0.7f))

    SectionCard("$title – ${list.size} sítí") {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(top = 8.dp),
        ) {
            val left = 26.dp.toPx()
            val top = 14.dp.toPx()
            val bottom = size.height - 16.dp.toPx()
            val w = size.width - left

            fun fx(f: Float): Float = left + (f - f0) / (f1 - f0).toFloat() * w
            fun fy(r: Float): Float = top + (bottom - top) * (1f - ((r + 100f) / 70f).coerceIn(0f, 1f))

            for (r in listOf(-90, -80, -70, -60, -50, -40)) {
                val y = fy(r.toFloat())
                drawLine(gridColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
                drawText(tm, "$r", Offset(0f, y - 6.dp.toPx()), style = labelStyle)
            }
            for ((ch, f) in channelLabels(f0)) {
                if (f < f0 || f > f1) continue
                val x = fx(f.toFloat())
                drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                drawText(tm, "$ch", Offset(x - 5.dp.toPx(), bottom + 2.dp.toPx()), style = labelStyle)
            }

            list.forEachIndexed { idx, ap ->
                val col = palette[idx % palette.size]
                fun drawBlock(center: Float, width: Int) {
                    val half = width / 2f
                    val xl = fx(center - half)
                    val xr = fx(center + half)
                    val yt = fy(ap.rssi.toFloat())
                    val yc = bottom - (bottom - yt) * 4f / 3f
                    val p = Path().apply {
                        moveTo(xl, bottom)
                        cubicTo(xl + (xr - xl) * 0.2f, yc, xr - (xr - xl) * 0.2f, yc, xr, bottom)
                        close()
                    }
                    drawPath(p, col.copy(alpha = 0.18f))
                    drawPath(p, col, style = Stroke(width = 2.dp.toPx()))
                }
                drawBlock(ap.centerFreq.toFloat(), ap.widthMhz)
                ap.secondaryCenterFreq?.let { drawBlock(it.toFloat(), 80) }
                val yt = fy(ap.rssi.toFloat())
                val label = ap.ssid.ifEmpty { "?" }.take(14)
                drawText(
                    tm,
                    label,
                    Offset(
                        (fx(ap.centerFreq.toFloat()) - 20.dp.toPx()).coerceAtLeast(left),
                        (yt - 12.dp.toPx()).coerceAtLeast(0f),
                    ),
                    style = TextStyle(fontSize = 9.sp, color = col),
                )
            }

        }
    }
}
