package cz.hqsnet.wifinavi

/** Jedna viditelna sit (AP) z vysledku skenovani. */
data class Ap(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val freq: Int,
    val centerFreq: Int,
    val widthMhz: Int,
    val caps: String,
    val secondaryCenterFreq: Int? = null,
) {
    val widthLabel: String get() = if (secondaryCenterFreq != null) "80+80" else "$widthMhz"
    val band: String get() = bandOf(freq)
    val channel: Int get() = freqToChannel(freq)
    val security: String get() = securityOf(caps)
}

/** Aktualni pripojeni telefonu. */
data class Conn(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val freq: Int,
    val linkMbps: Int,
    val txMbps: Int,
    val rxMbps: Int,
) {
    val band: String get() = bandOf(freq)
    val channel: Int get() = freqToChannel(freq)
}

/** Bod namereny pri chuzi (souradnice normalizovane 0..1 v ramci pudorysu). */
data class WPoint(
    val x: Float,
    val y: Float,
    val rssi: Int,
    val bssid: String,
    val ssid: String,
    val freq: Int,
    val ts: Long,
)

/** Udalost preskoku mezi AP. */
data class RoamEvent(
    val ts: Long,
    val fromBssid: String,
    val toBssid: String,
    val fromRssi: Int,
    val toRssi: Int,
    val fromFreq: Int,
    val toFreq: Int,
    val gapMs: Long,
)

data class PingStats(
    val running: Boolean = false,
    val sent: Int = 0,
    val recv: Int = 0,
    val min: Double = 0.0,
    val avg: Double = 0.0,
    val max: Double = 0.0,
    val jitter: Double = 0.0,
    val last: List<Double?> = emptyList(),
) {
    val lossPct: Double get() = if (sent == 0) 0.0 else (sent - recv) * 100.0 / sent
}

data class SpeedState(
    val running: Boolean = false,
    val mbps: Double = 0.0,
    val bytes: Long = 0,
    val seconds: Double = 0.0,
    val error: String? = null,
    val samples: List<Double> = emptyList(),
    val done: Boolean = false,
)

fun bandOf(freq: Int): String = when {
    freq < 3000 -> "2,4 GHz"
    freq < 5900 -> "5 GHz"
    else -> "6 GHz"
}

fun freqToChannel(f: Int): Int = when {
    f == 2484 -> 14
    f in 2412..2472 -> (f - 2407) / 5
    f in 5160..5885 -> (f - 5000) / 5
    f == 5935 -> 2
    f in 5955..7115 -> (f - 5950) / 5
    else -> 0
}

fun securityOf(c: String): String = when {
    c.contains("SAE") && c.contains("PSK") -> "WPA2/WPA3"
    c.contains("SUITE_B_192") || c.contains("SUITE-B-192") -> "WPA3-Ent"
    c.contains("SAE") -> "WPA3"
    c.contains("OWE") -> "OWE"
    c.contains("WPA2") || c.contains("RSN") -> if (c.contains("EAP")) "WPA2-Ent" else "WPA2"
    c.contains("WPA") -> "WPA"
    c.contains("WEP") -> "WEP"
    else -> "Otevřená"
}

fun validBssid(b: String?): Boolean =
    !b.isNullOrBlank() && b != "02:00:00:00:00:00" && b != "00:00:00:00:00:00"
