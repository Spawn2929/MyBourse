package fr.mybourse.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import fr.mybourse.app.data.PortefeuilleEntity
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
    val actif by vm.actif.collectAsState()
    val tous by vm.portefeuilles.collectAsState()
    val chargement by vm.chargement.collectAsState()
    var selecteur by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dialogue by remember { mutableStateOf<DialogueP?>(null) }
    val a = actif

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { selecteur = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(a?.portefeuille?.nom ?: "Portefeuille", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Outlined.ArrowDropDown, "Changer de portefeuille")
            }
            IconButton(onClick = { vm.ouvrir(Ecran.Historique) }) { Icon(Icons.Outlined.History, "Historique des transactions") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Plus d'options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val modifiable = a != null && !a.auto
                    if (modifiable) {
                        DropdownMenuItem(text = { Text("Nom, capital et frais") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; a?.let { dialogue = DialogueP.Modifier(it.portefeuille) } })
                    }
                    DropdownMenuItem(text = { Text("Nouveau portefeuille") }, leadingIcon = { Icon(Icons.Outlined.Add, null) },
                        onClick = { menu = false; dialogue = DialogueP.Nouveau })
                    if (modifiable) {
                        DropdownMenuItem(text = { Text("Réinitialiser") }, leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                            onClick = { menu = false; a?.let { dialogue = DialogueP.Reinitialiser(it.portefeuille) } })
                        DropdownMenuItem(text = { Text("Supprimer") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            enabled = tous.count { !it.auto } > 1,
                            onClick = { menu = false; a?.let { dialogue = DialogueP.Supprimer(it.portefeuille) } })
                    }
                }
            }
        }
        PullToRefreshBox(isRefreshing = chargement, onRefresh = vm::rafraichir, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (a == null) return@LazyColumn
                val v = a.valorisation
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
                        if (a.operations.any { it.sens == Sens.VENTE }) {
                            LigneInfo("Plus-values réalisées (ventes)", Format.eurosSigne(v.plusValuesRealisees), Couleurs.signe(v.plusValuesRealisees))
                        }
                        LigneInfo("Frais de courtage simulés", Format.fraisTexte(a.portefeuille.frais))
                    }
                }
                item(key = "perf-${a.id}") { PerformanceCarte(vm, a) }
                item {
                    if (a.auto) {
                        Carte {
                            Text("Géré automatiquement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Chaque début de semaine, l'analyse du soir vend les titres sortis des tops et achète les nouveaux " +
                                    "entrants à parts égales (5 France, 5 Europe, 5 États-Unis), au cours de clôture." +
                                    (vm.selection.value?.auto?.dernierReequilibrage?.let { j ->
                                        runCatching { " Dernière mise à jour : " + Format.jour(java.time.LocalDate.parse(j)) + "." }.getOrDefault("")
                                    } ?: ""),
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    } else {
                        Button(onClick = { vm.ouvrir(Ecran.Recherche) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Outlined.Add, null)
                            Text("  Acheter un titre (action ou ETF)")
                        }
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

    if (selecteur) {
        SelecteurPortefeuille(tous, a?.id, choisir = { vm.choisirPortefeuille(it); selecteur = false },
            nouveau = { selecteur = false; dialogue = DialogueP.Nouveau }, fermer = { selecteur = false })
    }
    when (val d = dialogue) {
        null -> {}
        DialogueP.Nouveau -> DialoguePortefeuille(
            null, nomPropose = "Portefeuille ${tous.count { !it.auto } + 1}", fraisProposes = a?.portefeuille,
            fermer = { dialogue = null },
        ) { nom, capital, pct, min -> vm.creerPortefeuille(nom, capital, pct, min) }
        is DialogueP.Modifier -> DialoguePortefeuille(d.p, fermer = { dialogue = null }) { nom, capital, pct, min ->
            vm.modifierPortefeuille(d.p.copy(nom = nom, capital = capital, fraisPct = pct, fraisMin = min))
        }
        is DialogueP.Reinitialiser -> AlertDialog(
            onDismissRequest = { dialogue = null },
            title = { Text("Réinitialiser « ${d.p.nom} » ?") },
            text = { Text("Toutes ses transactions seront effacées ; il repartira de son capital de départ. Vous pourrez annuler pendant 5 secondes.") },
            confirmButton = { TextButton(onClick = { vm.reinitialiserPortefeuille(d.p.id); dialogue = null }) { Text("Réinitialiser") } },
            dismissButton = { TextButton(onClick = { dialogue = null }) { Text("Annuler") } },
        )
        is DialogueP.Supprimer -> AlertDialog(
            onDismissRequest = { dialogue = null },
            title = { Text("Supprimer « ${d.p.nom} » ?") },
            text = { Text("Le portefeuille et toutes ses transactions seront supprimés. Vous pourrez annuler pendant 5 secondes.") },
            confirmButton = { TextButton(onClick = { vm.supprimerPortefeuille(d.p.id); dialogue = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { dialogue = null }) { Text("Annuler") } },
        )
    }
}

private sealed interface DialogueP {
    data object Nouveau : DialogueP
    data class Modifier(val p: PortefeuilleEntity) : DialogueP
    data class Reinitialiser(val p: PortefeuilleEntity) : DialogueP
    data class Supprimer(val p: PortefeuilleEntity) : DialogueP
}

/** Liste des portefeuilles avec leur valeur ; toucher pour l'afficher. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelecteurPortefeuille(tous: List<VuePortefeuille>, actif: Long?, choisir: (Long) -> Unit, nouveau: () -> Unit, fermer: () -> Unit) {
    ModalBottomSheet(onDismissRequest = fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Mes portefeuilles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            for (v in tous) {
                val valo = v.valorisation
                ListItem(
                    headlineContent = { Text(v.portefeuille.nom, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(Format.eurosSigne(valo.plusValueEur) + " (" + Format.pct(valo.plusValuePct) + ") · " +
                            "${valo.lignes.size} position${if (valo.lignes.size > 1) "s" else ""}",
                            color = Couleurs.signe(valo.plusValueEur))
                    },
                    leadingContent = {
                        if (v.id == actif) Icon(Icons.Outlined.Check, "Affiché", tint = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.width(24.dp))
                    },
                    trailingContent = { Text(Format.euros(valo.total), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.clickable { choisir(v.id) },
                )
            }
            ListItem(
                headlineContent = { Text("Nouveau portefeuille", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(onClick = nouveau),
            )
        }
    }
}

/** Création ou modification d'un portefeuille : nom, capital de départ et frais de courtage simulés. */
@Composable
private fun DialoguePortefeuille(
    initial: PortefeuilleEntity?,
    nomPropose: String = "",
    fraisProposes: PortefeuilleEntity? = null,
    fermer: () -> Unit,
    valider: (String, Double, Double, Double) -> Unit,
) {
    var nom by remember { mutableStateOf(initial?.nom ?: nomPropose) }
    var capital by remember { mutableStateOf(Format.nombreSimple(initial?.capital ?: 10_000.0)) }
    var pct by remember { mutableStateOf(Format.nombreSimple(initial?.fraisPct ?: fraisProposes?.fraisPct ?: 0.1)) }
    var min by remember { mutableStateOf(Format.nombreSimple(initial?.fraisMin ?: fraisProposes?.fraisMin ?: 1.0)) }
    val c = Format.lireNombre(capital)
    val p = Format.lireNombre(pct)
    val m = Format.lireNombre(min)
    val ok = nom.isNotBlank() && c != null && c > 0 && p != null && p < 100 && m != null
    fun filtre(s: String) = s.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(if (initial == null) "Nouveau portefeuille" else "Portefeuille") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nom, { nom = it.take(40) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(capital, { capital = filtre(it) }, label = { Text("Capital de départ (€)") }, singleLine = true,
                    isError = c == null || c <= 0, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Text("Frais de courtage simulés : pourcentage du montant, avec un minimum par ordre.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pct, { pct = filtre(it) }, label = { Text("Frais (%)") }, singleLine = true, isError = p == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(min, { min = filtre(it) }, label = { Text("Minimum (€)") }, singleLine = true, isError = m == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                if (initial != null) {
                    Text("Les frais déjà payés ne changent pas ; les nouveaux s'appliquent aux prochains ordres.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { valider(nom, c!!, p!!, m!!); fermer() }) {
                Text(if (initial == null) "Créer" else "Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = fermer) { Text("Annuler") } },
    )
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
    val actif by vm.actif.collectAsState()
    val ops = actif?.operations.orEmpty()
    var aSupprimer by remember { mutableStateOf<Operation?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Historique · " + (actif?.portefeuille?.nom ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            if (actif?.auto != true) {
                                IconButton(onClick = { aSupprimer = op }) { Icon(Icons.Outlined.Delete, "Supprimer") }
                            }
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
