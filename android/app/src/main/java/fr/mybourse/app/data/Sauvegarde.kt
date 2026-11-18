package fr.mybourse.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Fichier de sauvegarde du portefeuille (export / import JSON). */
@Serializable
data class Sauvegarde(
    val format: Int = 1,
    val appli: String = "MyBourse",
    val exporteLe: String = "",
    val reglages: Reglages = Reglages(),
    val operations: List<OperationSauvee> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
        fun ecrire(s: Sauvegarde): String = json.encodeToString(serializer(), s)

        /** Lit et vérifie un fichier ; lève une exception avec un message clair s'il est invalide. */
        fun lire(texte: String): Sauvegarde {
            val s = runCatching { json.decodeFromString(serializer(), texte) }
                .getOrElse { throw IllegalArgumentException("Ce fichier n'est pas une sauvegarde MyBourse") }
            require(s.appli == "MyBourse") { "Ce fichier n'est pas une sauvegarde MyBourse" }
            val ops = s.operations.map { it.versOperation() }
            require(bilan(s.reglages.capital, ops).coherent) { "Sauvegarde incohérente : import annulé" }
            return s
        }
    }
}

@Serializable
data class OperationSauvee(
    val ticker: String,
    val nom: String,
    val devise: String,
    val sens: Sens,
    val quantite: Int,
    val prix: Double,
    val change: Double,
    val frais: Double,
    val date: Long,
) {
    fun versOperation() = Operation(0, ticker, nom, devise, sens, quantite, prix, change, frais, date)

    companion object {
        fun depuis(o: Operation) = OperationSauvee(o.ticker, o.nom, o.devise, o.sens, o.quantite, o.prix, o.change, o.frais, o.date)
    }
}
