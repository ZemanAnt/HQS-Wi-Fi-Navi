package cz.hqsnet.wifinavi

/** Bez Android SDK: kotlinc Model.kt NetCalc.kt CsvExport.kt tests/ModelAndCsvTest.kt -include-runtime -d /tmp/test.jar */
fun main() {
    check(freqToChannel(2412) == 1)
    check(freqToChannel(2437) == 6)
    check(freqToChannel(2462) == 11)
    check(freqToChannel(5180) == 36)
    check(freqToChannel(5975) == 5)
    check(freqToChannel(5935) == 2)
    check(securityOf("[RSN-PSK-CCMP][ESS]") == "WPA2")
    check(securityOf("[RSN-SAE-CCMP][ESS]") == "WPA3")
    check(securityOf("[RSN-PSK+SAE-CCMP][ESS]") == "WPA2/WPA3")
    check(!validBssid("02:00:00:00:00:00"))
    check(validBssid("AA:BB:CC:DD:EE:FF"))
    val malicious = WPoint(0.22f, 0.31f, -67, "01:02:03:04:05:06", "=HYPERLINK(\"x\")", 2412, 1700000000000)
    val csv = CsvExport.walking(listOf(malicious))
    check(csv.first() == '\uFEFF')
    check(csv.contains("\"-67\""))
    check(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""))
    val a = Ap("Síť;\"A", "AB:CD:EF:01:02:03", -49, 5180, 5210, 80, "[RSN-PSK-CCMP]")
    val scan = CsvExport.scanner(listOf(a), 1700000000000)
    check(scan.contains("Síť;\"\"A"))
    check(scan.contains("\"-49\""))
    check(scan.contains("\"80\""))
    val roam = CsvExport.roaming(listOf(RoamEvent(1700000000000, "a", "b", -71, -50, 2412, 5180, 0)))
    check(roam.contains("\"\"\n")) // bez změřeného výpadku zůstane pole prázdné

    // ---- v1.2.0: NetCalc ----
    check(NetCalc.parsePingTime("64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=12.3 ms") == 12.3)
    val hop = NetCalc.parseTrace("From 192.168.1.1 icmp_seq=1 Time to live exceeded")!!
    check(hop.addr == "192.168.1.1" && !hop.reached)
    val dst = NetCalc.parseTrace("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=9.5 ms")!!
    check(dst.reached && dst.ms == 9.5)
    check(NetCalc.parseTrace("Request timeout") == null)
    val hosts = NetCalc.subnetHosts("192.168.1.10", 24)
    check(hosts.size == 253 && hosts.first() == "192.168.1.1" && "192.168.1.10" !in hosts)
    check(NetCalc.subnetHosts("10.0.5.9", 16).size == 253) // omezeno na /24
    check(NetCalc.subnetHosts("10.0.0.5", 30).size == 1)
    check(NetCalc.subnetHosts("10.0.0.5", 32).isEmpty())
    check(NetCalc.rangeLabel("192.168.1.10", 24) == "192.168.1.0/24")
    check(NetCalc.parsePorts("22, 80,8000-8002") == listOf(22, 80, 8000, 8001, 8002))
    check(NetCalc.parsePorts("0") == null && NetCalc.parsePorts("70000") == null)
    check(NetCalc.parsePorts("90-80") == null && NetCalc.parsePorts("abc") == null)
    check(NetCalc.parsePorts("1-1024")!!.size == 1024)
    // 1 GB po 100 Mbit/s při 100 % = 80 s
    check(Math.abs(NetCalc.transferSeconds(1e9, 100.0, 100.0) - 80.0) < 1e-9)
    check(Math.abs(NetCalc.requiredMbps(50.0, 5.0, 60.0, 20.0) - 180.0) < 1e-9)
    check(NetCalc.recommendedLink(180.0) == "1 Gbit/s")
    // 16 kamer x 4 Mbit/s = 64 Mbit/s = 8 MB/s -> 691,2 GB za den
    check(Math.abs(NetCalc.cctvStorageGb(64.0, 1.0) - 691.2) < 1e-6)
    check(Math.abs(NetCalc.poeTotalPse(listOf(0, 0, 2, 1, 0, 0, 0, 0), 1, 10.0) - 70.8) < 1e-9)
    val s = listOf(
        RoamPingSample(1000, 5.0), RoamPingSample(1200, null), RoamPingSample(1400, 15.0),
        RoamPingSample(9000, 1.0),
    )
    val w = NetCalc.roamWindow(s, 1300)
    check(w.sent == 3 && w.lost == 1 && w.maxMs == 15.0 && w.avgMs == 10.0)
    val me = Conn("Firma", "AA:00:00:00:00:01", -50, 2437, 144, 144, 144)
    val ap1 = Ap("Firma", "AA:00:00:00:00:01", -50, 2437, 2437, 20, "[RSN-PSK-CCMP]")
    val ap2 = Ap("Soused", "BB:00:00:00:00:02", -60, 2437, 2437, 20, "[RSN-PSK-CCMP]")
    val ap3 = Ap("Firma", "AA:00:00:00:00:03", -70, 2437, 2437, 20, "[RSN-PSK-CCMP]")
    val ap4 = Ap("Daleko", "CC:00:00:00:00:04", -60, 2412, 2412, 20, "[RSN-PSK-CCMP]") // ch1 vs ch6: bez prekryvu
    val ap5 = Ap("5G", "DD:00:00:00:00:05", -40, 5180, 5180, 20, "[RSN-PSK-CCMP]")
    val inter = NetCalc.interference(me, listOf(ap1, ap2, ap3, ap4, ap5))
    check(inter.overlapping == 1 && inter.strongest == -60 && inter.sameSsid == 1)
    check(NetCalc.rateInterference(inter) == TestLevel.WARN)
    check(NetCalc.rateSecurity("WEP") == TestLevel.FAIL && NetCalc.rateSecurity("WPA3") == TestLevel.OK)
    check(NetCalc.rateGateway(0.0, 3.0, 1.0) == TestLevel.OK)
    check(NetCalc.rateGateway(2.0, 3.0, 1.0) == TestLevel.WARN)
    check(NetCalc.rateGateway(10.0, 3.0, 1.0) == TestLevel.FAIL)
    check(NetCalc.verdict(listOf(TestStep("a", TestLevel.OK, ""), TestStep("b", TestLevel.FAIL, ""))) == "Nalezeny problémy")
    val roam2 = CsvExport.roaming(listOf(RoamEvent(1300, "a", "b", -71, -50, 2412, 5180, 0)), s)
    check(roam2.contains("\"1\"") && roam2.contains("\"15.0\""))
    println("OK: model + CSV + NetCalc")
}
