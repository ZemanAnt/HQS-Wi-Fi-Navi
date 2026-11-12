package cz.hqsnet.wifinavi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat

/** Tenka obalka nad WifiManagerem. */
class WifiRepo(context: Context) {
    private val app: Context = context.applicationContext
    private val wm: WifiManager = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val isEnabled: Boolean get() = wm.isWifiEnabled

    /** Zabrani omylu, kdy by speedtest sel pres mobilni data misto Wi-Fi. */
    val isWifiNetworkActive: Boolean
        get() {
            return try {
                val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork
                network != null && cm.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } catch (e: Exception) {
                false
            }
        }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun scanResults(): List<Ap> = try {
        wm.scanResults.orEmpty().map { r ->
            val width = when (r.channelWidth) {
                0 -> 20
                1 -> 40
                2 -> 80
                3 -> 160
                4 -> 80 // 80+80: dva oddelene 80MHz bloky, nikoliv souvisly 160MHz kanal
                5 -> 320
                else -> 20
            }
            val center = when {
                r.channelWidth > 0 && r.centerFreq0 > 0 -> r.centerFreq0
                else -> r.frequency
            }
            Ap(
                ssid = r.SSID.orEmpty(),
                bssid = r.BSSID.orEmpty(),
                rssi = r.level,
                freq = r.frequency,
                centerFreq = center,
                widthMhz = width,
                caps = r.capabilities.orEmpty(),
                secondaryCenterFreq = if (r.channelWidth == 4 && r.centerFreq1 > 0)
                    r.centerFreq1 else null,
            )
        }
    } catch (e: SecurityException) {
        emptyList()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun startScan(): Boolean = try {
        wm.startScan()
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun connection(): Conn? = try {
        val i = wm.connectionInfo
        if (i == null || i.frequency <= 0 || i.rssi <= -127) {
            null
        } else {
            val tx = if (Build.VERSION.SDK_INT >= 29) i.txLinkSpeedMbps else -1
            val rx = if (Build.VERSION.SDK_INT >= 29) i.rxLinkSpeedMbps else -1
            Conn(
                ssid = i.ssid.orEmpty().trim('"'),
                bssid = i.bssid.orEmpty(),
                rssi = i.rssi,
                freq = i.frequency,
                linkMbps = i.linkSpeed,
                txMbps = tx,
                rxMbps = rx,
            )
        }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    fun gateway(): String? {
        val g = try {
            wm.dhcpInfo?.gateway ?: 0
        } catch (e: Exception) {
            0
        }
        if (g == 0) return null
        return "${g and 0xff}.${(g shr 8) and 0xff}.${(g shr 16) and 0xff}.${(g shr 24) and 0xff}"
    }

    fun register(onResults: (Boolean) -> Unit): BroadcastReceiver {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onResults(intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true)
            }
        }
        ContextCompat.registerReceiver(
            app,
            r,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return r
    }

    fun unregister(r: BroadcastReceiver) {
        try {
            app.unregisterReceiver(r)
        } catch (e: Exception) {
            // uz odregistrovano
        }
    }
}
