package cz.hqsnet.wifinavi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stav a logika sitovych nastroju (integrovany test, speedtest, iPerf, traceroute, skenery). */
class ToolsVM(app: Application) : AndroidViewModel(app) {
    private val repo = WifiRepo(app)

    val publicIp = MutableStateFlow(TextState())
    val trace = MutableStateFlow(TraceState())
    val ipScan = MutableStateFlow(IpScanState())
    val portScan = MutableStateFlow(PortScanState())
    val integrated = MutableStateFlow(IntegratedState())
    val inetLat = MutableStateFlow<String?>(null)
    val inetDl = MutableStateFlow(SpeedState())
    val inetUl = MutableStateFlow(SpeedState())
    val iperf = MutableStateFlow(SpeedState())

    private var ipJob: Job? = null
    private var traceJob: Job? = null
    private var ipScanJob: Job? = null
    private var portJob: Job? = null
    private var inetJob: Job? = null
    private var iperfJob: Job? = null
    private var integratedJob: Job? = null

    private val noWifi = "Test vyžaduje aktivní připojení přes Wi-Fi."

    // ---------------- veřejná IP ----------------
    fun lookupPublicIp() {
        ipJob?.cancel()
        publicIp.value = TextState(busy = true)
        ipJob = viewModelScope.launch(Dispatchers.IO) {
            publicIp.value = try {
                val r = NetTools.publicIp()
                TextState(text = r.ip + (r.country?.let { " · $it" } ?: ""))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TextState(text = "Nepodařilo se zjistit: ${e.message ?: e.javaClass.simpleName}", error = true)
            }
        }
    }

