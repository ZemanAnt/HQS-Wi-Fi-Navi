package cz.hqsnet.wifinavi

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(vm: WifiVM) {
    val ctx = LocalContext.current
    val aps by vm.aps.collectAsState()
    val conn by vm.conn.collectAsState()
    val hist by vm.rssiHist.collectAsState()
    val lastUpdated by vm.lastScanMs.collectAsState()
    val exportStatus by vm.exportStatus.collectAsState()
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) vm.exportScannerCsv(uri) }
    var band by remember { mutableStateOf("Vše") }

    val shown = aps.filter { band == "Vše" || it.band.startsWith(band) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard("Aktuální připojení") {
                val c = conn
                if (c == null) {
                    Text("Nejste připojeni k Wi-Fi.")
                } else {
                    Text(
                        if (c.ssid.isEmpty() || c.ssid.startsWith("<unknown")) "(SSID nedostupné)" else c.ssid,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${c.rssi}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = rssiColor(c.rssi),
                        )
                        Text(" dBm", Modifier.padding(end = 16.dp))
                        Column {
                            Text(rssiLabel(c.rssi))
                            Text("Kanál ${c.channel} · ${c.band}")
                            Text(
                                "Link ${c.linkMbps} Mbps" +
                                    if (c.txMbps > 0) " (TX ${c.txMbps} / RX ${c.rxMbps})" else "",
                            )
                        }
                    }
                    Text("BSSID ${c.bssid}", style = MaterialTheme.typography.bodySmall)
                    Spark(
                        hist.map { it.toFloat() }, -100f, -30f, rssiColor(c.rssi),
                        Modifier.fillMaxWidth().height(60.dp).padding(top = 8.dp),
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Vše", "2,4", "5", "6").forEach { b ->
                        FilterChip(
                            selected = band == b,
                            onClick = { band = b },
                            label = { Text(if (b == "Vše") b else "$b GHz") },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${shown.size} sítí", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = { export.launch("hqs_wifi_sken.csv") },
                        enabled = aps.isNotEmpty(),
                    ) { Text("CSV") }
                    Button(onClick = {
                        if (!vm.scanNow()) {
                            Toast.makeText(
                                ctx,
                                "Android omezil skenování (max. 4× za 2 min). Zobrazuji poslední výsledky.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) { Text("Skenovat teď") }
                }
                Text(
                    if (lastUpdated == 0L) "Čekám na potvrzení nového skenu." else
                        "Poslední potvrzený sken: " +
                            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(lastUpdated)),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (exportStatus.isNotBlank()) {
                    Text(exportStatus, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Android může vracet starší výsledky skenování. Automatický interval: 35 s.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(shown) { ap ->
            val mine = conn?.bssid.equals(ap.bssid, ignoreCase = true)
            Card(
                Modifier.fillMaxWidth(),
                colors = if (mine) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ap.ssid.ifEmpty { "(skrytá síť)" } + if (mine) "  ● připojeno" else "",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Kanál ${ap.channel} · ${ap.band} · ${ap.widthLabel} MHz · ${ap.security}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(ap.bssid, style = MaterialTheme.typography.bodySmall)
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(((ap.rssi + 100) / 60f).coerceIn(0.02f, 1f))
                                    .height(6.dp)
                                    .background(rssiColor(ap.rssi)),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${ap.rssi}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = rssiColor(ap.rssi),
                        )
                        Text("dBm", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Box(Modifier.size(8.dp)) }
    }
}
