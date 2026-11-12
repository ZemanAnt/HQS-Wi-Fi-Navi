package cz.hqsnet.wifinavi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen(vm: WifiVM, tvm: ToolsVM) {
    val gw by vm.gateway.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PublicIpCard(tvm)
        TraceCard(tvm)
        IpScanCard(tvm)
        PortScanCard(tvm, gw)
        BandwidthCard()
        PoeCard()
    }
}

@Composable
private fun PublicIpCard(tvm: ToolsVM) {
    val st by tvm.publicIp.collectAsState()
    SectionCard("Veřejná IP adresa") {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tvm.lookupPublicIp() }, enabled = !st.busy) { Text("Zjistit") }
        }
        if (st.busy) Text("Zjišťuji…")
        else if (st.text.isNotBlank()) {
            Text(
                st.text,
                fontWeight = FontWeight.SemiBold,
                color = if (st.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TraceCard(tvm: ToolsVM) {
    val st by tvm.trace.collectAsState()
    var host by remember { mutableStateOf("8.8.8.8") }
    SectionCard("Trasa (traceroute)") {
        Text(
            "Postupné ICMP s rostoucím TTL (IPv4). Čas mezilehlých uzlů je orientační " +
                "(zahrnuje spuštění systémového pingu). Některé routery neodpovídají.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Cíl (IP nebo název)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tvm.startTrace(host) }, enabled = !st.running && host.isNotBlank()) { Text("Start") }
            OutlinedButton(onClick = { tvm.stopTrace() }, enabled = st.running) { Text("Stop") }
        }
        if (st.error != null) Text(st.error ?: "", color = MaterialTheme.colorScheme.error)
        st.hops.forEach { h ->
            val t = h.ms?.let { " · ≈ ${fmt1(it)} ms" } ?: ""
            Text(
                "${h.ttl}.  " + (h.addr ?: "*  (bez odpovědi)") + t + if (h.reached) "  ✔ cíl" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun IpScanCard(tvm: ToolsVM) {
    val st by tvm.ipScan.collectAsState()
    SectionCard("Hledání zařízení (IP scanner)") {
        Text(
            "Projde podsíť telefonu (max. /24): ICMP a krátké TCP sondy na běžné porty. " +
                "Zařízení, která neodpovídají vůbec, se nezobrazí. MAC adresa bývá od Androidu 10 nedostupná. " +
                "Skenujte pouze sítě, které spravujete.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tvm.startIpScan() }, enabled = !st.running) { Text("Skenovat") }
            OutlinedButton(onClick = { tvm.stopIpScan() }, enabled = st.running) { Text("Stop") }
        }
        if (st.error != null) Text(st.error ?: "", color = MaterialTheme.colorScheme.error)
        if (st.range.isNotBlank()) {
            Text(
                "Rozsah ${st.range} · ${st.progress}/${st.total} · nalezeno ${st.hosts.size}" +
                    if (st.done) " · hotovo" else "",
            )
        }
        st.hosts.forEach { h ->
            Column(Modifier.padding(top = 6.dp)) {
                Text(
                    h.ip + (h.name?.let { "  ($it)" } ?: "") + (h.ms?.let { " · ${fmt1(it)} ms" } ?: ""),
                    fontWeight = FontWeight.SemiBold,
                )
                val extra = buildList {
                    h.mac?.let { add("MAC $it") }
                    if (h.ports.isNotEmpty()) {
                        add("porty " + h.ports.joinToString(", ") { p ->
                            NetCalc.serviceName(p).let { n -> if (n.isEmpty()) "$p" else "$p/$n" }
                        })
                    }
                }
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PortScanCard(tvm: ToolsVM, gateway: String?) {
    val st by tvm.portScan.collectAsState()
    var host by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf(false) }
    var ports by remember { mutableStateOf("21,22,23,53,80,443,445,554,3389,8080,8443") }
    if (!edited && host.isBlank() && gateway != null) host = gateway

    SectionCard("Skener portů (TCP)") {
        Text("Skenujte pouze zařízení, která spravujete.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = host,
            onValueChange = { host = it; edited = true },
            label = { Text("Cíl (IP nebo název)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ports,
            onValueChange = { ports = it },
            label = { Text("Porty (např. 22,80,443 nebo 1-1024)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ports = "21,22,23,53,80,443,445,554,3389,8080,8443" }) { Text("Běžné") }
            OutlinedButton(onClick = { ports = "1-1024" }) { Text("1–1024") }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tvm.startPortScan(host, ports) }, enabled = !st.running && host.isNotBlank()) {
                Text("Skenovat")
            }
            OutlinedButton(onClick = { tvm.stopPortScan() }, enabled = st.running) { Text("Stop") }
        }
        if (st.error != null) Text(st.error ?: "", color = MaterialTheme.colorScheme.error)
        if (st.total > 0) {
            Text("${st.target}: ${st.progress}/${st.total} portů · otevřeno ${st.open.size}" + if (st.done) " · hotovo" else "")
        }
        st.open.forEach { p ->
            val n = NetCalc.serviceName(p)
            Text("• $p" + if (n.isEmpty()) "" else "  ($n)")
        }
    }
}

@Composable
private fun BandwidthCard() {
    SectionCard("Kalkulačka šířky pásma") {
        // --- čas přenosu ---
        var size by remember { mutableStateOf("10") }
        var unit by remember { mutableStateOf(1) }
        var speed by remember { mutableStateOf("100") }
        var eff by remember { mutableStateOf("70") }
        Text("Čas přenosu souboru", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Velikost", size, { size = it }, Modifier.weight(1f), decimal = true)
            NumField("Rychlost (Mbit/s)", speed, { speed = it }, Modifier.weight(1f), decimal = true)
        }
        ChoiceRow(listOf("MB", "GB", "TB"), unit) { unit = it }
        NumField("Reálné využití linky (%)", eff, { eff = it }, Modifier.fillMaxWidth(), decimal = true)
        val bytes = size.num()?.let { it * listOf(1e6, 1e9, 1e12)[unit] }
        val sec = if (bytes != null && speed.num() != null && eff.num() != null) {
            NetCalc.transferSeconds(bytes, speed.num()!!, eff.num()!!)
        } else null
        Text("Doba přenosu: " + (sec?.let { NetCalc.formatDuration(it) } ?: "–"))

        // --- potřebná kapacita ---
        var users by remember { mutableStateOf("50") }
        var per by remember { mutableStateOf("5") }
        var conc by remember { mutableStateOf("60") }
        var over by remember { mutableStateOf("20") }
        Text("Potřebná kapacita uplinku", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Uživatelé", users, { users = it }, Modifier.weight(1f))
            NumField("Mbit/s na uživ.", per, { per = it }, Modifier.weight(1f), decimal = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { per = "2" }) { Text("Web 2") }
            OutlinedButton(onClick = { per = "5" }) { Text("HD 5") }
            OutlinedButton(onClick = { per = "25" }) { Text("4K 25") }
            OutlinedButton(onClick = { per = "0.1" }) { Text("VoIP") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Souběžně (%)", conc, { conc = it }, Modifier.weight(1f), decimal = true)
            NumField("Rezerva (%)", over, { over = it }, Modifier.weight(1f), decimal = true)
        }
        val req = if (users.num() != null && per.num() != null && conc.num() != null && over.num() != null) {
            NetCalc.requiredMbps(users.num()!!, per.num()!!, conc.num()!!, over.num()!!)
        } else null
        Text(
            if (req == null) "Potřebná kapacita: –"
            else "Potřebná kapacita: ${fmt1(req)} Mbit/s → doporučená linka ${NetCalc.recommendedLink(req)}",
        )

        // --- CCTV ---
        var cams by remember { mutableStateOf("16") }
        var rate by remember { mutableStateOf("4") }
        var days by remember { mutableStateOf("14") }
        Text("Kamerový systém (CCTV)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Kamery", cams, { cams = it }, Modifier.weight(1f))
            NumField("Mbit/s na kameru", rate, { rate = it }, Modifier.weight(1f), decimal = true)
            NumField("Dnů záznamu", days, { days = it }, Modifier.weight(1f), decimal = true)
        }
        val total = if (cams.num() != null && rate.num() != null) cams.num()!! * rate.num()!! else null
        if (total == null) {
            Text("Celkem: –")
        } else {
            val gb = days.num()?.let { NetCalc.cctvStorageGb(total, it) }
            Text(
                "Celkem ${fmt1(total)} Mbit/s (nepřetržitý záznam)" +
                    (gb?.let { " · úložiště ≈ ${fmt1(it / 1000.0)} TB" } ?: ""),
            )
        }
    }
}

@Composable
private fun PoeCard() {
    val counts = remember { mutableStateListOf(*Array(8) { "" }) }
    var customN by remember { mutableStateOf("") }
    var customW by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }

    SectionCard("Kalkulačka PoE") {
        Text(
            "Zadejte počet zařízení podle PoE třídy. Switch rezervuje výkon podle třídy (PSE); " +
                "skutečná spotřeba zařízení bývá nižší. Výkon na konci kabelu (PD) je garantované minimum.",
            style = MaterialTheme.typography.bodySmall,
        )
        NetCalc.poeClasses.forEachIndexed { i, c ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Třída ${c.cls} · ${c.standard}\n${fmt1(c.pseW)} W PSE / ${fmt1(c.pdW)} W PD",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                NumField("Počet", counts[i], { counts[i] = it.take(4) }, Modifier.weight(1f))
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Vlastní: počet", customN, { customN = it.take(4) }, Modifier.weight(1f))
            NumField("Vlastní: W", customW, { customW = it }, Modifier.weight(1f), decimal = true)
        }
        NumField("Rozpočet switche (W)", budget, { budget = it }, Modifier.fillMaxWidth().padding(top = 4.dp), decimal = true)

        val cnt = counts.map { it.toIntOrNull() ?: 0 }
        val cn = customN.toIntOrNull() ?: 0
        val cw = customW.num() ?: 0.0
        val pse = NetCalc.poeTotalPse(cnt, cn, cw)
        val pd = NetCalc.poeTotalPd(cnt, cn, cw)
        val ports = cnt.sum() + cn
        Text("Zařízení: $ports · rezervováno (PSE): ${fmt1(pse)} W · na koncích (PD): ${fmt1(pd)} W",
            modifier = Modifier.padding(top = 8.dp))
        val b = budget.num()
        if (b != null && b > 0) {
            val left = b - pse
            Text(
                if (left >= 0) "Rezerva rozpočtu: ${fmt1(left)} W (využito ${fmt1(pse * 100 / b)} %)"
                else "Rozpočet je překročen o ${fmt1(-left)} W – nižší třídy, silnější switch nebo midspan injektor.",
                color = if (left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
            if (left >= 0 && pse > b * 0.8) {
                Text("Využití přes 80 % – doporučena rezerva pro rozšíření.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
