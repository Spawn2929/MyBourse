package fr.mybourse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mybourse.app.data.FAMILLES
import fr.mybourse.app.data.Ligne
import fr.mybourse.app.data.NOTES_TECHNIQUES
import fr.mybourse.app.data.Point
import fr.mybourse.app.data.Sens
import fr.mybourse.app.data.quantitePourMontant
import fr.mybourse.app.data.simulerAchat
import fr.mybourse.app.data.simulerVente
import fr.mybourse.app.data.niveauVolatilite
import fr.mybourse.app.ui.theme.Couleurs
import java.time.LocalDate

private val PERIODES = listOf("1 mois" to 1L, "6 mois" to 6L, "1 an" to 12L, "2 ans" to 24L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheEcran(vm: MainViewModel, ticker: String) {
    val fiches by vm.fiches.collectAsState()
    val selection by vm.selection.collectAsState()
    val cours by vm.cours.collectAsState()
    val portefeuilles by vm.portefeuilles.collectAsState()
    val actif by vm.actif.collectAsState()
    val etat = fiches[ticker] ?: FicheEtat()
    val titre = selection?.titres?.get(ticker)
    val cot = etat.cotation
    val connu = vm.dernierCours(ticker, cours, selection)
    val nom = cot?.nom ?: titre?.nom ?: connu?.nom ?: ticker
    val devise = connu?.devise ?: cot?.devise ?: titre?.devise ?: "EUR"
    val taux = vm.taux(devise, cours, selection)
    // Portefeuilles qui détiennent ce titre
    val detenteurs = portefeuilles.mapNotNull { v -> v.ligne(ticker)?.let { v to it } }
    var periode by rememberSaveable { mutableStateOf(6L) }
    var ordre by remember { mutableStateOf<Sens?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(nom, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = { vm.retour() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
        if (etat.chargement) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // En-tête : code, place, cours
            Column {
                Text(
                    listOfNotNull(ticker, cot?.bourse?.takeIf { it.isNotBlank() }, cot?.type).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (connu != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Format.prix(connu.prix, devise), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        if (devise != "EUR" && taux != null) {
                            Text("  ≈ " + Format.euros(connu.prix * taux), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    val variation = titre?.var1j
                    Text(
                        (variation?.let { Format.pct(it) + " sur la séance · " } ?: "") +
                            "cours " + (connu.heure?.let { Format.quand(it) } ?: "de clôture du " + Format.dateCourte(connu.date)),
                        style = MaterialTheme.typography.bodySmall, color = Couleurs.signe(variation),
                    )
                } else if (!etat.chargement) {
                    Text("Cours indisponible", style = MaterialTheme.typography.bodyLarge)
                }
                etat.erreur?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }

            // Graphique
            val historique: List<Point> = cot?.historique?.takeIf { it.size > 1 } ?: titre?.points.orEmpty()
            Carte {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((lib, mois) in PERIODES) {
                        FilterChip(selected = periode == mois, onClick = { periode = mois }, label = { Text(lib) })
                    }
                }
                val depuis = LocalDate.now().minusMonths(periode)
                Graphique(
                    listOf(Serie(nom, historique.filter { !it.date.isBefore(depuis) }, MaterialTheme.colorScheme.primary)),
                    Modifier.padding(top = 8.dp),
                ) { Format.prix(it, devise) }
            }

            // Position détenue
            for ((v, l) in detenteurs) {
                CartePosition(l, if (portefeuilles.size > 1) "Ma position · " + v.portefeuille.nom else "Ma position")
            }

            // Analyse
            if (titre != null) {
                Carte {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PastilleScore(titre.score, 56.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Score global", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                libelleMarcheCourt(titre.marche) +
                                    (if (titre.grande) " · grande valeur, hors des tops" else "") +
                                    (titre.capi?.let { " · " + Format.capi(it) } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    for ((cle, lib) in FAMILLES) {
                        titre.scores[cle]?.let { BarreNote(lib, it) }
                    }
                    if (titre.raisons.isNotEmpty()) {
                        Text("Pourquoi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        for (r in titre.raisons) Text("• $r", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (titre.notesTechnique.isNotEmpty()) {
                    Carte {
                        Text("Détail des signaux techniques", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        for ((cle, lib, aide) in NOTES_TECHNIQUES) {
                            titre.notesTechnique[cle]?.let { BarreNote(lib, it * 100, aide) }
                        }
                    }
                }
                Carte {
                    Text("Indicateurs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val i = titre.indicateurs
                    for ((cle, lib) in listOf("perf_1m" to "Sur 1 mois", "perf_3m" to "Sur 3 mois", "perf_6m" to "Sur 6 mois", "perf_1a" to "Sur 1 an")) {
                        i[cle]?.let { LigneInfo(lib, Format.pct(it), Couleurs.signe(it)) }
                    }
                    i["volatilite"]?.let { LigneInfo("Volatilité (1 an)", "±" + Format.score(it) + " % · " + niveauVolatilite(it)) }
                    i["rsi"]?.let { LigneInfo("RSI (14 jours)", Format.score(it)) }
                    i["mm50"]?.let { LigneInfo("Moyenne 50 jours", Format.prix(it, titre.devise)) }
                    i["mm200"]?.let { LigneInfo("Moyenne 200 jours", Format.prix(it, titre.devise)) }
                }
            } else {
                Carte {
                    Text("Titre non analysé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ce titre ne fait pas partie des actions analysées chaque soir (France, Europe et États-Unis, " +
                            "plus de 200 M€ de capitalisation, assez échangées) ni de « Ma liste » : vous pouvez l'acheter, " +
                            "mais il n'a pas de score. Pour qu'il soit analysé, ajoutez « $ticker » au fichier " +
                            "pipeline/ma_liste.txt sur GitHub.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Boutons d'ordre, toujours visibles
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (detenteurs.isNotEmpty()) {
                OutlinedButton(onClick = { ordre = Sens.VENTE }, enabled = connu != null, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Vendre")
                }
            }
            Button(onClick = { ordre = Sens.ACHAT }, enabled = connu != null, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Acheter (virtuel)")
            }
        }
    }

    val o = ordre
    if (o != null && connu != null) {
        val choix = if (o == Sens.VENTE) detenteurs.map { it.first } else portefeuilles
        if (choix.isNotEmpty()) OrdreFeuille(vm, o, ticker, nom, devise, connu.prix, taux, choix, actif?.id) { ordre = null }
    }
}

@Composable
fun CartePosition(l: Ligne, titre: String = "Ma position") {
    val p = l.position
    Carte {
        Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LigneInfo("Quantité", Format.quantite(p.quantite))
        LigneInfo("Prix de revient (frais compris)", Format.euros(p.pruEur) + " / titre")
        LigneInfo("Montant investi", Format.euros(p.coutEur))
        l.valeurEur?.let { LigneInfo("Valeur actuelle", Format.euros(it), gras = true) }
        l.plusValueEur?.let { pv ->
            LigneInfo("Plus ou moins-value", Format.eurosSigne(pv) + (l.plusValuePct?.let { " (" + Format.pct(it) + ")" } ?: ""),
                Couleurs.signe(pv), gras = true)
        }
    }
}

/** Feuille d'achat ou de vente : choix du portefeuille, saisie, calcul des frais et du total avant validation. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OrdreFeuille(
    vm: MainViewModel, sens: Sens, ticker: String, nom: String, devise: String, prix: Double, taux: Double?,
    choix: List<VuePortefeuille>, defaut: Long?, fermer: () -> Unit,
) {
    var pid by rememberSaveable { mutableLongStateOf(choix.firstOrNull { it.id == defaut }?.id ?: choix.first().id) }
    val vue = choix.firstOrNull { it.id == pid } ?: choix.first()
    val ligne = vue.ligne(ticker)
    val frais = vue.portefeuille.frais
    val liquidites = vue.valorisation.liquidites
    var enMontant by rememberSaveable { mutableStateOf(sens == Sens.ACHAT) }
    var saisie by rememberSaveable { mutableStateOf("") }
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val valeur = Format.lireNombre(saisie)
    val quantite = when {
        valeur == null -> 0
        enMontant && taux != null -> quantitePourMontant(valeur, prix, taux, frais)
        enMontant -> 0
        else -> valeur.toInt().takeIf { it.toDouble() == valeur } ?: -1
    }
    val sim = when {
        taux == null -> null
        sens == Sens.ACHAT -> simulerAchat(quantite, prix, taux, frais, liquidites)
        else -> simulerVente(ligne?.position, quantite, prix, taux, frais)
    }

    ModalBottomSheet(onDismissRequest = fermer, sheetState = etat) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (sens == Sens.ACHAT) "Acheter $nom" else "Vendre $nom",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Au dernier cours connu : " + Format.prix(prix, devise) +
                    (if (devise != "EUR" && taux != null) " (≈ " + Format.euros(prix * taux) + ")" else ""),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (choix.size > 1) {
                Text("Dans quel portefeuille ?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in choix) {
                        FilterChip(selected = v.id == pid, onClick = { pid = v.id }, label = { Text(v.portefeuille.nom) })
                    }
                }
            }
            if (sens == Sens.ACHAT) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(enMontant, { enMontant = true; saisie = "" }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Montant en €") }
                    SegmentedButton(!enMontant, { enMontant = false; saisie = "" }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Nombre de titres") }
                }
            }
            OutlinedTextField(
                value = saisie,
                onValueChange = { saisie = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                label = { Text(if (enMontant) "Montant maximum (€)" else "Nombre de titres") },
                supportingText = {
                    Text(
                        if (sens == Sens.ACHAT) "Liquidités disponibles : " + Format.euros(liquidites)
                        else "Titres détenus : " + Format.quantite(ligne?.position?.quantite ?: 0),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = if (enMontant) KeyboardType.Decimal else KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (sens == Sens.VENTE && ligne != null) {
                TextButton(onClick = { saisie = ligne.position.quantite.toString() }) { Text("Tout vendre") }
            }
            if (taux == null) {
                Text("Taux de change indisponible : vérifiez la connexion.", color = MaterialTheme.colorScheme.error)
            } else if (sim != null && quantite > 0) {
                Carte {
                    LigneInfo("Quantité", Format.quantite(sim.quantite))
                    LigneInfo("Montant brut", Format.euros(sim.brutEur))
                    LigneInfo("Frais de courtage simulés", Format.euros(sim.fraisEur))
                    LigneInfo(if (sens == Sens.ACHAT) "Total débité" else "Total crédité", Format.euros(sim.totalEur), gras = true)
                    sim.plusValue?.let { LigneInfo("Plus ou moins-value réalisée", Format.eurosSigne(it), Couleurs.signe(it), gras = true) }
                }
            } else if (valeur != null && valeur > 0 && enMontant) {
                Text("Montant insuffisant pour acheter un titre, frais compris.", color = MaterialTheme.colorScheme.error)
            } else if (quantite < 0) {
                Text("Indiquez un nombre entier de titres.", color = MaterialTheme.colorScheme.error)
            }
            sim?.erreur?.takeIf { quantite > 0 }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    if (sens == Sens.ACHAT) vm.acheter(pid, ticker, nom, devise, quantite, prix) else vm.vendre(pid, ticker, quantite, prix)
                    fermer()
                },
                enabled = sim?.possible == true && quantite > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (sens == Sens.ACHAT) "Confirmer l'achat" else "Confirmer la vente") }
        }
    }
}
