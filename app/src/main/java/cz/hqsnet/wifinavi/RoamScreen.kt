package cz.hqsnet.wifinavi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoamScreen(vm: WifiVM) {
    val running by vm.roamRunning.collectAsState()
    val events by vm.roamEvents.collectAsState()
    val rssi by vm.roamRssi.collectAsState()
    val conn by vm.conn.collectAsState()
    val exportStatus by vm.exportStatus.collectAsState()
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) vm.exportRoamCsv(uri) }
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // ping-pong: navrat na predchozi AP behem 10 s
    var pingPong = 0
    for (i in 1 until events.size) {
        if (events[i].toBssid == events[i - 1].fromBssid && events[i].ts - events[i - 1].ts < 10_000L) {
            pingPong++
        }
    }
    val gaps = events.filter { it.gapMs > 0 }.map { it.gapMs }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard("Roaming Test") {
                Text(
                    "Spusťte test a projděte areálem mezi dosahy dvou AP (tam i zpět). " +
                        "Aplikace zaznamenává každou změnu BSSID při stejném SSID " +
                        "(vzorkování ≈ 300 ms, přerušení je tedy orientační).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { if (running) vm.stopRoam() else vm.startRoam() }) {
                        Text(if (running) "Zastavit test" else "Spustit test")
                    }
                    OutlinedButton(
                        onClick = { export.launch("hqs_wifi_roaming.csv") },
                        enabled = events.isNotEmpty(),
                    ) { Text("Export CSV") }
                }
                if (exportStatus.isNotBlank()) {
                    Text(exportStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionCard("Aktuálně") {
                val c = conn
                if (c == null) {
                    Text("Nejste připojeni k Wi-Fi.")
                } else {
                    Text("AP: ${c.bssid}", fontWeight = FontWeight.SemiBold)
                    Text("${c.rssi} dBm · ${c.band} · kanál ${c.channel}")
                }
                Spark(
                    rssi.map { it.toFloat() }, -100f, -30f, MaterialTheme.colorScheme.primary,
                    Modifier.fillMaxWidth().height(70.dp).padding(top = 8.dp),
                )
            }
        }
        item {
            SectionCard("Souhrn") {
                Text("Počet přechodů mezi AP: ${events.size}")
                Text(
                    if (gaps.isEmpty()) {
                        "Přerušení Wi-Fi nebylo vzorkováním zachyceno (nejde o potvrzení nulového výpadku)."
                    } else {
                        "Přerušení při přechodu: průměr ${gaps.average().toLong()} ms · max ${gaps.max()} ms"
                    },
                )
                Text("Ping-pong přechody (zpět do 10 s): $pingPong")
                if (pingPong > 0) {
                    Text(
                        "Ping-pong značí příliš těsné hranice buněk nebo agresivní roaming – " +
                            "zvažte snížení výkonu AP nebo úpravu prahu roamingu.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(events.reversed()) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "${fmt.format(Date(e.ts))}  ${bandOf(e.fromFreq)} → ${bandOf(e.toFreq)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(e.fromBssid + " → " + e.toBssid, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${e.fromRssi} → ${e.toRssi} dBm · přerušení " +
                            if (e.gapMs > 0) "≈ ${e.gapMs} ms" else "nezaznamenáno",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
