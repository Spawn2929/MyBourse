package fr.mybourse.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mybourse.app.data.Marches
import fr.mybourse.app.data.Titre
import fr.mybourse.app.ui.theme.Couleurs
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionEcran(vm: MainViewModel) {
    val selection by vm.selection.collectAsState()
    val chargement by vm.chargement.collectAsState()
    val erreur by vm.erreurSelection.collectAsState()
    val reglages by vm.reglages.collectAsState()
    val marches = reglages?.marches ?: emptySet()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sélection du jour", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                val s = selection
                if (s?.dateMarche != null) {
                    val jour = runCatching { Format.jour(LocalDate.parse(s.dateMarche)) }.getOrDefault(s.dateMarche)
                    val maj = runCatching { Format.quand(Instant.parse(s.genereLe).toEpochMilli()) }.getOrNull()
                    Text(
                        "Clôture du $jour" + (maj?.let { " · analysée $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = vm::rafraichir) { Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser") }
        }

        PullToRefreshBox(isRefreshing = chargement, onRefresh = vm::rafraichir, modifier = Modifier.fillMaxSize()) {
            val s = selection
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                erreur?.let { e ->
                    item {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                            Text(e, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (s == null) {
                    if (erreur == null) item { EtatVide("Chargement…", "Récupération de la sélection du jour") }
                    return@LazyColumn
                }
                for (marche in listOf(Marches.FR, Marches.US)) {
                    if (marche !in marches) continue
                    val tickers = s.top[marche].orEmpty()
                    item(key = "titre-$marche") { TitreSection(Marches.libelles.getValue(marche), "Top ${tickers.size}") }
                    items(tickers, key = { "$marche-$it" }) { t ->
                        s.titres[t]?.let { CarteTitre(t, it, rang = tickers.indexOf(t) + 1) { vm.ouvrir(Ecran.Fiche(t)) } }
                    }
                }
                if (Marches.LISTE in marches && s.maListe.isNotEmpty()) {
                    item(key = "titre-liste") {
                        TitreSection("Ma liste", "Titres suivis en plus (modifiable sur GitHub : pipeline/ma_liste.txt)")
                    }
                    items(s.maListe, key = { "liste-$it" }) { t ->
                        s.titres[t]?.let { CarteTitre(t, it, rang = null) { vm.ouvrir(Ecran.Fiche(t)) } }
                    }
                }
                if (marches.isEmpty()) {
                    item { EtatVide("Aucun marché affiché", "Choisissez les marchés à afficher dans Réglages.") }
                }
            }
        }
    }
}

@Composable
private fun TitreSection(titre: String, sousTitre: String) {
    Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(sousTitre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CarteTitre(ticker: String, t: Titre, rang: Int?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastilleScore(t.score)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        (rang?.let { "$it. " } ?: "") + t.nom,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        ticker + (t.secteur.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row {
                        Text(Format.prix(t.cours, t.devise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        t.var1j?.let {
                            Text("  " + Format.pct(it), style = MaterialTheme.typography.bodyMedium, color = Couleurs.signe(it))
                        }
                    }
                }
                MiniCourbe(t.points.takeLast(63), Modifier.width(88.dp).height(40.dp))
            }
            if (t.raisons.isNotEmpty()) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (r in t.raisons) {
                        Row {
                            Text("• ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text(r, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
