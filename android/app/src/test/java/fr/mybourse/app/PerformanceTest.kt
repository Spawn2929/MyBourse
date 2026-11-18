package fr.mybourse.app

import fr.mybourse.app.data.Backtest
import fr.mybourse.app.data.Operation
import fr.mybourse.app.data.Point
import fr.mybourse.app.data.Selection
import fr.mybourse.app.data.Sens
import fr.mybourse.app.data.bilan
import fr.mybourse.app.data.enPourcentage
import fr.mybourse.app.data.periodePour
import fr.mybourse.app.data.valeurAu
import fr.mybourse.app.data.valeursQuotidiennes
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTest {
    private fun j(d: Int) = LocalDate.of(2026, 11, d)
    private fun ms(d: Int) = j(d).atTime(15, 0).atZone(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli()

    @Test fun derniereValeurConnue() {
        val pts = listOf(Point(j(2), 1.0), Point(j(4), 2.0), Point(j(6), 3.0))
        assertNull(valeurAu(pts, j(1)))
        assertEquals(2.0, valeurAu(pts, j(5))!!, 1e-9)
        assertEquals(3.0, valeurAu(pts, j(30))!!, 1e-9)
    }

    @Test fun valeurJourParJourAvecDollars() {
        val ops = listOf(
            Operation(1, "AAPL", "Apple", "USD", Sens.ACHAT, 10, 100.0, 0.9, 1.0, ms(3)),
            Operation(2, "AI.PA", "Air Liquide", "EUR", Sens.ACHAT, 5, 200.0, 1.0, 1.0, ms(5)),
        )
        val cours = mapOf(
            "AAPL" to listOf(Point(j(3), 100.0), Point(j(4), 110.0), Point(j(5), 120.0)),
            "AI.PA" to listOf(Point(j(5), 210.0)),
        )
        val change = mapOf("USD" to listOf(Point(j(3), 0.9), Point(j(5), 0.8)))
        val v = valeursQuotidiennes(10_000.0, ops, listOf(j(2), j(3), j(4), j(5)), cours, change)
        assertEquals(listOf(j(3), j(4), j(5)), v.map { it.date })                // la courbe part du premier achat
        assertEquals(10_000 - 901 + 900.0, v[0].valeur, 0.01)                     // 9 099 de liquidités + 10 × 100 × 0,9
        assertEquals(9099 + 10 * 110 * 0.9, v[1].valeur, 0.01)
        assertEquals(9099 - 1001 + 10 * 120 * 0.8 + 5 * 210.0, v[2].valeur, 0.01)
        // Le dernier jour correspond au bilan de l'appli valorisé aux mêmes cours
        val b = bilan(10_000.0, ops)
        assertEquals(b.liquidites + 960 + 1050, v[2].valeur, 0.01)
    }

    @Test fun pourcentageDepuisLeDebutDeLaPeriode() {
        val pts = listOf(Point(j(1), 100.0), Point(j(2), 110.0), Point(j(3), 99.0))
        val p = enPourcentage(pts, j(2))
        assertEquals(0.0, p[0].valeur, 1e-9)
        assertEquals(-10.0, p[1].valeur, 1e-9)
        assertTrue(enPourcentage(pts, j(9)).isEmpty())
    }

    @Test fun periodeYahoo() {
        assertEquals("1mo", periodePour(j(1), j(20)))
        assertEquals("1y", periodePour(j(1).minusMonths(8), j(1)))
        assertEquals("5y", periodePour(j(1).minusYears(3), j(1)))
    }

    @Test fun lecturePortefeuilleAutomatique() {
        val s = Selection.lire(
            """{"auto":{"capital":10000,"frais_pct":0.1,"frais_min":1,"debut":"2026-11-18",
              "dernier_reequilibrage":"2026-11-18",
              "operations":[{"date":1,"ticker":"SW.PA","nom":"Sodexo","devise":"EUR","sens":"ACHAT","quantite":10,
                "prix":60.0,"change":1.0,"frais":1.0}],
              "valeurs":[["2026-11-18",10010.5]]}}""",
        )
        val a = s.auto!!
        val ops = a.versOperations(-1)
        assertEquals(-1L, ops.single().portefeuille)
        assertEquals(Sens.ACHAT, ops.single().sens)
        assertEquals(10_000 - 601.0, bilan(a.capital, ops).liquidites, 0.001)
        assertEquals(10010.5, a.points.single().valeur, 1e-9)
    }

    @Test fun lectureBacktest() {
        val b = Backtest.lire(
            """{"format":1,"calcule_le":"2026-12-01T06:20:00Z","annees":2,"debut":"2024-11-18","fin":"2026-11-30",
              "portefeuille":{"capital":10000,"frais_pct":0.1,"frais_min":1,"debut":"2024-11-18",
                "dernier_reequilibrage":"2026-11-30",
                "operations":[{"date":1,"ticker":"SW.PA","nom":"Sodexo","devise":"EUR","sens":"ACHAT","quantite":10,
                  "prix":60.0,"change":1.0,"frais":1.0}],
                "valeurs":[["2024-11-18",9999.0],["2026-11-30",12500.0]]},
              "bilan":{"valeur_finale":12500,"perf_totale":25,"perf_annuelle":11.8,"pire_baisse":-18.4,
                "semaines_gagnantes":56.3,"semaines":105,"operations":812,"frais":950.5,"titres_differents":301,
                "indices":{"^FCHI":4.2,"^GSPC":31.0}},
              "indices":{"^FCHI":{"nom":"CAC 40","historique":[["2024-11-18",7200.0],["2026-11-30",7500.0]]}}}""",
        )
        assertEquals(2, b.portefeuille.points.size)
        assertEquals(-18.4, b.bilan.pireBaisse, 1e-9)
        assertEquals(31.0, b.bilan.indices["^GSPC"]!!, 1e-9)
        assertEquals("CAC 40", b.indices["^FCHI"]!!.nom)
        assertEquals(2, b.indices["^FCHI"]!!.points.size)
        assertEquals(10_000 - 601.0, bilan(b.portefeuille.capital, b.portefeuille.versOperations(-2)).liquidites, 0.001)
    }
}
