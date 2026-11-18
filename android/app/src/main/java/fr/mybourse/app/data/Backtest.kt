package fr.mybourse.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backtest publié une fois par mois (backtest.json) : la méthode et le portefeuille automatique
 * rejoués sur les années passées, avec les indices sur la même période.
 */
@Serializable
data class Backtest(
    @SerialName("calcule_le") val calculeLe: String = "",
    val annees: Int = 2,
    val debut: String = "",
    val fin: String = "",
    val portefeuille: Auto = Auto(),
    val bilan: BilanTest = BilanTest(),
    val indices: Map<String, Indice> = emptyMap(),
) {
    companion object {
        fun lire(texte: String): Backtest = Selection.json.decodeFromString(serializer(), texte)
    }
}

@Serializable
data class BilanTest(
    @SerialName("valeur_finale") val valeurFinale: Double = 0.0,
    @SerialName("perf_totale") val perfTotale: Double = 0.0,
    @SerialName("perf_annuelle") val perfAnnuelle: Double = 0.0,
    @SerialName("pire_baisse") val pireBaisse: Double = 0.0,
    @SerialName("semaines_gagnantes") val semainesGagnantes: Double? = null,
    val semaines: Int = 0,
    val operations: Int = 0,
    val frais: Double = 0.0,
    @SerialName("titres_differents") val titresDifferents: Int = 0,
    /** Performance de chaque indice sur la même période, en %. */
    val indices: Map<String, Double> = emptyMap(),
)
