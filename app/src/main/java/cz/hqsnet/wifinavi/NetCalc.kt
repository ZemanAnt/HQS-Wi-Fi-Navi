package cz.hqsnet.wifinavi

import kotlin.math.abs
import kotlin.math.sqrt

/** Ciste vypocty bez Android SDK (testovatelne na JVM). */
object NetCalc {

    // ---------------- ping / traceroute ----------------

    fun parsePingTime(out: String): Double? =
        Regex("time[=<]\\s*([0-9.]+)").find(out)?.groupValues?.get(1)?.toDoubleOrNull()

    data class TraceReply(val addr: String, val reached: Boolean, val ms: Double?)

    private val fromIp = Regex("(?i)from\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})")

    /** Rozbor vystupu `ping -c 1 -t TTL`: odpoved cile, nebo "Time to live exceeded" z mezilehleho routeru. */
    fun parseTrace(out: String): TraceReply? {
        val m = fromIp.find(out) ?: return null
        val exceeded = out.contains("exceeded", ignoreCase = true)
        val reached = !exceeded && out.contains("bytes from", ignoreCase = true)
        return TraceReply(m.groupValues[1], reached, parsePingTime(out))
    }

    // ---------------- IPv4 ----------------

    fun ipToLong(s: String): Long? {
        val p = s.trim().split(".")
        if (p.size != 4) return null
        var v = 0L
        for (x in p) {
            val n = x.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            v = (v shl 8) or n.toLong()
        }
        return v
    }

