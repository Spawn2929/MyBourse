package fr.mybourse.app.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class Theme(val libelle: String) { AUTO("Automatique"), CLAIR("Clair"), SOMBRE("Sombre") }

/** Réglages de l'appli (le capital et les frais sont propres à chaque portefeuille). */
data class Reglages(
    val theme: Theme = Theme.AUTO,
    /** Zones masquées dans la sélection du jour (toutes affichées par défaut). */
    val masques: Set<String> = emptySet(),
    /** Portefeuille affiché dans l'onglet Portefeuille. */
    val actif: Long = 1,
)

/** Capital et frais de la version 0.1 (un seul portefeuille), repris dans « Principal ». */
@Serializable
data class AnciensReglages(val capital: Double = 10_000.0, val fraisPct: Double = 0.1, val fraisMin: Double = 1.0)

private val Context.store by preferencesDataStore("reglages")

class ReglagesStore(private val context: Context) {
    private val kTheme = stringPreferencesKey("theme")
    private val kMasques = stringSetPreferencesKey("zones_masquees")
    private val kActif = longPreferencesKey("portefeuille_actif")
    // Version 0.1
    private val kCapital = doublePreferencesKey("capital")
    private val kFraisPct = doublePreferencesKey("frais_pct")
    private val kFraisMin = doublePreferencesKey("frais_min")

    val flux: Flow<Reglages> = context.store.data.map { p ->
        Reglages(
            theme = p[kTheme]?.let { runCatching { Theme.valueOf(it) }.getOrNull() } ?: Theme.AUTO,
            masques = p[kMasques] ?: emptySet(),
            actif = p[kActif] ?: 1,
        )
    }

    suspend fun anciens(): AnciensReglages {
        val p = context.store.data.first()
        val d = AnciensReglages()
        return AnciensReglages(p[kCapital] ?: d.capital, p[kFraisPct] ?: d.fraisPct, p[kFraisMin] ?: d.fraisMin)
    }

    suspend fun enregistrer(r: Reglages) {
        context.store.edit { p ->
            p[kTheme] = r.theme.name
            p[kMasques] = r.masques
            p[kActif] = r.actif
        }
    }
}
