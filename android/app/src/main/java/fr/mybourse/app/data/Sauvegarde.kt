package fr.mybourse.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Fichier de sauvegarde de tous les portefeuilles (export / import JSON).
 * Format 2 : liste de portefeuilles. Le format 1 (v0.1, un seul portefeuille) reste lisible.
 */
@Serializable
data class Sauvegarde(
    val format: Int = 2,
    val appli: String = "MyBourse",
    val exporteLe: String = "",
    val portefeuilles: List<PortefeuilleSauve> = emptyList(),
    /** Format 1 seulement. */
    val reglages: AnciensReglages? = null,
    /** Format 1 seulement. */
    val operations: List<OperationSauvee> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; explicitNulls = false }
        fun ecrire(s: Sauvegarde): String = json.encodeToString(serializer(), s)

        /** Lit et vérifie un fichier ; lève une exception avec un message clair s'il est invalide. */
        fun lire(texte: String): Sauvegarde {
            val s = runCatching { json.decodeFromString(serializer(), texte) }
                .getOrElse { throw IllegalArgumentException("Ce fichier n'est pas une sauvegarde MyBourse") }
            require(s.appli == "MyBourse") { "Ce fichier n'est pas une sauvegarde MyBourse" }
            val portefeuilles = s.portefeuilles.ifEmpty {
                val r = s.reglages ?: AnciensReglages()
                listOf(PortefeuilleSauve("Principal", r.capital, r.fraisPct, r.fraisMin, 0, s.operations))
            }
            for (p in portefeuilles) {
                require(bilan(p.capital, p.operations.map { it.versOperation() }).coherent) {
                    "Portefeuille « ${p.nom} » incohérent : import annulé"
                }
            }
            return Sauvegarde(format = 2, exporteLe = s.exporteLe, portefeuilles = portefeuilles)
        }
    }
}

@Serializable
data class PortefeuilleSauve(
    val nom: String,
    val capital: Double,
    val fraisPct: Double,
    val fraisMin: Double,
    val creeLe: Long = 0,
    val operations: List<OperationSauvee> = emptyList(),
) {
    fun versEntite() = PortefeuilleEntity(0, nom, capital, fraisPct, fraisMin, creeLe)

    companion object {
        fun depuis(p: PortefeuilleEntity, ops: List<Operation>) =
            PortefeuilleSauve(p.nom, p.capital, p.fraisPct, p.fraisMin, p.creeLe, ops.map { OperationSauvee.depuis(it) })
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
