package fr.mybourse.app.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class Theme(val libelle: String) { AUTO("Automatique"), CLAIR("Clair"), SOMBRE("Sombre") }

@Serializable
data class Reglages(
    val capital: Double = 10_000.0,
    val fraisPct: Double = 0.1,
    val fraisMin: Double = 1.0,
    val theme: Theme = Theme.AUTO,
    /** Marchés affichés dans la sélection du jour. */
    val marches: Set<String> = setOf(Marches.FR, Marches.US, Marches.LISTE),
) {
    val frais: Frais get() = Frais(fraisPct, fraisMin)
}

private val Context.store by preferencesDataStore("reglages")

class ReglagesStore(private val context: Context) {
    private val kCapital = doublePreferencesKey("capital")
    private val kFraisPct = doublePreferencesKey("frais_pct")
    private val kFraisMin = doublePreferencesKey("frais_min")
    private val kTheme = stringPreferencesKey("theme")
    private val kMarches = stringSetPreferencesKey("marches")

    val flux: Flow<Reglages> = context.store.data.map { p ->
        val d = Reglages()
        Reglages(
            capital = p[kCapital] ?: d.capital,
            fraisPct = p[kFraisPct] ?: d.fraisPct,
            fraisMin = p[kFraisMin] ?: d.fraisMin,
            theme = p[kTheme]?.let { runCatching { Theme.valueOf(it) }.getOrNull() } ?: d.theme,
            marches = p[kMarches] ?: d.marches,
        )
    }

    suspend fun enregistrer(r: Reglages) {
        context.store.edit { p ->
            p[kCapital] = r.capital
            p[kFraisPct] = r.fraisPct
            p[kFraisMin] = r.fraisMin
            p[kTheme] = r.theme.name
            p[kMarches] = r.marches
        }
    }
}
