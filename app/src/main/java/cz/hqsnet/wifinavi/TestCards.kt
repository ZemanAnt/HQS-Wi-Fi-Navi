package cz.hqsnet.wifinavi

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Jedno klepnutí: signál, zabezpečení, rušení, brána, DNS, internet, propustnost. */
@Composable
fun IntegratedTestCard(vm: WifiVM, tvm: ToolsVM) {
    val st by tvm.integrated.collectAsState()
    val conn by vm.conn.collectAsState()
    val aps by vm.aps.collectAsState()
    val gw by vm.gateway.collectAsState()
    val ctx = LocalContext.current

    SectionCard("Wi-Fi integrovaný test") {
        Text(
            "Jedním klepnutím zkontroluje signál, zabezpečení, rušení okolními AP, bránu, DNS, " +
                "internet a rychlost (cca 25–35 s). Rychlost se měří vůči serverům Cloudflare.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { tvm.runIntegrated(conn, aps, gw) },
                enabled = !st.running,
            ) { Text("Spustit test") }
            OutlinedButton(onClick = { tvm.stopIntegrated() }, enabled = st.running) { Text("Stop") }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, NetCalc.report(st.steps))
                    }
                    ctx.startActivity(Intent.createChooser(send, "Sdílet report"))
                },
                enabled = st.steps.isNotEmpty() && !st.running,
            ) { Text("Sdílet") }
        }
        if (st.error != null) Text(st.error ?: "", color = MaterialTheme.colorScheme.error)
        if (st.running && st.stage.isNotBlank()) Text("Probíhá: ${st.stage}…")
        if (st.done) Text(NetCalc.verdict(st.steps), fontWeight = FontWeight.Bold)
        st.steps.forEach { s ->
            val mark = when (s.level) {
                TestLevel.OK -> "✅"
                TestLevel.WARN -> "⚠️"
                TestLevel.FAIL -> "❌"
                TestLevel.INFO -> "ℹ️"
            }
            Column(Modifier.padding(top = 6.dp)) {
                Text("$mark ${s.name}", fontWeight = FontWeight.SemiBold)
                Text(s.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Rychlost internetu přes Cloudflare (4 paralelní spojení, 8 s na směr). */
@Composable
fun InternetSpeedCard(tvm: ToolsVM) {
    val lat by tvm.inetLat.collectAsState()
    val dl by tvm.inetDl.collectAsState()
    val ul by tvm.inetUl.collectAsState()
    val running = dl.running || ul.running

    SectionCard("Rychlost internetu") {
        Text(
            "Měří latenci, stažení a odeslání vůči speed.cloudflare.com (4 spojení, 8 s na směr). " +
                "Spotřebuje desítky až stovky MB dat.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tvm.startInternetSpeed() }, enabled = !running) { Text("Start") }
            OutlinedButton(onClick = { tvm.stopSpeed() }, enabled = running) { Text("Stop") }
        }
        lat?.let { Text("Latence: $it") }
        if (dl.error != null || dl.running || dl.done) {
            Text("Stažení", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            SpeedView(dl)
        }
        if (ul.running || ul.done || ul.error != null) {
            Text("Odeslání", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            SpeedView(ul)
        }
    }
}

/** TCP klient kompatibilní s `iperf -s` (iPerf2) a s HQS test serverem; více paralelních vláken. */
@Composable
fun IperfCard(tvm: ToolsVM) {
    val st by tvm.iperf.collectAsState()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5001") }
    var streams by remember { mutableStateOf("4") }
    var secs by remember { mutableStateOf("10") }

    SectionCard("iPerf klient (TCP, více vláken)") {
        Text(
            "Na PC/serveru spusťte iPerf2 v režimu serveru (iperf -s) nebo přiložený HQS_test_server.py " +
                "(port 5001). Aplikace odesílá data klient → server v zadaném počtu paralelních vláken. " +
                "Směr server → klient (-R), UDP, retransmise a plná hlavička iPerf nejsou podporovány. " +
                "Kompatibilita s iPerf2 nebyla ověřena na reálném serveru.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Adresa serveru") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            NumField("Port", port, { port = it.take(5) }, Modifier.weight(1f))
            NumField("Vlákna", streams, { streams = it.take(2) }, Modifier.weight(1f))
            NumField("Doba (s)", secs, { secs = it.take(3) }, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    tvm.startIperf(
                        host, port.toIntOrNull() ?: 5001,
                        streams.toIntOrNull() ?: 1, secs.toIntOrNull() ?: 10,
                    )
                },
                enabled = !st.running && host.isNotBlank(),
            ) { Text("Start") }
            OutlinedButton(onClick = { tvm.stopSpeed() }, enabled = st.running) { Text("Stop") }
        }
        SpeedView(st)
    }
}
