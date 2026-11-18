package fr.mybourse.app

import fr.mybourse.app.data.Devises
import fr.mybourse.app.data.Frais
import fr.mybourse.app.data.Operation
import fr.mybourse.app.data.Sauvegarde
import fr.mybourse.app.data.OperationSauvee
import fr.mybourse.app.data.PortefeuilleSauve
import fr.mybourse.app.data.niveauVolatilite
import fr.mybourse.app.data.Selection
import fr.mybourse.app.data.Sens
import fr.mybourse.app.data.bilan
import fr.mybourse.app.data.peutSupprimer
import fr.mybourse.app.data.quantitePourMontant
import fr.mybourse.app.data.simulerAchat
import fr.mybourse.app.data.simulerVente
import fr.mybourse.app.ui.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortefeuilleTest {
    private val frais = Frais(0.1, 1.0)
    private var t = 0L
    private fun achat(ticker: String, q: Int, prix: Double, change: Double = 1.0, id: Long = ++t) =
        Operation(id, ticker, ticker, if (change == 1.0) "EUR" else "USD", Sens.ACHAT, q, prix, change,
            frais.pour(q * prix * change), id)
    private fun vente(ticker: String, q: Int, prix: Double, change: Double = 1.0, id: Long = ++t) =
        Operation(id, ticker, ticker, if (change == 1.0) "EUR" else "USD", Sens.VENTE, q, prix, change,
            frais.pour(q * prix * change), id)

    @Test fun fraisAvecMinimum() {
        assertEquals(1.0, frais.pour(100.0), 1e-9)      // 0,10 € < minimum 1 €
        assertEquals(5.0, frais.pour(5000.0), 1e-9)     // 0,1 % de 5 000 €
        assertEquals(0.0, frais.pour(0.0), 1e-9)
    }

    @Test fun achatPuisVenteAuCoutMoyen() {
        val ops = listOf(achat("AI.PA", 10, 100.0), achat("AI.PA", 10, 200.0), vente("AI.PA", 5, 250.0))
        val b = bilan(10_000.0, ops)
        val p = b.positions.single()
        assertEquals(15, p.quantite)
        // Coût : 1000+1 + 2000+2 = 3003 ; il en reste 15/20 = 2252,25
        assertEquals(2252.25, p.coutEur, 0.01)
        // Vente : 1250 - 1,25 = 1248,75 ; coût sorti 750,75 ; plus-value 498
        assertEquals(498.0, b.plusValuesRealisees, 0.01)
        assertEquals(10_000 - 3003 + 1248.75, b.liquidites, 0.01)
        assertTrue(b.coherent)
    }

    @Test fun conversionDollars() {
        val b = bilan(10_000.0, listOf(achat("AAPL", 10, 200.0, change = 0.9)))
        assertEquals(1800.0 + 1.8, b.positions.single().coutEur, 0.01)
    }

    @Test fun quantiteMaxPourUnMontant() {
        assertEquals(9, quantitePourMontant(1000.0, 100.0, 1.0, frais))   // 10 titres = 1001 € > 1000 €
        assertEquals(10, quantitePourMontant(1001.0, 100.0, 1.0, frais))
        assertEquals(0, quantitePourMontant(50.0, 100.0, 1.0, frais))
    }

    @Test fun achatRefuseSansLiquidites() {
        val s = simulerAchat(10, 100.0, 1.0, frais, 1000.0)
        assertFalse(s.possible)
        assertEquals("Liquidités insuffisantes", s.erreur)
        assertTrue(simulerAchat(9, 100.0, 1.0, frais, 1000.0).possible)
    }

    @Test fun venteLimiteeALaPosition() {
        val p = bilan(10_000.0, listOf(achat("X", 3, 10.0))).positions.single()
        assertFalse(simulerVente(p, 4, 10.0, 1.0, frais).possible)
        val s = simulerVente(p, 3, 20.0, 1.0, frais)
        assertEquals(59.0, s.totalEur, 0.001)
        assertEquals(59.0 - 31.0, s.plusValue!!, 0.001)
        assertFalse(simulerVente(null, 1, 10.0, 1.0, frais).possible)
    }

    @Test fun suppressionInterditeSiUneVenteEnDepend() {
        val a = achat("X", 3, 10.0)
        val v = vente("X", 3, 12.0)
        assertFalse(peutSupprimer(10_000.0, listOf(a, v), a.id))
        assertTrue(peutSupprimer(10_000.0, listOf(a, v), v.id))
    }

    @Test fun capitalTropFaibleIncoherent() {
        assertFalse(bilan(500.0, listOf(achat("X", 10, 100.0))).coherent)
    }

    @Test fun penceDeLondres() {
        assertEquals("GBP" to 100.0, Devises.base("GBp"))
        assertEquals("USD" to 1.0, Devises.base("USD"))
    }

    @Test fun formatFrancais() {
        assertEquals("1 234,50 €", Format.euros(1234.5))
        assertEquals("+3,4 %", Format.pct(3.42))
        assertEquals("−12,0 %", Format.pct(-12.0))
        assertEquals(1234.5, Format.lireNombre("1 234,5")!!, 1e-9)
        assertNull(Format.lireNombre("abc"))
        assertEquals("eurofins", Format.cle("ÉUROFINS"))
    }

    @Test fun lectureDuFichierDuSoir() {
        val s = Selection.lire(
            """{"format":1,"genere_le":"2026-11-18T22:40:00Z","date_marche":"2026-11-18","change":{"EURUSD":1.1},
              "top":{"FR":["AI.PA"]},"nouveau_champ":42,
              "titres":{"AI.PA":{"nom":"Air Liquide","marche":"FR","devise":"EUR","date":"2026-11-18","cours":191.42,
                "score":84.1,"scores":{"technique":84.1,"fondamental":null},"raisons":["a","b"],
                "historique":[["2026-11-17",190.0],["2026-11-18",191.42]]}}}""",
        )
        val t = s.titres.getValue("AI.PA")
        assertEquals(84.1, t.score!!, 1e-9)
        assertNull(t.scores["fondamental"])
        assertEquals(2, t.points.size)
        assertEquals(1 / 1.1, s.eurParUsd!!, 1e-9)
    }

    @Test fun sauvegardeAllerRetour() {
        val p1 = PortefeuilleSauve("Principal", 5000.0, 0.1, 1.0, 1, listOf(achat("X", 3, 10.0)).map { OperationSauvee.depuis(it) })
        val p2 = PortefeuilleSauve("Prudent", 20_000.0, 0.5, 5.0, 2)
        val lu = Sauvegarde.lire(Sauvegarde.ecrire(Sauvegarde(portefeuilles = listOf(p1, p2))))
        assertEquals(2, lu.portefeuilles.size)
        assertEquals(5000.0, lu.portefeuilles[0].capital, 1e-9)
        assertEquals(1, lu.portefeuilles[0].operations.size)
        assertEquals("Prudent", lu.portefeuilles[1].nom)
        assertEquals(5.0, lu.portefeuilles[1].fraisMin, 1e-9)
        assertNotNull(runCatching { Sauvegarde.lire("{\"appli\":\"Autre\"}") }.exceptionOrNull())
    }

    @Test fun ancienneSauvegardeV01() {
        val texte = """{"format":1,"appli":"MyBourse","exporteLe":"2026-11-18",
            "reglages":{"capital":8000.0,"fraisPct":0.2,"fraisMin":2.0,"theme":"SOMBRE","marches":["FR","US"]},
            "operations":[{"ticker":"AI.PA","nom":"Air Liquide","devise":"EUR","sens":"ACHAT","quantite":2,
              "prix":190.0,"change":1.0,"frais":2.0,"date":1}]}"""
        val p = Sauvegarde.lire(texte).portefeuilles.single()
        assertEquals("Principal", p.nom)
        assertEquals(8000.0, p.capital, 1e-9)
        assertEquals(0.2, p.fraisPct, 1e-9)
        assertEquals(1, p.operations.size)
    }

    @Test fun sauvegardeIncoherenteRefusee() {
        val p = PortefeuilleSauve("Trop petit", 100.0, 0.1, 1.0, 1, listOf(achat("X", 30, 10.0)).map { OperationSauvee.depuis(it) })
        val e = runCatching { Sauvegarde.lire(Sauvegarde.ecrire(Sauvegarde(portefeuilles = listOf(p)))) }.exceptionOrNull()
        assertTrue(e!!.message!!.contains("Trop petit"))
    }

    @Test fun volatiliteEtCapitalisation() {
        assertEquals("faible", niveauVolatilite(15.0))
        assertEquals("élevée", niveauVolatilite(42.0))
        assertEquals("très élevée", niveauVolatilite(80.0))
        assertEquals("1,2 Md€", Format.capi(1.23))
        assertEquals("450 M€", Format.capi(0.45))
        assertEquals("0,1 % (minimum " + Format.euros(1.0) + ")", Format.fraisTexte(frais))
    }
}