    fun longToIp(v: Long): String =
        "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    /** Adresy hostu v podsiti (bez vlastni adresy). Prefix < 24 se omezi na /24 kolem zarizeni. */
    fun subnetHosts(ip: String, prefix: Int): List<String> {
        val addr = ipToLong(ip) ?: return emptyList()
        val p = if (prefix < 24) 24 else prefix
        if (p > 30) return emptyList()
        val mask = (0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL
        val net = addr and mask
        val bcast = net or (mask.inv() and 0xFFFFFFFFL)
        return (net + 1 until bcast).filter { it != addr }.map { longToIp(it) }
    }

    fun rangeLabel(ip: String, prefix: Int): String {
        val addr = ipToLong(ip) ?: return ""
        val p = if (prefix < 24) 24 else prefix
        val mask = (0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL
        return longToIp(addr and mask) + "/" + p
    }

    // ---------------- porty ----------------

    /** "22,80,8000-8010" -> serazeny seznam; null pri chybe. */
    fun parsePorts(spec: String): List<Int>? {
        val out = sortedSetOf<Int>()
        val tokens = spec.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        for (t in tokens) {
            if (t.contains('-')) {
                val a = t.substringBefore('-').toIntOrNull() ?: return null
                val b = t.substringAfter('-').toIntOrNull() ?: return null
                if (a !in 1..65535 || b !in 1..65535 || a > b) return null
                for (p in a..b) out.add(p)
            } else {
                val p = t.toIntOrNull() ?: return null
                if (p !in 1..65535) return null
                out.add(p)
            }
        }
        return out.toList()
    }

    private val services = mapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS", 80 to "HTTP",
        110 to "POP3", 123 to "NTP", 139 to "NetBIOS", 143 to "IMAP", 161 to "SNMP",
        443 to "HTTPS", 445 to "SMB", 554 to "RTSP", 587 to "SMTP", 631 to "IPP",
        993 to "IMAPS", 1883 to "MQTT", 3306 to "MySQL", 3389 to "RDP", 5000 to "UPnP/HTTP",
        5060 to "SIP", 5353 to "mDNS", 5900 to "VNC", 8000 to "HTTP-alt", 8080 to "HTTP-alt",
        8443 to "HTTPS-alt", 8554 to "RTSP-alt", 8888 to "HTTP-alt", 37777 to "Dahua", 34567 to "DVR",
        62078 to "Apple lockdown",
    )

    fun serviceName(port: Int): String = services[port] ?: ""

    // ---------------- kalkulacka pasma ----------------

    fun transferSeconds(sizeBytes: Double, mbps: Double, efficiencyPct: Double): Double {
        val eff = mbps * 1e6 * efficiencyPct / 100.0
        return if (eff <= 0.0) Double.NaN else sizeBytes * 8.0 / eff
    }

    fun requiredMbps(users: Double, perUserMbps: Double, concurrentPct: Double, overheadPct: Double): Double =
        users * perUserMbps * (concurrentPct / 100.0) * (1.0 + overheadPct / 100.0)

    fun recommendedLink(mbps: Double): String = when {
        mbps <= 80 -> "100 Mbit/s"
        mbps <= 800 -> "1 Gbit/s"
        mbps <= 2000 -> "2,5 Gbit/s"
        mbps <= 4000 -> "5 Gbit/s"
        mbps <= 8000 -> "10 Gbit/s"
        else -> "více linek / agregace (> 10 Gbit/s)"
    }

    /** Ulozisko CCTV v GB (desitkove): Mbit/s -> MB/s -> za dny. */
    fun cctvStorageGb(totalMbps: Double, days: Double): Double = totalMbps / 8.0 * 86_400.0 * days / 1000.0

    fun formatDuration(sec: Double): String {
        if (sec.isNaN() || sec.isInfinite()) return "–"
        val s = sec.toLong()
        val d = s / 86_400
        val h = (s % 86_400) / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return buildString {
            if (d > 0) append("$d d ")
            if (d > 0 || h > 0) append("$h h ")
            if (d == 0L && (h > 0 || m > 0)) append("$m min ")
            if (d == 0L && h == 0L) append(if (s < 60) String.format("%.1f s", sec) else "$r s")
        }.trim()
    }

    // ---------------- PoE ----------------

    /** PSE = vykon, ktery rezervuje switch; PD = garantovany vykon na konci kabelu. */
    val poeClasses = listOf(
        PoeClass(1, "802.3af", 4.0, 3.84),
        PoeClass(2, "802.3af", 7.0, 6.49),
        PoeClass(3, "802.3af", 15.4, 12.95),
        PoeClass(4, "802.3at", 30.0, 25.5),
        PoeClass(5, "802.3bt", 45.0, 40.0),
        PoeClass(6, "802.3bt", 60.0, 51.0),
        PoeClass(7, "802.3bt", 75.0, 62.0),
        PoeClass(8, "802.3bt", 90.0, 71.3),
    )

    fun poeTotalPse(counts: List<Int>, customCount: Int, customW: Double): Double {
        var t = 0.0
        poeClasses.forEachIndexed { i, c -> t += c.pseW * (counts.getOrElse(i) { 0 }) }
        return t + customCount * customW
    }

    fun poeTotalPd(counts: List<Int>, customCount: Int, customW: Double): Double {
        var t = 0.0
        poeClasses.forEachIndexed { i, c -> t += c.pdW * (counts.getOrElse(i) { 0 }) }
        return t + customCount * customW
    }

    // ---------------- roaming: ping okolo prechodu ----------------

    fun roamWindow(
        samples: List<RoamPingSample>,
        eventTs: Long,
        beforeMs: Long = 1000L,
        afterMs: Long = 3000L,
    ): RoamWindow = windowOf(samples.filter { it.ts >= eventTs - beforeMs && it.ts <= eventTs + afterMs })

    fun windowOf(w: List<RoamPingSample>): RoamWindow {
        val ok = w.mapNotNull { it.rttMs }
        return RoamWindow(
            sent = w.size,
            lost = w.size - ok.size,
            avgMs = if (ok.isEmpty()) 0.0 else ok.average(),
            maxMs = ok.maxOrNull() ?: 0.0,
        )
    }

    // ---------------- integrovany test: hodnoceni ----------------

    fun median(v: List<Double>): Double {
        if (v.isEmpty()) return 0.0
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    fun jitter(v: List<Double>): Double =
        if (v.size < 2) 0.0 else (1 until v.size).sumOf { abs(v[it] - v[it - 1]) } / (v.size - 1)

    fun stdDev(v: List<Double>): Double {
        if (v.size < 2) return 0.0
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }

    fun rateRssi(rssi: Int): TestLevel = when {
        rssi >= -67 -> TestLevel.OK
        rssi >= -75 -> TestLevel.WARN
        else -> TestLevel.FAIL
    }

    fun rateSecurity(sec: String): TestLevel = when (sec) {
        "WPA2", "WPA3", "WPA2/WPA3", "WPA2-Ent", "WPA3-Ent" -> TestLevel.OK
        "OWE" -> TestLevel.WARN
        else -> TestLevel.FAIL // otevrena, WEP, WPA(1)
    }

    fun rateGateway(lossPct: Double, avgMs: Double, jitterMs: Double): TestLevel = when {
        lossPct > 5.0 || avgMs > 60.0 -> TestLevel.FAIL
        lossPct > 1.0 || avgMs > 20.0 || jitterMs > 10.0 -> TestLevel.WARN
        else -> TestLevel.OK
    }

    /** AP prekryvajici spektrum pripojene site (mimo ni samu), pocitano jen se signalem >= -80 dBm. */
    fun interference(own: Conn, aps: List<Ap>): Interference {
        val me = aps.firstOrNull { it.bssid.equals(own.bssid, ignoreCase = true) }
        val ownW = me?.widthMhz ?: 20
        val ownC = me?.centerFreq ?: own.freq
        var n = 0
        var same = 0
        var strongest: Int? = null
        for (a in aps) {
            if (a.bssid.equals(own.bssid, ignoreCase = true)) continue
            if (bandOf(a.freq) != own.band || a.rssi < -80) continue
            val overlap = abs(a.centerFreq - ownC) < (a.widthMhz + ownW) / 2
            if (!overlap) continue
            if (a.ssid == own.ssid && a.ssid.isNotEmpty()) {
                same++
            } else {
                n++
                if (strongest == null || a.rssi > strongest) strongest = a.rssi
            }
        }
        return Interference(n, strongest, same)
    }

    fun rateInterference(i: Interference): TestLevel = when {
        i.overlapping >= 5 || (i.strongest ?: -100) >= -55 -> TestLevel.FAIL
        i.overlapping >= 2 || (i.strongest ?: -100) >= -65 -> TestLevel.WARN
        else -> TestLevel.OK
    }

    fun verdict(steps: List<TestStep>): String = when {
        steps.any { it.level == TestLevel.FAIL } -> "Nalezeny problémy"
        steps.any { it.level == TestLevel.WARN } -> "V pořádku s výhradami"
        else -> "Bez nalezených problémů"
    }

    fun report(steps: List<TestStep>): String = buildString {
        appendLine("HQS Wi-Fi Navi – integrovaný test")
        appendLine("Závěr: " + verdict(steps))
        steps.forEach {
            val mark = when (it.level) {
                TestLevel.OK -> "[OK]"
                TestLevel.WARN -> "[POZOR]"
                TestLevel.FAIL -> "[CHYBA]"
                TestLevel.INFO -> "[i]"
            }
            appendLine("$mark ${it.name}: ${it.detail}")
        }
    }
}