    // ---------------- traceroute ----------------
    fun startTrace(host: String) {
        val h = host.trim()
        if (!NetTools.validHost(h)) {
            trace.value = TraceState(error = "Zadejte platnou IP adresu nebo název hostitele.")
            return
        }
        traceJob?.cancel()
        traceJob = viewModelScope.launch {
            trace.value = TraceState(running = true, target = h)
            try {
                NetTools.traceroute(h, 30) { hop -> trace.update { it.copy(hops = it.hops + hop) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trace.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            } finally {
                trace.update { it.copy(running = false) }
            }
        }
    }

    fun stopTrace() {
        traceJob?.cancel()
    }

    // ---------------- IP scanner ----------------
    fun startIpScan() {
        if (!repo.isWifiNetworkActive) {
            ipScan.value = IpScanState(error = "Skenování vyžaduje aktivní připojení přes Wi-Fi.")
            return
        }
        val local = repo.localIpv4()
        val ips = local?.let { NetCalc.subnetHosts(it.first, it.second) }.orEmpty()
        if (local == null || ips.isEmpty()) {
            ipScan.value = IpScanState(error = "Nepodařilo se zjistit IPv4 adresu a masku sítě.")
            return
        }
        ipScanJob?.cancel()
        ipScanJob = viewModelScope.launch {
            ipScan.value = IpScanState(
                running = true, total = ips.size, range = NetCalc.rangeLabel(local.first, local.second),
            )
            try {
                val result = NetTools.scanHosts(
                    ips,
                    onFound = { h ->
                        ipScan.update { st ->
                            st.copy(hosts = (st.hosts + h).sortedBy { NetCalc.ipToLong(it.ip) ?: 0L })
                        }
                    },
                    onProgress = { ipScan.update { it.copy(progress = it.progress + 1) } },
                )
                ipScan.update { it.copy(hosts = result, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ipScan.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            } finally {
                ipScan.update { it.copy(running = false) }
            }
        }
    }

    fun stopIpScan() {
        ipScanJob?.cancel()
    }

    // ---------------- port scanner ----------------
    fun startPortScan(host: String, spec: String) {
        val h = host.trim()
        if (!NetTools.validHost(h)) {
            portScan.value = PortScanState(error = "Zadejte platnou IP adresu nebo název hostitele.")
            return
        }
        val ports = NetCalc.parsePorts(spec)
        if (ports == null) {
            portScan.value = PortScanState(error = "Porty zadejte např. 22,80,443 nebo 1-1024.")
            return
        }
        portJob?.cancel()
        portJob = viewModelScope.launch {
            portScan.value = PortScanState(running = true, target = h, total = ports.size)
            try {
                NetTools.scanPorts(
                    h, ports, 500,
                    onOpen = { p -> portScan.update { it.copy(open = (it.open + p).sorted()) } },
                    onProgress = { portScan.update { it.copy(progress = it.progress + 1) } },
                )
                portScan.update { it.copy(done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                portScan.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            } finally {
                portScan.update { it.copy(running = false) }
            }
        }
    }

    fun stopPortScan() {
        portJob?.cancel()
    }

    // ---------------- internetový speedtest ----------------
    fun startInternetSpeed() {
        if (!repo.isWifiNetworkActive) {
            inetDl.value = SpeedState(error = noWifi)
            return
        }
        inetJob?.cancel()
        inetJob = viewModelScope.launch(Dispatchers.IO) {
            inetLat.value = null
            inetDl.value = SpeedState(running = true)
            inetUl.value = SpeedState()
            try {
                val lat = (1..5).map { NetTools.tcpLatencyMs("1.1.1.1", 443) }
                val ok = lat.filterNotNull()
                inetLat.value = if (ok.isEmpty()) "bez odpovědi" else
                    "${fmt1(NetCalc.median(ok))} ms (jitter ${fmt1(NetCalc.jitter(ok))} ms)"
                NetTools.runParallel(inetDl, 8, 4, NetTools.httpDownloadWorker(NetTools.CF_DOWN))
                if (inetDl.value.error != null) return@launch
                inetUl.value = SpeedState(running = true)
                NetTools.runParallel(inetUl, 8, 4, NetTools.httpUploadWorker(NetTools.CF_UP))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                inetDl.value = SpeedState(error = e.message ?: e.javaClass.simpleName)
            } finally {
                inetDl.update { it.copy(running = false) }
                inetUl.update { it.copy(running = false) }
            }
        }
    }

    // ---------------- iPerf klient (TCP, více vláken) ----------------
    fun startIperf(host: String, port: Int, streams: Int, seconds: Int) {
        if (!repo.isWifiNetworkActive) {
            iperf.value = SpeedState(error = noWifi)
            return
        }
        val h = host.trim()
        if (!NetTools.validHost(h)) {
            iperf.value = SpeedState(error = "Zadejte platnou adresu serveru.")
            return
        }
        if (port !in 1..65535) {
            iperf.value = SpeedState(error = "Port musí být v rozsahu 1–65535.")
            return
        }
        iperfJob?.cancel()
        iperfJob = viewModelScope.launch(Dispatchers.IO) {
            iperf.value = SpeedState(running = true)
            try {
                NetTools.runParallel(
                    iperf, seconds.coerceIn(3, 120), streams.coerceIn(1, 16),
                    NetTools.tcpUploadWorker(h, port),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                iperf.value = SpeedState(error = e.message ?: e.javaClass.simpleName)
            } finally {
                iperf.update { it.copy(running = false) }
            }
        }
    }

    fun stopSpeed() {
        inetJob?.cancel()
        iperfJob?.cancel()
    }

    // ---------------- Wi-Fi integrovaný test ----------------
    fun stopIntegrated() {
        integratedJob?.cancel()
    }

    fun runIntegrated(c: Conn?, aps: List<Ap>, gateway: String?) {
        if (c == null || !repo.isWifiNetworkActive) {
            integrated.value = IntegratedState(error = "Nejste připojeni k Wi-Fi.")
            return
        }
        integratedJob?.cancel()
        integratedJob = viewModelScope.launch(Dispatchers.IO) {
            val steps = mutableListOf<TestStep>()
            fun stage(s: String) {
                integrated.value = IntegratedState(running = true, stage = s, steps = steps.toList())
            }
            fun add(name: String, level: TestLevel, detail: String) {
                steps.add(TestStep(name, level, detail))
                integrated.value = IntegratedState(running = true, stage = "", steps = steps.toList())
            }
            try {
                // 1) připojení
                stage("Připojení")
                add(
                    "Signál", NetCalc.rateRssi(c.rssi),
                    "${c.ssid} · ${c.band} · kanál ${c.channel} · ${c.rssi} dBm (${rssiLabel(c.rssi)}) · " +
                        "link ${c.linkMbps} Mbit/s",
                )

                // 2) zabezpečení
                val me = aps.firstOrNull { it.bssid.equals(c.bssid, ignoreCase = true) }
                if (me == null) {
                    add("Zabezpečení", TestLevel.WARN, "AP není ve výsledcích skenu, zabezpečení nelze určit.")
                } else {
                    val lvl = NetCalc.rateSecurity(me.security)
                    val hint = when (lvl) {
                        TestLevel.FAIL -> " – nedostatečné, použijte WPA2/WPA3"
                        TestLevel.WARN -> " – otevřená síť s šifrováním OWE bez autentizace"
                        else -> ""
                    }
                    add("Zabezpečení", lvl, me.security + hint)
                }

                // 3) rušení
                stage("Rušení")
                val inter = NetCalc.interference(c, aps)
                add(
                    "Rušení / překryv", NetCalc.rateInterference(inter),
                    "${inter.overlapping} cizích AP se překrývá" +
                        (inter.strongest?.let { ", nejsilnější $it dBm" } ?: "") +
                        (if (inter.sameSsid > 0) " · ${inter.sameSsid} dalších AP stejné sítě" else ""),
                )

                // 4) brána
                stage("Ping na bránu")
                if (gateway == null) {
                    add("Brána", TestLevel.WARN, "Adresu brány se nepodařilo zjistit.")
                } else {
                    val rtts = (1..20).map { NetTools.pingOnce(gateway) }
                    val ok = rtts.filterNotNull()
                    val loss = (rtts.size - ok.size) * 100.0 / rtts.size
                    if (ok.isEmpty()) {
                        add("Brána $gateway", TestLevel.FAIL, "žádná odpověď (ztráty 100 %)")
                    } else {
                        val avg = ok.average()
                        val jit = NetCalc.jitter(ok)
                        add(
                            "Brána $gateway", NetCalc.rateGateway(loss, avg, jit),
                            "latence ${fmt1(avg)} ms (max ${fmt1(ok.max())}) · jitter ${fmt1(jit)} ms · " +
                                "ztráty ${fmt1(loss)} %",
                        )
                    }
                }

                // 5) DNS
                stage("DNS")
                val dns = NetTools.dnsLookup("one.one.one.one")
                if (dns == null) {
                    add("DNS", TestLevel.FAIL, "překlad jména selhal")
                } else {
                    add("DNS", if (dns.first > 250) TestLevel.WARN else TestLevel.OK, "${fmt1(dns.first)} ms")
                }

                // 6) internet
                stage("Internet")
                val lat = (1..5).mapNotNull { NetTools.tcpLatencyMs("1.1.1.1", 443) }
                if (lat.isEmpty()) {
                    add("Internet", TestLevel.FAIL, "bez přístupu k internetu (TCP 1.1.1.1:443)")
                    integrated.value = IntegratedState(steps = steps.toList(), done = true)
                    return@launch
                }
                val med = NetCalc.median(lat)
                add(
                    "Internet (latence)", if (med > 150) TestLevel.WARN else TestLevel.OK,
                    "${fmt1(med)} ms, jitter ${fmt1(NetCalc.jitter(lat))} ms",
                )
                stage("Veřejná IP")
                try {
                    val r = NetTools.publicIp()
                    add("Veřejná IP", TestLevel.INFO, r.ip + (r.country?.let { " · $it" } ?: ""))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    add("Veřejná IP", TestLevel.INFO, "nezjištěna")
                }

                // 7) šířka pásma
                val tmp = MutableStateFlow(SpeedState())
                stage("Stažení (5 s)")
                NetTools.runParallel(tmp, 5, 4, NetTools.httpDownloadWorker(NetTools.CF_DOWN))
                tmp.value.let {
                    if (it.error != null) add("Stažení", TestLevel.WARN, "chyba: ${it.error}")
                    else add("Stažení", if (it.mbps < 5) TestLevel.WARN else TestLevel.INFO, "${fmt1(it.mbps)} Mbit/s")
                }
                stage("Odeslání (5 s)")
                NetTools.runParallel(tmp, 5, 4, NetTools.httpUploadWorker(NetTools.CF_UP))
                tmp.value.let {
                    if (it.error != null) add("Odeslání", TestLevel.WARN, "chyba: ${it.error}")
                    else add("Odeslání", if (it.mbps < 2) TestLevel.WARN else TestLevel.INFO, "${fmt1(it.mbps)} Mbit/s")
                }
                integrated.value = IntegratedState(steps = steps.toList(), done = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                integrated.value = IntegratedState(
                    steps = steps.toList(), error = e.message ?: e.javaClass.simpleName,
                )
            } finally {
                integrated.update { it.copy(running = false, stage = "") }
            }
        }
    }
}
