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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mybourse.app.data.Ligne
import fr.mybourse.app.data.Operation
import fr.mybourse.app.data.Sens
import fr.mybourse.app.ui.theme.Couleurs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortefeuilleEcran(vm: MainViewModel) {
    val valo by vm.valorisation.collectAsState()
    val chargement by vm.chargement.collectAsState()
    val ops by vm.operations.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mon portefeuille", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.ouvrir(Ecran.Historique) }) { Icon(Icons.Outlined.History, "Historique des transactions") }
        }
        PullToRefreshBox(isRefreshing = chargement, onRefresh = vm::rafraichir, modifier = Modifier.fillMaxSize()) {
            val v = valo
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (v == null) return@LazyColumn
                item {
                    Carte {
                        Text("Valeur totale", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Format.euros(v.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            Format.eurosSigne(v.plusValueEur) + " (" + Format.pct(v.plusValuePct) + ") depuis le départ",
                            color = Couleurs.signe(v.plusValueEur), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        )
                        Spacer8()
                        LigneInfo("Capital de départ", Format.euros(v.capital))
                        LigneInfo("Liquidités", Format.euros(v.liquidites))
                        LigneInfo("Titres", Format.euros(v.valeurTitres))
                        if (ops.any { it.sens == Sens.VENTE }) {
                            LigneInfo("Plus-values réalisées (ventes)", Format.eurosSigne(v.plusValuesRealisees), Couleurs.signe(v.plusValuesRealisees))
                        }
                    }
                }
                item {
                    Button(onClick = { vm.ouvrir(Ecran.Recherche) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Outlined.Add, null)
                        Text("  Acheter un titre (action ou ETF)")
                    }
                }
                if (v.lignes.isEmpty()) {
                    item {
                        EtatVide(
                            "Aucune position pour l'instant",
                            "Touchez « Acheter un titre » ou ouvrez une action de la sélection du jour, puis « Acheter (virtuel) ».",
                        )
                    }
                } else {
                    item { Text("Positions (${v.lignes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)) }
                    items(v.lignes, key = { it.position.ticker }) { l -> CarteLigne(l) { vm.ouvrir(Ecran.Fiche(l.position.ticker)) } }
                }
            }
        }
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

@Composable
private fun CarteLigne(l: Ligne, onClick: () -> Unit) {
    val p = l.position
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${p.ticker} · ${Format.quantite(p.quantite)} titre${if (p.quantite > 1) "s" else ""} · PRU ${Format.euros(p.pruEur)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(l.valeurEur?.let { Format.euros(it) } ?: "Cours ?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                l.plusValueEur?.let { pv ->
                    Text(Format.eurosSigne(pv) + " · " + (l.plusValuePct?.let { Format.pct(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Couleurs.signe(pv))
                }
            }
        }
    }
}

// ---------------- Historique des transactions ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoriqueEcran(vm: MainViewModel) {
    val ops by vm.operations.collectAsState()
    var aSupprimer by remember { mutableStateOf<Operation?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Historique des transactions") },
            navigationIcon = { IconButton(onClick = { vm.retour() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
        if (ops.isEmpty()) {
            EtatVide("Aucune transaction", "Vos achats et ventes virtuels apparaîtront ici, avec la date et l'heure.")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(ops.reversed(), key = { it.id }) { op ->
                val achat = op.sens == Sens.ACHAT
                ListItem(
                    headlineContent = {
                        Text((if (achat) "Achat · " else "Vente · ") + op.nom, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium)
                    },
                    supportingContent = {
                        Text(
                            "${Format.quantite(op.quantite)} × ${Format.prix(op.prix, op.devise)} · frais ${Format.euros(op.frais)}\n" +
                                Format.dateHeure(op.date),
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (achat) "−" else "+") + Format.euros(if (achat) op.brutEur + op.frais else op.brutEur - op.frais),
                                color = if (achat) MaterialTheme.colorScheme.onSurface else Couleurs.signe(1.0),
                                fontWeight = FontWeight.Medium,
                            )
                            IconButton(onClick = { aSupprimer = op }) { Icon(Icons.Outlined.Delete, "Supprimer") }
                        }
                    },
                    modifier = Modifier.clickable { vm.ouvrir(Ecran.Fiche(op.ticker)) },
                )
            }
        }
    }
    aSupprimer?.let { op ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text("Supprimer cette transaction ?") },
            text = { Text("${if (op.sens == Sens.ACHAT) "Achat" else "Vente"} de ${Format.quantite(op.quantite)} ${op.nom} du ${Format.dateHeure(op.date)}. Le portefeuille sera recalculé comme si elle n'avait pas eu lieu.") },
            confirmButton = { TextButton(onClick = { vm.supprimerOperation(op); aSupprimer = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text("Garder") } },
        )
    }
}

// ---------------- Recherche d'un titre ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechercheEcran(vm: MainViewModel) {
    val r by vm.recherche.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Acheter un titre") },
            navigationIcon = { IconButton(onClick = { vm.retour() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
        OutlinedTextField(
            value = r.requete,
            onValueChange = vm::chercherTexte,
            label = { Text("Nom ou code (ex. Air Liquide, CW8, VOO)") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
        )
        if (r.chargement) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        r.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (r.requete.trim().length >= 2 && !r.chargement && r.resultats.isEmpty() && r.erreur == null) {
            EtatVide("Aucun résultat", "Essayez le nom complet ou le code Yahoo (ex. « MC.PA » pour LVMH).")
        }
        if (r.requete.isBlank()) {
            Text(
                "Toutes les actions et tous les ETF cotés sont disponibles. Les cours viennent de Yahoo Finance, " +
                    "les titres de Paris finissent par « .PA ».",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(r.resultats, key = { it.ticker }) { res ->
                ListItem(
                    headlineContent = { Text(res.nom, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${res.ticker} · ${res.type} · ${res.bourse}") },
                    modifier = Modifier.clickable { vm.ouvrir(Ecran.Fiche(res.ticker)) },
                )
            }
        }
    }
}
