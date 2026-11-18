package fr.mybourse.app.ui

import fr.mybourse.app.data.Devises
import java.text.Normalizer
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mise en forme à la française : « 1 234,56 € », « +3,4 % », « mardi 18 novembre ». */
object Format {
    private val FR = Locale.FRANCE
    val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    private fun nombre(v: Double, decimales: Int): String = NumberFormat.getNumberInstance(FR).apply {
        minimumFractionDigits = decimales
        maximumFractionDigits = decimales
    }.format(v).replace(' ', ' ')

    fun euros(v: Double): String = nombre(v, 2) + " €"

    fun eurosSigne(v: Double): String = (if (v > 0.004) "+" else if (v < -0.004) "−" else "") + euros(kotlin.math.abs(v))

    fun pct(v: Double): String = (if (v > 0.004) "+" else if (v < -0.004) "−" else "") +
        nombre(kotlin.math.abs(v), if (kotlin.math.abs(v) >= 100) 0 else 1) + " %"

    /** Prix dans la devise de cotation : « 191,42 € », « 443,34 $ », « 1 234,50 p ». */
    fun prix(v: Double, devise: String): String {
        val dec = if (v >= 1000) 2 else if (v >= 1) 2 else 4
        return nombre(v, dec) + " " + Devises.symbole(devise)
    }

    fun quantite(q: Int): String = nombre(q.toDouble(), 0)

    fun score(v: Double): String = nombre(v, 0)

    private val jourLong = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)
    private val jourCourt = DateTimeFormatter.ofPattern("d MMM yyyy", FR)
    private val dateHeure = DateTimeFormatter.ofPattern("d MMM yyyy 'à' H 'h' mm", FR)
    private val heure = DateTimeFormatter.ofPattern("H 'h' mm", FR)

    fun jour(d: LocalDate): String = jourLong.format(d)
    fun dateCourte(d: LocalDate): String = jourCourt.format(d)
    fun dateHeure(millis: Long): String = dateHeure.format(Instant.ofEpochMilli(millis).atZone(PARIS))

    /** « aujourd'hui à 17 h 35 », « hier à 22 h 00 », sinon la date complète. */
    fun quand(millis: Long): String {
        val z = Instant.ofEpochMilli(millis).atZone(PARIS)
        val auj = LocalDate.now(PARIS)
        return when (z.toLocalDate()) {
            auj -> "aujourd'hui à " + heure.format(z)
            auj.minusDays(1) -> "hier à " + heure.format(z)
            else -> dateHeure.format(z)
        }
    }

    /** Pour comparer des noms sans tenir compte des majuscules ni des accents. */
    fun cle(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(FR)

    /** Nombre à modifier dans un champ : « 10000 », « 0,1 ». */
    fun nombreSimple(v: Double): String =
        if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString().replace('.', ',')

    /** « 0,1 % (minimum 1,00 €) ». */
    fun fraisTexte(f: fr.mybourse.app.data.Frais): String =
        nombreSimple(f.pourcentage) + " % (minimum " + euros(f.minimum) + ")"

    /** Capitalisation en milliards d'euros : « 1,2 Md€ », « 450 M€ ». */
    fun capi(milliards: Double): String =
        if (milliards >= 1) nombre(milliards, 1) + " Md€" else nombre(milliards * 1000, 0) + " M€"

    /** Saisie d'un nombre (virgule ou point) ; null si invalide. */
    fun lireNombre(s: String): Double? = s.trim().replace(" ", "").replace(" ", "")
        .replace(',', '.').toDoubleOrNull()
}
