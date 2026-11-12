package cz.hqsnet.wifinavi

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun WalkScreen(vm: WifiVM) {
    val ctx = LocalContext.current
    val conn by vm.conn.collectAsState()
    val pts by vm.points.collectAsState()
    val plan by vm.plan.collectAsState()
    val hist by vm.rssiHist.collectAsState()
    val walkStatus by vm.walkStatus.collectAsState()
    val exportStatus by vm.exportStatus.collectAsState()
    var heat by remember { mutableStateOf(true) }
    val tm = rememberTextMeasurer()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.loadPlan(u)
        }
    }
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { u ->
        if (u != null) vm.exportCsv(u)
    }

    val ratio = plan?.let { it.width.toFloat() / it.height.toFloat() } ?: (4f / 3f)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard("Walking Test – živý signál") {
            val c = conn
            if (c == null) {
                Text("Nejste připojeni k Wi-Fi.")
            } else {
                val avg = hist.takeLast(3).let {
                    if (it.isEmpty()) c.rssi.toFloat() else it.average().toFloat()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${avg.roundToInt()}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = rssiColor(avg),
                    )
                    Text(
                        " dBm · ${c.band} · kanál ${c.channel}\n${c.bssid}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                "Postup: načtěte půdorys (obrázek), postavte se na místo, klepněte na odpovídající " +
                    "bod v plánu. Aplikace uloží průměr posledních 3 vzorků RSSI.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { pick.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
                Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
            ) { Text("Půdorys") }
            OutlinedButton(
                onClick = { vm.undoPoint() },
                Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
            ) { Text("Zpět") }
            OutlinedButton(
                onClick = { vm.clearPoints() },
                Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
            ) { Text("Smazat") }
            OutlinedButton(
                onClick = { save.launch("wifi_walking_test.csv") },
                Modifier.weight(1f),
                enabled = pts.isNotEmpty(),
                contentPadding = PaddingValues(4.dp),
            ) { Text("CSV") }
        }
        if (walkStatus.isNotBlank()) {
            Text(walkStatus, style = MaterialTheme.typography.bodySmall)
        }
        if (exportStatus.isNotBlank()) {
            Text(exportStatus, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Měřicí body a odkaz na půdorys se ukládají pouze do telefonu. Při volbě nového půdorysu se body smažou.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = heat, onCheckedChange = { heat = it })
            Text("Zobrazit heatmapu")
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(Color.White)
                .border(1.dp, Color.Gray)
                .pointerInput(plan) {
                    detectTapGestures { o ->
                        val ok = vm.addPoint(o.x / size.width, o.y / size.height)
                        if (!ok) {
                            Toast.makeText(ctx, "Nejste připojeni k Wi-Fi", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
        ) {
            val img = plan
            if (img != null) {
                drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            } else {
                // mrizka, kdyz neni nacteny pudorys
                val step = size.width / 10f
                var gx = 0f
                while (gx <= size.width) {
                    drawLine(Color(0xFFEEEEEE), Offset(gx, 0f), Offset(gx, size.height), 1f)
                    gx += step
                }
                var gy = 0f
                while (gy <= size.height) {
                    drawLine(Color(0xFFEEEEEE), Offset(0f, gy), Offset(size.width, gy), 1f)
                    gy += step
                }
            }

            if (heat && pts.isNotEmpty()) {
                val gxn = 48
                val gyn = (gxn / ratio).roundToInt().coerceIn(16, 120)
                val cw = size.width / gxn
                val ch = size.height / gyn
                for (j in 0 until gyn) {
                    for (i in 0 until gxn) {
                        val x = (i + 0.5f) / gxn
                        val y = (j + 0.5f) / gyn
                        var wsum = 0f
                        var vsum = 0f
                        var dmin = Float.MAX_VALUE
                        for (p in pts) {
                            val dx = (x - p.x) * ratio
                            val dy = y - p.y
                            val d2 = dx * dx + dy * dy + 1e-6f
                            val wgt = 1f / d2
                            wsum += wgt
                            vsum += wgt * p.rssi
                            if (d2 < dmin) dmin = d2
                        }
                        val v = vsum / wsum
                        val a = (0.6f * (1f - sqrt(dmin) / 0.30f)).coerceIn(0f, 0.6f)
                        if (a > 0f) {
                            drawRect(
                                rssiColor(v).copy(alpha = a),
                                topLeft = Offset(i * cw, j * ch),
                                size = Size(cw + 1f, ch + 1f),
                            )
                        }
                    }
                }
            }

            for (p in pts) {
                val centre = Offset(p.x * size.width, p.y * size.height)
                drawCircle(Color.White, 12.dp.toPx(), centre)
                drawCircle(rssiColor(p.rssi), 10.dp.toPx(), centre)
                drawText(
                    tm,
                    "${p.rssi}",
                    Offset(centre.x - 10.dp.toPx(), centre.y - 6.dp.toPx()),
                    style = TextStyle(fontSize = 9.sp, color = Color.Black),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Legenda:", style = MaterialTheme.typography.bodySmall)
            listOf(-90, -80, -70, -60, -50).forEach { r ->
                Box(Modifier.background(rssiColor(r)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("$r", fontSize = 11.sp, color = Color.Black)
                }
            }
            Text("dBm", style = MaterialTheme.typography.bodySmall)
        }

        if (pts.isNotEmpty()) {
            val vals = pts.map { it.rssi }
            val good = vals.count { it >= -67 } * 100 / vals.size
            SectionCard("Souhrn (${pts.size} bodů)") {
                Text("Min ${vals.min()} · průměr ${vals.average().roundToInt()} · max ${vals.max()} dBm")
                Text("Body se signálem ≥ −67 dBm: $good %")
            }
        }
    }
}
