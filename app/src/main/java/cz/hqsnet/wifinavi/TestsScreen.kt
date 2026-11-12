package cz.hqsnet.wifinavi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun TestsScreen(vm: WifiVM) {
    val gw by vm.gateway.collectAsState()
    val ping by vm.pingStats.collectAsState()
    val pingError by vm.pingError.collectAsState()
    val dl by vm.dl.collectAsState()
    val ul by vm.ul.collectAsState()

    var host by remember { mutableStateOf("") }
    var hostManuallyEdited by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var iperfHost by remember { mutableStateOf("") }
    var iperfPort by remember { mutableStateOf("5001") }

    LaunchedEffect(gw) {
        if (!hostManuallyEdited) host = gw ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard("Ping / latence / jitter") {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it; hostManuallyEdited = true },
                label = { Text("Host (výchozí: brána)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startPing(host) }, enabled = !ping.running && host.isNotBlank()) { Text("Start") }
                OutlinedButton(onClick = { vm.stopPing() }, enabled = ping.running) { Text("Stop") }
            }
            if (pingError != null) Text(pingError ?: "", color = MaterialTheme.colorScheme.error)
            if (ping.sent > 0) {
                Text("Odesláno ${ping.sent} · přijato ${ping.recv} · ztráty ${fmt1(ping.lossPct)} %")
                Text(
                    "min ${fmt1(ping.min)} / avg ${fmt1(ping.avg)} / max ${fmt1(ping.max)} ms · " +
                        "jitter ${fmt1(ping.jitter)} ms",
                )
                val shown = ping.last.map { it?.toFloat() }
                val top = ((shown.filterNotNull().maxOrNull() ?: 20f) * 1.2f).coerceAtLeast(10f)
                Spark(
                    shown, 0f, top, MaterialTheme.colorScheme.primary,
                    Modifier.fillMaxWidth().height(70.dp).padding(top = 8.dp),
                )
            }
        }

        SectionCard("Rychlost – stažení (HTTP)") {
            Text("Na PC v LAN spusťte nastroje/SPUSTIT_TEST_SERVER.bat. Zadejte například " +
                "http://192.168.1.10:8080/download (IP změňte podle počítače).",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL souboru (ideálně server v LAN)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startDownload(url, 10) }, enabled = !dl.running && url.isNotBlank()) { Text("Start (10 s)") }
                OutlinedButton(onClick = { vm.stopSpeed() }, enabled = dl.running) { Text("Stop") }
            }
            SpeedView(dl)
        }

        SectionCard("Rychlost – odeslání (TCP, HQS server)") {
            Text(
                "Na PC spusťte přiložený HQS_test_server.py (výchozí TCP port 5001). " +
                    "Nejde o implementaci protokolu iperf, použijte náš server. " +
                    "Výsledek porovnejte s hodnotou vypsanou na PC.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = iperfHost,
                onValueChange = { iperfHost = it },
                label = { Text("Adresa serveru") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = iperfPort,
                onValueChange = { iperfPort = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.startUpload(iperfHost, iperfPort.toIntOrNull() ?: 5001, 10) },
                    enabled = !ul.running && iperfHost.isNotBlank(),
                ) { Text("Start (10 s)") }
                OutlinedButton(onClick = { vm.stopSpeed() }, enabled = ul.running) { Text("Stop") }
            }
            SpeedView(ul)
        }
    }
}

@Composable
private fun SpeedView(s: SpeedState) {
    if (s.error != null) {
        Text("Chyba: ${s.error}", color = MaterialTheme.colorScheme.error)
        return
    }
    if (s.running || s.done) {
        Text(
            (if (s.done) "Výsledek: " else "Probíhá: ") + fmt1(s.mbps) + " Mbit/s" +
                " · " + fmt1(s.bytes / 1_048_576.0) + " MB za " + fmt1(s.seconds) + " s",
        )
        val top = ((s.samples.maxOrNull() ?: 10.0) * 1.2).toFloat().coerceAtLeast(5f)
        Spark(
            s.samples.map { it.toFloat() }, 0f, top, MaterialTheme.colorScheme.primary,
            Modifier.fillMaxWidth().height(70.dp).padding(top = 8.dp),
        )
    }
}
