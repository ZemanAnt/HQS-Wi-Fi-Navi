package cz.hqsnet.wifinavi

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CSV pro Excel CZ: UTF-8 BOM, strednik, uvozovky, ochrana proti CSV vzorcu. */
object CsvExport {
    private fun cell(value: String): String {
        val first = value.trimStart().firstOrNull()
        // Záporná čísla (např. -67 dBm) musí zůstat čísly, ne textem.
        val suspicious = value.toDoubleOrNull() == null &&
            (first == '=' || first == '+' || first == '-' || first == '@' ||
                first == '\t' || first == '\r' || first == '\n')
        val safe = if (suspicious) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private fun row(vararg values: String): String = values.joinToString(";") { cell(it) } + "\n"
    private fun datetime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(timestamp))

    fun walking(points: List<WPoint>): String = buildString {
        append('\uFEFF')
        append(row("x", "y", "rssi_dBm", "bssid", "ssid", "frekvence_MHz", "cas"))
        points.forEach { p ->
            append(row(
                "%.4f".format(Locale.US, p.x), "%.4f".format(Locale.US, p.y),
                p.rssi.toString(), p.bssid, p.ssid, p.freq.toString(), datetime(p.ts),
            ))
        }
    }

    fun scanner(aps: List<Ap>, timestamp: Long): String = buildString {
        append('\uFEFF')
        append(row("cas_skenu", "ssid", "bssid", "rssi_dBm", "pasmo", "kanal",
            "frekvence_MHz", "sirka_MHz", "zabezpeceni"))
        aps.forEach { a ->
            append(row(if (timestamp > 0) datetime(timestamp) else "", a.ssid, a.bssid, a.rssi.toString(),
                a.band, a.channel.toString(), a.freq.toString(), a.widthLabel, a.security))
        }
    }

    fun roaming(events: List<RoamEvent>, pings: List<RoamPingSample> = emptyList()): String = buildString {
        append('\uFEFF')
        append(row("cas", "bssid_z", "bssid_na", "rssi_z_dBm", "rssi_na_dBm",
            "frekvence_z_MHz", "frekvence_na_MHz", "pozorovane_preruseni_ms",
            "ping_okoli_odeslano", "ping_okoli_ztraceno", "ping_okoli_max_ms"))
        events.forEach { e ->
            val w = if (pings.isEmpty()) null else NetCalc.roamWindow(pings, e.ts)
            append(row(datetime(e.ts), e.fromBssid, e.toBssid,
                e.fromRssi.toString(), e.toRssi.toString(), e.fromFreq.toString(),
                e.toFreq.toString(), if (e.gapMs > 0) e.gapMs.toString() else "",
                w?.sent?.toString() ?: "", w?.lost?.toString() ?: "",
                if (w != null && w.sent > w.lost) "%.1f".format(Locale.US, w.maxMs) else ""))
        }
    }
}
