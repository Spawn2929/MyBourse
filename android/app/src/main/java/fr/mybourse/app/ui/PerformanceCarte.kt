package fr.mybourse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mybourse.app.data.enPourcentage
import fr.mybourse.app.ui.theme.Couleurs
import java.time.LocalDate

/** Indices de comparaison, dans l'ordre d'affichage, avec leur couleur. */
private val INDICES = listOf(
    "^FCHI" to Color(0xFF4C7BD9),
    "^GSPC" to Color(0xFFE07B39),
    "^RUT" to Color(0xFF9B6BD3),
    "^STOXX" to Color(0xFF8A8F98),
)

private val PERIODES_PERF = listOf("1 mois" to 1L, "6 mois" to 6L, "Tout" to 0L)

/** Courbe du portefeuille comparée aux indices, en % depuis le début de la période. */
@Composable
fun PerformanceCarte(vm: MainViewModel, v: VuePortefeuille) {
    val perfs by vm.performances.collectAsState()
    val selection by vm.selection.collectAsState()
    LaunchedEffect(v.id, v.operations.size, selection?.auto?.valeurs?.size) { vm.chargerPerformance(v) }
    val etat = perfs[v.id] ?: EtatPerformance(chargement = true)
    var mois by rememberSaveable { mutableLongStateOf(0L) }

    Carte {
        Text("Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (etat.chargement) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        etat.erreur?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (etat.valeurs.isEmpty()) {
            if (!etat.chargement) {
                Text(
                    if (v.auto) "La courbe commencera après la prochaine analyse du soir."
                    else "La courbe démarrera avec votre premier achat.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Carte
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((lib, m) in PERIODES_PERF) {
                FilterChip(selected = mois == m, onClick = { mois = m }, label = { Text(lib) })
            }
        }
        val premier = etat.valeurs.first().date
        val depuis = if (mois == 0L) premier else maxOf(premier, LocalDate.now(Format.PARIS).minusMonths(mois))
        val principal = MaterialTheme.colorScheme.primary
        val series = buildList {
            add(Serie(v.portefeuille.nom, enPourcentage(etat.valeurs, depuis), principal))
            for ((sym, couleur) in INDICES) {
                val i = selection?.indices?.get(sym) ?: continue
                add(Serie(i.nom, enPourcentage(i.points, depuis), couleur, pointillee = true))
            }
        }.filter { it.points.size >= 2 || it.couleur == principal }
        Graphique(series, Modifier.padding(top = 8.dp), legende = false) { Format.pct(it) }
        for (s in series) {
            val fin = s.points.lastOrNull()?.valeur ?: continue
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(s.couleur))
                Text(
                    s.nom, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (s.couleur == principal) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Text(Format.pct(fin), style = MaterialTheme.typography.bodyMedium, color = Couleurs.signe(fin),
                    fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        Text(
            "Depuis le " + Format.dateCourte(depuis) + (if (v.auto) " · valeur de chaque soir à la clôture" else " · cours de clôture Yahoo") +
                ". Indices sans dividendes, en monnaie locale.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
