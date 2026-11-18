package fr.mybourse.app.data

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Fichier publié chaque soir par l'analyse (selection.json). */
@Serializable
data class Selection(
    val format: Int = 1,
    @SerialName("genere_le") val genereLe: String = "",
    @SerialName("date_marche") val dateMarche: String? = null,
    val avertissement: String = "",
    val ponderations: Map<String, Double> = emptyMap(),
    val change: Map<String, Double?> = emptyMap(),
    val top: Map<String, List<String>> = emptyMap(),
    @SerialName("ma_liste") val maListe: List<String> = emptyList(),
    val indices: Map<String, Indice> = emptyMap(),
    val titres: Map<String, Titre> = emptyMap(),
) {
    /** Euros pour 1 dollar, d'après le cours EUR/USD publié. */
    val eurParUsd: Double? get() = change["EURUSD"]?.takeIf { it > 0 }?.let { 1 / it }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        fun lire(texte: String): Selection = json.decodeFromString(serializer(), texte)
    }
}

@Serializable
data class Indice(val nom: String = "", val historique: List<JsonArray> = emptyList()) {
    val points: List<Point> get() = versPoints(historique)
}

@Serializable
data class Titre(
    val nom: String = "",
    val marche: String = "",
    val secteur: String = "",
    val devise: String = "EUR",
    val date: String = "",
    val cours: Double = 0.0,
    @SerialName("var_1j") val var1j: Double? = null,
    val score: Double? = null,
    val scores: Map<String, Double?> = emptyMap(),
    val raisons: List<String> = emptyList(),
    val indicateurs: Map<String, Double?> = emptyMap(),
    @SerialName("notes_technique") val notesTechnique: Map<String, Double> = emptyMap(),
    val historique: List<JsonArray> = emptyList(),
    /** Capitalisation en milliards d'euros. */
    val capi: Double? = null,
    /** Grande valeur (CAC 40, S&P 500 ou plus de 10 Md€) : analysée mais hors des tops. */
    val grande: Boolean = false,
) {
    val points: List<Point> get() = versPoints(historique)
    val volatilite: Double? get() = indicateurs["volatilite"]
}

/** Niveau de risque d'après la volatilité annuelle (%). */
fun niveauVolatilite(v: Double): String = when {
    v < 20 -> "faible"
    v < 35 -> "moyenne"
    v < 50 -> "élevée"
    else -> "très élevée"
}

data class Point(val date: LocalDate, val valeur: Double)

private fun versPoints(brut: List<JsonArray>): List<Point> = brut.mapNotNull { p ->
    val d = runCatching { LocalDate.parse(p[0].jsonPrimitive.content) }.getOrNull()
    val v = p.getOrNull(1)?.jsonPrimitive?.doubleOrNull
    if (d != null && v != null) Point(d, v) else null
}

/** Marchés du classement, dans l'ordre d'affichage. */
object Marches {
    const val FR = "FR"
    const val EU = "EU"
    const val US = "US"
    const val LISTE = "LISTE"
    val zones = listOf(FR, EU, US)
    val libelles = mapOf(FR to "France", EU to "Europe (hors France)", US to "États-Unis", LISTE to "Ma liste")
}

/** Familles de signaux, dans l'ordre d'affichage. */
val FAMILLES = listOf(
    "technique" to "Technique",
    "fondamental" to "Fondamental",
    "sentiment" to "Sentiment",
    "macro" to "Macro et secteur",
)

/** Composantes du score technique, avec une explication courte. */
val NOTES_TECHNIQUES = listOf(
    Triple("tendance", "Tendance", "Cours au-dessus de ses moyennes 50 et 200 jours"),
    Triple("momentum", "Élan", "Hausse sur 3 et 6 mois comparée aux autres actions du marché"),
    Triple("rsi", "RSI", "Force des achats récents, sans excès (idéal entre 55 et 70)"),
    Triple("macd", "MACD", "Accélération récente de la tendance"),
    Triple("volume", "Volumes", "Échanges plus nombreux quand le cours monte"),
)
