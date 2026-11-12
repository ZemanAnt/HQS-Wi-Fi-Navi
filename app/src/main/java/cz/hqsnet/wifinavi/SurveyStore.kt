package cz.hqsnet.wifinavi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Soukroma lokalni zaloha posledniho Walking Testu (zadny cloud). */
class SurveyStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "walking_session.json")

    data class Session(val planUri: String?, val points: List<WPoint>)

    fun load(): Session {
        if (!file.exists()) return Session(null, emptyList())
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val arr = json.optJSONArray("points") ?: JSONArray()
            val points = (0 until arr.length()).mapNotNull { index ->
                runCatching {
                    val p = arr.getJSONObject(index)
                    WPoint(
                        x = p.getDouble("x").toFloat().coerceIn(0f, 1f),
                        y = p.getDouble("y").toFloat().coerceIn(0f, 1f),
                        rssi = p.getInt("rssi"),
                        bssid = p.optString("bssid"),
                        ssid = p.optString("ssid"),
                        freq = p.optInt("freq"),
                        ts = p.optLong("ts"),
                    )
                }.getOrNull()
            }
            Session(json.optString("planUri").ifBlank { null }, points)
        }.getOrElse { Session(null, emptyList()) }
    }

    @Synchronized
    fun save(session: Session) {
        val root = JSONObject().apply {
            put("version", 1)
            put("planUri", session.planUri ?: "")
            put("points", JSONArray().apply {
                session.points.forEach { p ->
                    put(JSONObject().apply {
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                        put("rssi", p.rssi)
                        put("bssid", p.bssid)
                        put("ssid", p.ssid)
                        put("freq", p.freq)
                        put("ts", p.ts)
                    })
                }
            })
        }
        val temp = File(file.parentFile, "walking_session.json.tmp")
        temp.writeText(root.toString(), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeText(root.toString(), Charsets.UTF_8)
            temp.delete()
        }
    }
}
