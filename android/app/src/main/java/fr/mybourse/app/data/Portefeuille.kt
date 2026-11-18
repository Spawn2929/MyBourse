package fr.mybourse.app.data

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Calculs du portefeuille virtuel (fonctions pures, testées).
 *
 * Tous les montants en euros sont arrondis au centime. Le prix de revient (PRU) inclut les frais
 * d'achat ; à la vente, il est diminué en proportion des titres vendus (méthode du coût moyen).
 */

enum class Sens { ACHAT, VENTE }

data class Operation(
    val id: Long = 0,
    val ticker: String,
    val nom: String,
    val devise: String,
    val sens: Sens,
    val quantite: Int,
    /** Prix unitaire dans la devise de cotation. */
    val prix: Double,
    /** Euros pour 1 unité de la devise de cotation au moment de l'opération. */
    val change: Double,
    /** Frais de courtage simulés, en euros. */
    val frais: Double,
    val date: Long,
    /** Identifiant du portefeuille. */
    val portefeuille: Long = 1,
) {
    val brutEur: Double get() = centimes(quantite * prix * change)
}

data class Frais(val pourcentage: Double = 0.1, val minimum: Double = 1.0) {
    fun pour(montantEur: Double): Double =
        if (montantEur <= 0) 0.0 else centimes(max(montantEur * pourcentage / 100, minimum))
}

data class Position(
    val ticker: String,
    val nom: String,
    val devise: String,
    val quantite: Int,
    /** Coût total en euros, frais d'achat compris. */
    val coutEur: Double,
) {
    val pruEur: Double get() = if (quantite > 0) coutEur / quantite else 0.0
}

data class Bilan(
    val liquidites: Double,
    val positions: List<Position>,
    val plusValuesRealisees: Double,
    val fraisPayes: Double,
    /** false si l'historique est incohérent (vente de titres non détenus, liquidités négatives). */
    val coherent: Boolean = true,
)

fun centimes(v: Double): Double = (v * 100).roundToLong() / 100.0

/** Rejoue toutes les opérations dans l'ordre chronologique. */
fun bilan(capital: Double, operations: List<Operation>): Bilan {
    var liquidites = capital
    var realise = 0.0
    var frais = 0.0
    var coherent = true
    val pos = LinkedHashMap<String, Position>()
    for (op in operations.sortedWith(compareBy({ it.date }, { it.id }))) {
        val p = pos[op.ticker] ?: Position(op.ticker, op.nom, op.devise, 0, 0.0)
        frais += op.frais
        when (op.sens) {
            Sens.ACHAT -> {
                liquidites -= op.brutEur + op.frais
                pos[op.ticker] = p.copy(nom = op.nom, quantite = p.quantite + op.quantite,
                    coutEur = p.coutEur + op.brutEur + op.frais)
            }
            Sens.VENTE -> {
                if (op.quantite > p.quantite) coherent = false
                val q = minOf(op.quantite, p.quantite)
                val coutSorti = if (p.quantite > 0) p.coutEur * q / p.quantite else 0.0
                liquidites += op.brutEur - op.frais
                realise += op.brutEur - op.frais - coutSorti
                pos[op.ticker] = p.copy(quantite = p.quantite - q, coutEur = p.coutEur - coutSorti)
            }
        }
        if (liquidites < -0.005) coherent = false
    }
    return Bilan(
        liquidites = centimes(liquidites),
        positions = pos.values.filter { it.quantite > 0 }.map { it.copy(coutEur = centimes(it.coutEur)) },
        plusValuesRealisees = centimes(realise),
        fraisPayes = centimes(frais),
        coherent = coherent,
    )
}

/** Résultat d'une simulation d'ordre, affiché avant de valider. */
data class Simulation(
    val quantite: Int,
    val brutEur: Double,
    val fraisEur: Double,
    /** Achat : ce qui sort des liquidités. Vente : ce qui y entre. */
    val totalEur: Double,
    /** Vente seulement : plus ou moins-value réalisée. */
    val plusValue: Double? = null,
    val erreur: String? = null,
) {
    val possible: Boolean get() = erreur == null
}

fun simulerAchat(quantite: Int, prix: Double, change: Double, frais: Frais, liquidites: Double): Simulation {
    if (quantite <= 0) return Simulation(0, 0.0, 0.0, 0.0, erreur = "Indiquez une quantité")
    if (prix <= 0 || change <= 0) return Simulation(0, 0.0, 0.0, 0.0, erreur = "Cours indisponible")
    val brut = centimes(quantite * prix * change)
    val f = frais.pour(brut)
    val total = centimes(brut + f)
    val erreur = if (total > liquidites + 0.005) "Liquidités insuffisantes" else null
    return Simulation(quantite, brut, f, total, erreur = erreur)
}

/** Plus grand nombre entier d'actions achetables avec ce montant, frais compris. */
fun quantitePourMontant(montantEur: Double, prix: Double, change: Double, frais: Frais): Int {
    val unitaire = prix * change
    if (montantEur <= 0 || unitaire <= 0) return 0
    var q = floor(montantEur / unitaire).toInt()
    while (q > 0 && centimes(q * unitaire) + frais.pour(centimes(q * unitaire)) > montantEur + 0.005) q--
    return q
}

fun simulerVente(position: Position?, quantite: Int, prix: Double, change: Double, frais: Frais): Simulation {
    if (quantite <= 0) return Simulation(0, 0.0, 0.0, 0.0, erreur = "Indiquez une quantité")
    if (position == null || quantite > position.quantite) {
        return Simulation(0, 0.0, 0.0, 0.0, erreur = "Vous détenez ${position?.quantite ?: 0} titre(s)")
    }
    if (prix <= 0 || change <= 0) return Simulation(0, 0.0, 0.0, 0.0, erreur = "Cours indisponible")
    val brut = centimes(quantite * prix * change)
    val f = frais.pour(brut)
    val net = centimes(brut - f)
    val cout = position.coutEur * quantite / position.quantite
    return Simulation(quantite, brut, f, net, plusValue = centimes(net - cout))
}

/** Une opération peut être supprimée si l'historique reste cohérent sans elle. */
fun peutSupprimer(capital: Double, operations: List<Operation>, id: Long): Boolean =
    bilan(capital, operations.filter { it.id != id }).coherent

/** Ligne de portefeuille valorisée au dernier cours connu. */
data class Ligne(
    val position: Position,
    val cours: Double?,
    val change: Double?,
) {
    val valeurEur: Double? get() =
        if (cours != null && change != null) centimes(position.quantite * cours * change) else null
    val plusValueEur: Double? get() = valeurEur?.let { centimes(it - position.coutEur) }
    val plusValuePct: Double? get() =
        plusValueEur?.let { if (position.coutEur > 0) it / position.coutEur * 100 else null }
}

data class Valorisation(
    val capital: Double,
    val liquidites: Double,
    val lignes: List<Ligne>,
    val plusValuesRealisees: Double,
) {
    /** Les lignes sans cours connu sont comptées à leur prix de revient. */
    val valeurTitres: Double get() = centimes(lignes.sumOf { it.valeurEur ?: it.position.coutEur })
    val total: Double get() = centimes(liquidites + valeurTitres)
    val plusValueEur: Double get() = centimes(total - capital)
    val plusValuePct: Double get() = if (capital > 0) plusValueEur / capital * 100 else 0.0
}
