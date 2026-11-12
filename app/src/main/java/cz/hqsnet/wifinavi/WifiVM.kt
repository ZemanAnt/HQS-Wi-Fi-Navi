package cz.hqsnet.wifinavi

import android.app.Application
import android.content.BroadcastReceiver
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WifiVM(app: Application) : AndroidViewModel(app) {
    private val repo = WifiRepo(app)
    private val surveyStore = SurveyStore(app)
    private var surveyPlanUri: String? = null
    private var saveJob: Job? = null
    val walkStatus = MutableStateFlow("")
    val exportStatus = MutableStateFlow("")

    // ---- zivy stav ----
    val aps = MutableStateFlow<List<Ap>>(emptyList())
    val conn = MutableStateFlow<Conn?>(null)
    val rssiHist = MutableStateFlow<List<Int>>(emptyList())
    val gateway = MutableStateFlow<String?>(null)
    val lastScanMs = MutableStateFlow(0L)

    // ---- chuze ----
    val plan = MutableStateFlow<ImageBitmap?>(null)
    val points = MutableStateFlow<List<WPoint>>(emptyList())

    // ---- roaming ----
    val roamRunning = MutableStateFlow(false)
    val roamEvents = MutableStateFlow<List<RoamEvent>>(emptyList())
    val roamRssi = MutableStateFlow<List<Int>>(emptyList())

    // ---- testy ----
    val pingStats = MutableStateFlow(PingStats())
    val pingError = MutableStateFlow<String?>(null)
    val dl = MutableStateFlow(SpeedState())
    val ul = MutableStateFlow(SpeedState())

    init {
        val session = surveyStore.load()
        surveyPlanUri = session.planUri
        points.value = session.points
        session.planUri?.let { uri ->
            viewModelScope.launch(Dispatchers.IO) { decodePlan(Uri.parse(uri), replaceSession = false) }
        }
    }

    private var pollJob: Job? = null
    private var scanJob: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var pingJob: Job? = null
    private var dlJob: Job? = null
    private var ulJob: Job? = null
    private var previousBssid: String? = null

    // ------------------------------------------------------------------
    // zivotni cyklus: mereni bezi jen kdyz je aplikace na popredi
    // ------------------------------------------------------------------
    fun setActive(on: Boolean) {
        if (on) {
            if (pollJob?.isActive == true) return
            receiver = repo.register { success ->
                if (success) lastScanMs.value = System.currentTimeMillis()
                refresh()
            }
            refresh()
            pollJob = viewModelScope.launch(Dispatchers.Default) {
                while (isActive) {
                    pollOnce()
                    delay(if (roamRunning.value) 300L else 1000L)
                }
            }
            scanJob = viewModelScope.launch(Dispatchers.Default) {
                while (isActive) {
                    repo.startScan()
                    // Android 9+ standardne omezuje skenovani na 4 pokusy za 2 minuty.
                    delay(35_000L)
                }
            }
        } else {
            pollJob?.cancel()
            scanJob?.cancel()
            pollJob = null
            scanJob = null
            receiver?.let { repo.unregister(it) }
            receiver = null
            // Při návratu z pozadí nepovažovat prodlevu za přerušení roamingu.
            roamRunning.value = false
        }
    }

    fun refresh() {
        aps.value = repo.scanResults().sortedByDescending { it.rssi }
        // cas potvrzene aktualizace nastavuje pouze broadcast od Androidu
    }

    fun scanNow(): Boolean {
        val ok = repo.startScan()
        refresh()
        return ok
    }

    val wifiEnabled: Boolean get() = repo.isEnabled

    private fun pollOnce() {
        val c = repo.connection()
        conn.value = c
        if (c != null) {
            if (previousBssid != c.bssid) rssiHist.value = emptyList()
            previousBssid = c.bssid
            rssiHist.value = (rssiHist.value + c.rssi).takeLast(90)
            gateway.value = repo.gateway()
        } else {
            previousBssid = null
            rssiHist.value = emptyList()
            gateway.value = null
        }
        if (roamRunning.value) {
            if (c != null) roamRssi.value = (roamRssi.value + c.rssi).takeLast(300)
            trackRoam(c, System.currentTimeMillis())
        }
    }

    // ------------------------------------------------------------------
    // Walking test
    // ------------------------------------------------------------------
    fun loadPlan(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) { decodePlan(uri, replaceSession = true) }
    }

    private fun decodePlan(uri: Uri, replaceSession: Boolean) {
        try {
            val cr = getApplication<Application>().contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                walkStatus.value = "Soubor neobsahuje platný obrázek."
                return
            }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) {
                walkStatus.value = "Půdorys nelze načíst."
                return
            }
            plan.value = bmp.asImageBitmap()
            if (replaceSession) {
                // Novy pudorys nema shodne souřadnice se starým měřením.
                points.value = emptyList()
                surveyPlanUri = uri.toString()
                persistWalking()
                walkStatus.value = "Nový půdorys načten. Měřicí body byly vynulovány."
            } else {
                walkStatus.value = "Obnoveno poslední měření (${points.value.size} bodů)."
            }
        } catch (e: Exception) {
            walkStatus.value = if (replaceSession) "Půdorys se nepodařilo načíst." else
                "Body byly obnoveny, ale obrázek chybí. Vyberte půdorys znovu."
        }
    }

    private fun persistWalking() {
        val snapshot = SurveyStore.Session(surveyPlanUri, points.value.toList())
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250L) // spojit několik rychlých změn do jednoho zápisu
            runCatching { surveyStore.save(snapshot) }
                .onFailure { walkStatus.value = "Upozornění: měření se nepodařilo uložit." }
        }
    }

    /** Pridani mericiho bodu na pozici (x,y) = 0..1. Vraci false, kdyz nejsme pripojeni. */
    fun addPoint(x: Float, y: Float): Boolean {
        val c = conn.value ?: return false
        val h = rssiHist.value.takeLast(3)
        if (h.isEmpty()) return false
        val r = h.average().roundToInt()
        points.value = points.value + WPoint(
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f),
            rssi = r,
            bssid = c.bssid,
            ssid = c.ssid,
            freq = c.freq,
            ts = System.currentTimeMillis(),
        )
        persistWalking()
        return true
    }

    fun undoPoint() {
        if (points.value.isNotEmpty()) {
            points.value = points.value.dropLast(1)
            persistWalking()
        }
    }

    fun clearPoints() {
        points.value = emptyList()
        persistWalking()
    }

    fun exportCsv(uri: Uri) {
        writeCsv(uri, CsvExport.walking(points.value))
    }

    fun exportScannerCsv(uri: Uri) {
        writeCsv(uri, CsvExport.scanner(aps.value, lastScanMs.value))
    }

    fun exportRoamCsv(uri: Uri) {
        writeCsv(uri, CsvExport.roaming(roamEvents.value))
    }

    private fun writeCsv(uri: Uri, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cr = getApplication<Application>().contentResolver
                val out = cr.openOutputStream(uri) ?: error("Nepodařilo se otevřít výstupní soubor.")
                out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                exportStatus.value = "CSV úspěšně uloženo."
            } catch (e: Exception) {
                exportStatus.value = "Export CSV selhal: ${e.message ?: "chyba zápisu"}"
            }
        }
    }

    // ------------------------------------------------------------------
    // Roaming test
    // ------------------------------------------------------------------
    private var lastBssid: String? = null
    private var lastSsid: String? = null
    private var lastRssi = 0
    private var lastFreq = 0
    private var lostAt = 0L

    fun startRoam() {
        roamEvents.value = emptyList()
        roamRssi.value = emptyList()
        val c = conn.value
        lastBssid = c?.bssid?.takeIf { validBssid(it) }
        lastSsid = c?.ssid
        lastRssi = c?.rssi ?: 0
        lastFreq = c?.freq ?: 0
        lostAt = 0L
        roamRunning.value = true
    }

    fun stopRoam() {
        roamRunning.value = false
    }

    private fun trackRoam(c: Conn?, now: Long) {
        if (c == null) {
            if (lostAt == 0L) lostAt = now
            return
        }
        val b = c.bssid
        val prev = lastBssid
        if (prev != null && validBssid(b) && prev != b && c.ssid == lastSsid) {
            val gap = if (lostAt > 0L) now - lostAt else 0L
            roamEvents.value = roamEvents.value + RoamEvent(
                ts = now,
                fromBssid = prev,
                toBssid = b,
                fromRssi = lastRssi,
                toRssi = c.rssi,
                fromFreq = lastFreq,
                toFreq = c.freq,
                gapMs = gap,
            )
        }
        if (validBssid(b)) lastBssid = b
        lastSsid = c.ssid
        lastRssi = c.rssi
        lastFreq = c.freq
        lostAt = 0L
    }

    // ------------------------------------------------------------------
    // Ping
    // ------------------------------------------------------------------
    fun startPing(host: String) {
        val h = host.trim()
        if (!Regex("^[A-Za-z0-9._:-]+$").matches(h) || h.startsWith("-")) {
            pingError.value = "Zadejte platnou IP adresu nebo název hostitele."
            return
        }
        if (!repo.isWifiNetworkActive) {
            pingError.value = "Test vyžaduje aktivní připojení přes Wi-Fi."
            return
        }
        pingError.value = null
        pingJob?.cancel()
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            var sent = 0
            var recv = 0
            var sum = 0.0
            var mn = Double.MAX_VALUE
            var mx = 0.0
            var jsum = 0.0
            var prev: Double? = null
            val last = ArrayDeque<Double?>()
            pingStats.value = PingStats(running = true)
            try {
                while (isActive) {
                    val t = pingOnce(h)
                    sent++
                    if (t != null) {
                        recv++
                        sum += t
                        mn = min(mn, t)
                        mx = max(mx, t)
                        prev?.let { jsum += abs(t - it) }
                        prev = t
                    }
                    last.addLast(t)
                    if (last.size > 60) last.removeFirst()
                    pingStats.value = PingStats(
                        running = true,
                        sent = sent,
                        recv = recv,
                        min = if (recv > 0) mn else 0.0,
                        avg = if (recv > 0) sum / recv else 0.0,
                        max = mx,
                        jitter = if (recv > 1) jsum / (recv - 1) else 0.0,
                        last = last.toList(),
                    )
                    delay(500L)
                }
            } finally {
                pingStats.value = pingStats.value.copy(running = false)
            }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
    }

    private fun pingOnce(host: String): Double? {
        var p: Process? = null
        return try {
            p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", host)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            Regex("time=([0-9.]+)").find(out)?.groupValues?.get(1)?.toDouble()
        } catch (e: Exception) {
            null
        } finally {
            p?.destroy()
        }
    }

    // ------------------------------------------------------------------
    // Test rychlosti: HTTP download a TCP upload (kompatibilni s iperf2 -s)
    // ------------------------------------------------------------------
    fun startDownload(url: String, maxSec: Int) {
        if (!repo.isWifiNetworkActive) {
            dl.value = SpeedState(error = "Test vyžaduje aktivní připojení přes Wi-Fi.")
            return
        }
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            dl.value = SpeedState(error = "URL musí začínat http:// nebo https://")
            return
        }
        dlJob?.cancel()
        dlJob = viewModelScope.launch(Dispatchers.IO) {
            dl.value = SpeedState(running = true)
            var c: HttpURLConnection? = null
            try {
                c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.useCaches = false
                c.connect()
                val buf = ByteArray(64 * 1024)
                c.inputStream.use { ins ->
                    runMeter(dl, maxSec) { ins.read(buf) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                dl.value = SpeedState(error = e.message ?: e.javaClass.simpleName)
            } finally {
                c?.disconnect()
            }
        }
    }

    fun startUpload(host: String, port: Int, maxSec: Int) {
        if (!repo.isWifiNetworkActive) {
            ul.value = SpeedState(error = "Test vyžaduje aktivní připojení přes Wi-Fi.")
            return
        }
        if (port !in 1..65535) {
            ul.value = SpeedState(error = "Port musí být v rozsahu 1–65535.")
            return
        }
        val h = host.trim()
        if (h.isEmpty()) {
            ul.value = SpeedState(error = "Zadejte adresu serveru")
            return
        }
        ulJob?.cancel()
        ulJob = viewModelScope.launch(Dispatchers.IO) {
            ul.value = SpeedState(running = true)
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(h, port), 5000)
                    val out = s.getOutputStream()
                    val buf = ByteArray(128 * 1024)
                    runMeter(ul, maxSec) {
                        out.write(buf)
                        buf.size
                    }
                    out.flush()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                ul.value = SpeedState(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun stopSpeed() {
        dlJob?.cancel()
        ulJob?.cancel()
    }

    /** Spolecna smycka mereni; step() vraci pocet preslanych bajtu nebo -1 pri konci. */
    private fun CoroutineScope.runMeter(
        target: MutableStateFlow<SpeedState>,
        maxSec: Int,
        step: () -> Int,
    ) {
        val t0 = System.nanoTime()
        var lastT = t0
        var lastB = 0L
        var total = 0L
        val samples = mutableListOf<Double>()
        while (isActive) {
            val n = step()
            if (n < 0) break
            total += n
            val now = System.nanoTime()
            if (now - lastT >= 500_000_000L) {
                val m = (total - lastB) * 8.0 / ((now - lastT) / 1e9) / 1e6
                samples.add(m)
                lastT = now
                lastB = total
                target.value = SpeedState(
                    running = true,
                    mbps = m,
                    bytes = total,
                    seconds = (now - t0) / 1e9,
                    samples = samples.toList(),
                )
            }
            if ((now - t0) / 1e9 >= maxSec) break
        }
        val sec = max((System.nanoTime() - t0) / 1e9, 0.001)
        if (!isActive) {
            target.value = target.value.copy(running = false, done = false)
            return
        }
        target.value = SpeedState(
            running = false,
            mbps = total * 8.0 / sec / 1e6,
            bytes = total,
            seconds = sec,
            samples = samples.toList(),
            done = true,
        )
    }

    override fun onCleared() {
        setActive(false)
        // pokud se obrazovka zavírá během debounce, uložit aktuální snapshot
        saveJob?.cancel()
        runCatching {
            surveyStore.save(SurveyStore.Session(surveyPlanUri, points.value.toList()))
        }
        super.onCleared()
    }
}
