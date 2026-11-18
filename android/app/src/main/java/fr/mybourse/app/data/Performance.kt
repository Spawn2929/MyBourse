package fr.mybourse.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Courbe de valeur d'un portefeuille et comparaison avec les indices (fonctions pures, testées).
 */

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

fun jourDe(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(PARIS).toLocalDate()

/** Dernière valeur connue à cette date (ou avant) dans une série triée par date. */
fun valeurAu(points: List<Point>, jour: LocalDate): Double? {
    var bas = 0
    var haut = points.size - 1
    var res: Double? = null
    while (bas <= haut) {
        val m = (bas + haut) / 2
        if (points[m].date <= jour) { res = points[m].valeur; bas = m + 1 } else haut = m - 1
    }
    return res
}

/**
 * Valeur du portefeuille à la clôture de chaque jour : liquidités + titres détenus ce jour-là.
 * cours : historique par titre (devise de cotation) ; change : euros pour 1 unité de chaque devise de cotation,
 * par jour (EUR absent : 1). Un titre sans cours ce jour-là est compté au cours de sa dernière opération.
 */
fun valeursQuotidiennes(
    capital: Double,
    operations: List<Operation>,
    jours: List<LocalDate>,
    cours: Map<String, List<Point>>,
    change: Map<String, List<Point>>,
): List<Point> {
    if (operations.isEmpty()) return emptyList()
    val ops = operations.sortedWith(compareBy({ it.date }, { it.id }))
    val debut = jourDe(ops.first().date)
    val res = ArrayList<Point>()
    var n = 0
    for (j in jours.filter { it >= debut }.distinct().sorted()) {
        while (n < ops.size && jourDe(ops[n].date) <= j) n++
        val faites = ops.subList(0, n)
        val b = bilan(capital, faites)
        var total = b.liquidites
        for (p in b.positions) {
            val derniere = faites.last { it.ticker == p.ticker }
            val prix = cours[p.ticker]?.let { valeurAu(it, j) } ?: derniere.prix
            val taux = if (p.devise == "EUR") 1.0 else change[p.devise]?.let { valeurAu(it, j) } ?: derniere.change
            total += prix * taux * p.quantite
        }
        res += Point(j, centimes(total))
    }
    return res
}

/** Variation en % depuis le premier point de la période (première valeur = 0 %). */
fun enPourcentage(points: List<Point>, depuis: LocalDate): List<Point> {
    val p = points.filter { it.date >= depuis }
    val base = p.firstOrNull()?.valeur?.takeIf { it > 0 } ?: return emptyList()
    return p.map { Point(it.date, (it.valeur / base - 1) * 100) }
}

/** Période Yahoo suffisante pour couvrir depuis cette date. */
fun periodePour(debut: LocalDate, aujourdhui: LocalDate = LocalDate.now(PARIS)): String {
    val jours = aujourdhui.toEpochDay() - debut.toEpochDay()
    return when {
        jours <= 25 -> "1mo"
        jours <= 170 -> "6mo"
        jours <= 350 -> "1y"
        jours <= 700 -> "2y"
        else -> "5y"
    }
}
