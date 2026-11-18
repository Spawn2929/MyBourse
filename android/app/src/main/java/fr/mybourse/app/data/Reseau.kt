package fr.mybourse.app.data

import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Accès réseau : fichier du soir (GitHub) et cours à la demande (Yahoo, gratuit, sans clé). */
object Reseau {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/130.0 Mobile Safari/537.36"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun texte(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Erreur ${r.code}")
            r.body?.string() ?: throw IOException("Réponse vide")
        }
    }

    private suspend fun objet(url: String): JsonObject = json.parseToJsonElement(texte(url)).jsonObject

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Recherche d'actions et d'ETF par nom ou par code. */
    suspend fun rechercher(q: String): List<ResultatRecherche> {
        val o = objet("https://query2.finance.yahoo.com/v1/finance/search?q=${enc(q)}" +
            "&quotesCount=15&newsCount=0&listsCount=0&lang=fr-FR&region=FR")
        return o["quotes"]?.jsonArray.orEmpty().mapNotNull { e ->
            val q2 = e.jsonObject
            val type = q2.str("quoteType") ?: return@mapNotNull null
            if (type !in setOf("EQUITY", "ETF")) return@mapNotNull null
            val symbole = q2.str("symbol") ?: return@mapNotNull null
            ResultatRecherche(
                ticker = symbole,
                nom = q2.str("longname") ?: q2.str("shortname") ?: symbole,
                bourse = q2.str("exchDisp") ?: q2.str("exchange") ?: "",
                type = if (type == "ETF") "ETF" else "Action",
            )
        }
    }

    /** Dernier cours et historique quotidien (période Yahoo : 1mo, 6mo, 1y, 2y…). */
    suspend fun cotation(ticker: String, periode: String = "2y"): Cotation {
        val o = objet("https://query1.finance.yahoo.com/v8/finance/chart/${enc(ticker)}" +
            "?range=$periode&interval=1d")
        val res = o["chart"]?.jsonObject?.get("result")?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IOException("Titre introuvable")
        val meta = res["meta"]!!.jsonObject
        val zone = runCatching { ZoneId.of(meta.str("exchangeTimezoneName")) }.getOrDefault(ZoneId.of("Europe/Paris"))
        val temps = res["timestamp"]?.jsonArray.orEmpty().map { it.jsonPrimitive.longOrNull }
        val closes = res["indicators"]?.jsonObject?.get("quote")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("close")?.jsonArray.orEmpty().map { it.jsonPrimitive.doubleOrNull }
        val points = temps.zip(closes).mapNotNull { (t, c) ->
            if (t != null && c != null) Point(Instant.ofEpochSecond(t).atZone(zone).toLocalDate(), c) else null
        }.distinctBy { it.date }
        val prix = meta.dbl("regularMarketPrice") ?: points.lastOrNull()?.valeur ?: throw IOException("Cours indisponible")
        val quand = meta["regularMarketTime"]?.jsonPrimitive?.longOrNull?.let { it * 1000 } ?: System.currentTimeMillis()
        return Cotation(
            ticker = meta.str("symbol") ?: ticker,
            nom = meta.str("longName") ?: meta.str("shortName") ?: ticker,
            devise = meta.str("currency") ?: "EUR",
            prix = prix,
            heure = quand,
            bourse = meta.str("fullExchangeName") ?: meta.str("exchangeName") ?: "",
            type = if (meta.str("instrumentType") == "ETF") "ETF" else "Action",
            historique = points,
        )
    }

    /** Euros pour 1 unité de la devise (pence et cents gérés). */
    suspend fun tauxVersEur(devise: String): Double {
        val (base, diviseur) = Devises.base(devise)
        if (base == "EUR") return 1.0 / diviseur
        return cotation("${base}EUR=X", "5d").prix / diviseur
    }
}

data class ResultatRecherche(val ticker: String, val nom: String, val bourse: String, val type: String)

data class Cotation(
    val ticker: String,
    val nom: String,
    val devise: String,
    val prix: Double,
    /** Heure du dernier cours (millisecondes). */
    val heure: Long,
    val bourse: String,
    val type: String,
    val historique: List<Point>,
) {
    val date: LocalDate get() = Instant.ofEpochMilli(heure).atZone(ZoneId.of("Europe/Paris")).toLocalDate()
}

object Devises {
    /** Devise de base et diviseur : les cours de Londres sont en pence (GBp = GBP / 100). */
    fun base(devise: String): Pair<String, Double> = when (devise) {
        "GBp", "GBX" -> "GBP" to 100.0
        "ZAc", "ZAC" -> "ZAR" to 100.0
        "ILA" -> "ILS" to 100.0
        else -> devise.uppercase() to 1.0
    }

    fun symbole(devise: String): String = when (devise) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBp", "GBX" -> "p"
        "GBP" -> "£"
        "CHF" -> "CHF"
        "JPY" -> "¥"
        else -> devise
    }
}

private fun JsonObject.str(k: String): String? = (this[k] as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull
