package com.juanigsrz.driverhelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class Point(val lat: Double, val lng: Double)

@Serializable
data class OfferIn(
    val platform: String,
    val price_ars: Double? = null,
    val driver: Point,
    val pickup_addr: String? = null,
    val dropoff_addr: String? = null,
    val pickup: Point? = null,
    val dropoff: Point? = null,
    val raw_text: String? = null,
    val cost_per_km: Double? = null,
    val min_ars_per_km: Double? = null,
    val min_ars_per_hr: Double? = null,
    val max_deadhead_ratio: Double? = null,
)

@Serializable
data class BackendConfig(
    val cost_per_km: Double,
    val platform_commission: Double,
    val min_ars_per_km: Double,
    val min_ars_per_hr: Double,
    val max_deadhead_ratio: Double,
)

@Serializable
data class VerdictOut(
    val decision: String,
    val gross_ars: Double,
    val net_ars: Double,
    val profit_ars: Double,
    val total_km: Double,
    val total_min: Double,
    val deadhead_km: Double,
    val deadhead_ratio: Double,
    val ars_per_km: Double,
    val ars_per_hr: Double,
    val reasons: List<String>,
)

class BackendClient(
    private val baseUrl: String,
    private val secret: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    suspend fun fetchConfig(): BackendConfig = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/config")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("backend ${conn.responseCode}")
            }
            val txt = conn.inputStream.bufferedReader().readText()
            json.decodeFromString(BackendConfig.serializer(), txt)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun evaluate(offer: OfferIn): VerdictOut = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl.trimEnd('/')}/evaluate")
        val body = json.encodeToString(offer).toByteArray()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Secret", secret)
        }
        try {
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw RuntimeException("backend ${conn.responseCode}: $err")
            }
            val txt = conn.inputStream.bufferedReader().readText()
            json.decodeFromString(VerdictOut.serializer(), txt)
        } finally {
            conn.disconnect()
        }
    }
}
