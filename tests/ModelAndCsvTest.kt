package cz.hqsnet.wifinavi

/** Bez Android SDK: kotlinc Model.kt CsvExport.kt tests/ModelAndCsvTest.kt -include-runtime -d /tmp/test.jar */
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
    println("OK: model + CSV (12+ assertions)")
}
