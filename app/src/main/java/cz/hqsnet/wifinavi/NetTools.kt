package cz.hqsnet.wifinavi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/** Sitove nastroje bez zavislosti na Android UI. Vse blokujici bezi na Dispatchers.IO. */
object NetTools {
    const val CF_DOWN = "https://speed.cloudflare.com/__down?bytes=50000000"
    const val CF_UP = "https://speed.cloudflare.com/__up"

    private val hostRe = Regex("^[A-Za-z0-9._:-]+$")
    fun validHost(h: String): Boolean = hostRe.matches(h) && !h.startsWith("-")

    // ---------------- ping ----------------

    /** Surovy vystup systemoveho `ping -c 1`; host musi projit validHost(). */
    fun pingRaw(host: String, ttl: Int? = null, waitSec: Int = 1): String? {
        var p: Process? = null
        return try {
            val cmd = mutableListOf("/system/bin/ping", "-c", "1", "-W", waitSec.toString())
            if (ttl != null) {
                cmd.add("-t")
                cmd.add(ttl.toString())
            }
            cmd.add(host)
            p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        } catch (e: Exception) {
            null
        } finally {
            p?.destroy()
        }
    }

    fun pingOnce(host: String): Double? = pingRaw(host)?.let { NetCalc.parsePingTime(it) }

    // ---------------- jednoduche dotazy ----------------

    fun tcpLatencyMs(host: String, port: Int, timeoutMs: Int = 2000): Double? = try {
        val t0 = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        (System.nanoTime() - t0) / 1e6
    } catch (e: Exception) {
        null
    }

    /** Doba prekladu jmena (ms) a adresa; null pri chybe. */
    fun dnsLookup(name: String): Pair<Double, String>? = try {
        val t0 = System.nanoTime()
        val a = InetAddress.getByName(name)
        ((System.nanoTime() - t0) / 1e6) to (a.hostAddress ?: "")
    } catch (e: Exception) {
        null
    }

    private fun httpGetText(u: String, timeoutMs: Int = 5000): String {
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.useCaches = false
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().take(8192).toByteArray().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    data class PublicIp(val ip: String, val country: String?)

    fun publicIp(): PublicIp {
        try {
            val t = httpGetText("https://1.1.1.1/cdn-cgi/trace")
            val kv = t.lines().mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i) to it.substring(i + 1).trim() else null
            }.toMap()
            val ip = kv["ip"]
            if (!ip.isNullOrBlank()) return PublicIp(ip, kv["loc"]?.takeIf { it.isNotBlank() })
        } catch (e: Exception) {
            // zkusit zalozni sluzbu
        }
        val ip = httpGetText("https://api.ipify.org").trim()
        if (ip.isEmpty() || ip.length > 45) error("Neplatná odpověď služby")
        return PublicIp(ip, null)
    }

    // ---------------- traceroute ----------------

    suspend fun traceroute(host: String, maxHops: Int, onHop: (TraceHop) -> Unit) =
        withContext(Dispatchers.IO) {
            val target = InetAddress.getAllByName(host).filterIsInstance<Inet4Address>().firstOrNull()
                ?: error("Traceroute podporuje jen IPv4 adresy.")
            val ip = target.hostAddress ?: error("Adresu se nepodařilo přeložit.")
            var silent = 0
            for (ttl in 1..maxHops) {
                if (!isActive) break
                val t0 = System.nanoTime()
                val raw = pingRaw(ip, ttl, 2)
                val wall = (System.nanoTime() - t0) / 1e6
                val r = raw?.let { NetCalc.parseTrace(it) }
                if (r == null) {
                    silent++
                    onHop(TraceHop(ttl, null, null, false))
                    if (silent >= 6) break
                } else {
                    silent = 0
                    onHop(TraceHop(ttl, r.addr, r.ms ?: wall, r.reached))
                    if (r.reached) break
                }
            }
        }

    // ---------------- paralelni mereni propustnosti ----------------

    /**
     * Spusti [streams] pracovniku, kazdy cte/pise a pricita bajty do sdileneho citace.
     * Pracovnik musi pravidelne kontrolovat `stop` a registrovat sokety do `closeables`.
     */
    suspend fun runParallel(
        state: MutableStateFlow<SpeedState>,
        seconds: Int,
        streams: Int,
        worker: (AtomicBoolean, AtomicLong, MutableCollection<Closeable>) -> Unit,
    ) = coroutineScope {
        val stop = AtomicBoolean(false)
        val counter = AtomicLong(0)
        val closeables = ConcurrentLinkedQueue<Closeable>()
        val firstError = AtomicReference<String?>(null)
        val jobs = (1..streams).map {
            async(Dispatchers.IO) {
                try {
                    worker(stop, counter, closeables)
                } catch (e: Exception) {
                    if (!stop.get()) firstError.compareAndSet(null, e.message ?: e.javaClass.simpleName)
                }
            }
        }
        val t0 = System.nanoTime()
        var lastT = t0
        var lastB = 0L
        val samples = mutableListOf<Double>()
        try {
            while (true) {
                delay(500L)
                val now = System.nanoTime()
                val total = counter.get()
                val m = (total - lastB) * 8.0 / ((now - lastT) / 1e9) / 1e6
                samples.add(m)
                lastT = now
                lastB = total
                state.value = SpeedState(
                    running = true, mbps = m, bytes = total,
                    seconds = (now - t0) / 1e9, samples = samples.toList(),
                )
                if ((now - t0) / 1e9 >= seconds || jobs.all { it.isCompleted }) break
            }
        } finally {
            stop.set(true)
            closeables.forEach { runCatching { it.close() } }
        }
        jobs.awaitAll()
        val sec = max((System.nanoTime() - t0) / 1e9, 0.001)
        val total = counter.get()
        val err = firstError.get()
        state.value = if (total == 0L && err != null) {
            SpeedState(error = err)
        } else {
            SpeedState(
                mbps = total * 8.0 / sec / 1e6, bytes = total, seconds = sec,
                samples = samples.toList(), done = true,
            )
        }
    }

    fun httpDownloadWorker(url: String): (AtomicBoolean, AtomicLong, MutableCollection<Closeable>) -> Unit =
        { stop, counter, closeables ->
            val buf = ByteArray(64 * 1024)
            while (!stop.get()) {
                val c = URL(url).openConnection() as HttpURLConnection
                closeables.add(Closeable { c.disconnect() })
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.useCaches = false
                c.inputStream.use { ins ->
                    while (!stop.get()) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        counter.addAndGet(n.toLong())
                    }
                }
                c.disconnect()
            }
        }

    fun httpUploadWorker(url: String): (AtomicBoolean, AtomicLong, MutableCollection<Closeable>) -> Unit =
        { stop, counter, closeables ->
            val chunk = ByteArray(64 * 1024)
            val size = 8L * 1024 * 1024
            while (!stop.get()) {
                val c = URL(url).openConnection() as HttpURLConnection
                closeables.add(Closeable { c.disconnect() })
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 5000
                c.readTimeout = 15000
                c.useCaches = false
                c.setFixedLengthStreamingMode(size)
                c.outputStream.use { out ->
                    var sent = 0L
                    while (sent < size && !stop.get()) {
                        val n = minOf(chunk.size.toLong(), size - sent).toInt()
                        out.write(chunk, 0, n)
                        sent += n
                        counter.addAndGet(n.toLong())
                    }
                }
                if (!stop.get()) runCatching { c.responseCode }
                c.disconnect()
            }
        }

    /** TCP odesilani na iperf2 server (`iperf -s`) nebo HQS server; jeden soket na pracovnika. */
    fun tcpUploadWorker(host: String, port: Int): (AtomicBoolean, AtomicLong, MutableCollection<Closeable>) -> Unit =
        { stop, counter, closeables ->
            val s = Socket()
            closeables.add(s)
            s.connect(InetSocketAddress(host, port), 5000)
            val out = s.getOutputStream()
            val buf = ByteArray(128 * 1024)
            while (!stop.get()) {
                out.write(buf)
                counter.addAndGet(buf.size.toLong())
            }
        }

    // ---------------- skenery ----------------

    private val probePorts = listOf(80, 443, 22, 445, 139, 8080, 53, 554, 8443, 62078)

    /** 1 = otevreno, 2 = odmitnuto (hostitel zije), 0 = bez odpovedi. */
    private fun tcpProbe(ip: String, port: Int, timeoutMs: Int): Int = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
        1
    } catch (e: ConnectException) {
        val m = e.message.orEmpty()
        if (m.contains("refused", ignoreCase = true) || m.contains("ECONNREFUSED")) 2 else 0
    } catch (e: Exception) {
        0
    }

    private fun probeHost(ip: String): HostInfo? {
        val t0 = System.nanoTime()
        var ms: Double? = null
        var alive = false
        try {
            if (InetAddress.getByName(ip).isReachable(400)) {
                alive = true
                ms = (System.nanoTime() - t0) / 1e6
            }
        } catch (e: Exception) {
            // ICMP nemusi byt povoleno
        }
        val open = mutableListOf<Int>()
        for (p in probePorts) {
            val t1 = System.nanoTime()
            when (tcpProbe(ip, p, 250)) {
                1 -> {
                    alive = true
                    open.add(p)
                    if (ms == null) ms = (System.nanoTime() - t1) / 1e6
                }
                2 -> {
                    alive = true
                    if (ms == null) ms = (System.nanoTime() - t1) / 1e6
                }
            }
        }
        return if (alive) HostInfo(ip, ms, open) else null
    }

    private fun readArp(): Map<String, String> = try {
        File("/proc/net/arp").readLines().drop(1).mapNotNull { line ->
            val f = line.trim().split(Regex("\\s+"))
            if (f.size >= 4 && f[3] != "00:00:00:00:00:00") f[0] to f[3].uppercase() else null
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    suspend fun scanHosts(
        ips: List<String>,
        onFound: (HostInfo) -> Unit,
        onProgress: () -> Unit,
    ): List<HostInfo> = coroutineScope {
        val sem = Semaphore(48)
        val found = ips.map { ip ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val h = probeHost(ip)
                    if (h != null) onFound(h)
                    onProgress()
                    h
                }
            }
        }.awaitAll().filterNotNull()
        val arp = withContext(Dispatchers.IO) { readArp() }
        found.map { h ->
            async(Dispatchers.IO) {
                val name = withTimeoutOrNull(1500L) {
                    runCatching { InetAddress.getByName(h.ip).canonicalHostName }.getOrNull()
                }?.takeIf { it != h.ip }
                h.copy(name = name, mac = arp[h.ip])
            }
        }.awaitAll().sortedBy { NetCalc.ipToLong(it.ip) ?: 0L }
    }

    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        onOpen: (Int) -> Unit,
        onProgress: () -> Unit,
    ) = coroutineScope {
        val ip = withContext(Dispatchers.IO) {
            InetAddress.getByName(host).hostAddress ?: error("Adresu se nepodařilo přeložit.")
        }
        val sem = Semaphore(150)
        ports.map { p ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    if (tcpProbe(ip, p, timeoutMs) == 1) onOpen(p)
                    onProgress()
                }
            }
        }.awaitAll()
        Unit
    }
}
