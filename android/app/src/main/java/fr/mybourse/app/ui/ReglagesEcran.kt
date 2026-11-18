package fr.mybourse.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.mybourse.app.BuildConfig
import fr.mybourse.app.data.Marches
import fr.mybourse.app.data.Reglages
import fr.mybourse.app.data.Theme
import java.time.LocalDate

@Composable
fun ReglagesEcran(vm: MainViewModel) {
    val reglages by vm.reglages.collectAsState()
    val r = reglages ?: return
    var dialogue by remember { mutableStateOf<String?>(null) }
    var aImporter by remember { mutableStateOf<android.net.Uri?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exporter)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> aImporter = uri }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        Carte {
            Titre("Portefeuille virtuel")
            ChampNombre("Capital de départ (€)", r.capital) { vm.modifierReglages(r.copy(capital = it)) }
            Text("Frais de courtage simulés : pourcentage du montant, avec un minimum par ordre.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChampNombre("Frais (%)", r.fraisPct, Modifier.weight(1f)) { vm.modifierReglages(r.copy(fraisPct = it)) }
                ChampNombre("Minimum (€)", r.fraisMin, Modifier.weight(1f)) { vm.modifierReglages(r.copy(fraisMin = it)) }
            }
        }

        Carte {
            Titre("Marchés affichés dans la sélection")
            for ((m, lib) in Marches.libelles) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(m in r.marches, onCheckedChange = { c ->
                        vm.modifierReglages(r.copy(marches = if (c) r.marches + m else r.marches - m))
                    })
                    Text(lib, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                "Ajouter des titres à « Ma liste » ou changer le nombre d'actions retenues : fichiers " +
                    "pipeline/ma_liste.txt et pipeline/config.yaml sur GitHub (prise en compte le soir même).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Carte {
            Titre("Apparence")
            for (t in Theme.entries) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(r.theme == t, onClick = { vm.modifierReglages(r.copy(theme = t)) })
                    Text(t.libelle, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Carte {
            Titre("Sauvegarde")
            Text("Le portefeuille est enregistré uniquement sur ce téléphone. Exportez-le de temps en temps (Drive, e-mail…).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { exporter.launch("mybourse-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Exporter")
                }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.weight(1f).height(48.dp)) { Text("Importer") }
            }
        }

        Carte {
            Titre("Réinitialisation")
            Text("Efface toutes les transactions et repart du capital de départ.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { dialogue = "reinit" }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Réinitialiser le portefeuille", color = MaterialTheme.colorScheme.error)
            }
        }

        Text(
            "MyBourse ${BuildConfig.VERSION_NAME} · données : Yahoo Finance (via l'analyse du soir et en direct).\n$AVERTISSEMENT",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (dialogue == "reinit") {
        AlertDialog(
            onDismissRequest = { dialogue = null },
            title = { Text("Réinitialiser le portefeuille ?") },
            text = { Text("Toutes les transactions seront effacées. Vous pourrez annuler pendant 5 secondes.") },
            confirmButton = { TextButton(onClick = { vm.reinitialiser(); dialogue = null }) { Text("Réinitialiser") } },
            dismissButton = { TextButton(onClick = { dialogue = null }) { Text("Annuler") } },
        )
    }
    aImporter?.let { uri ->
        AlertDialog(
            onDismissRequest = { aImporter = null },
            title = { Text("Importer cette sauvegarde ?") },
            text = { Text("Le portefeuille actuel et ses réglages seront remplacés par ceux du fichier.") },
            confirmButton = { TextButton(onClick = { vm.importer(uri); aImporter = null }) { Text("Importer") } },
            dismissButton = { TextButton(onClick = { aImporter = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Titre(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))

/** Champ numérique : enregistré en quittant le champ ou avec « OK » du clavier (virgule acceptée). */
@Composable
private fun ChampNombre(libelle: String, valeur: Double, modifier: Modifier = Modifier.fillMaxWidth(), onValide: (Double) -> Unit) {
    var texte by remember(valeur) { mutableStateOf(nombreSimple(valeur)) }
    val focus = LocalFocusManager.current
    val v = Format.lireNombre(texte)
    fun valider() {
        val n = Format.lireNombre(texte)
        if (n != null && n >= 0 && n != valeur) onValide(n) else texte = nombreSimple(valeur)
    }
    var avaitFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = texte,
        onValueChange = { s -> texte = s.filter { it.isDigit() || it == ',' || it == '.' } },
        label = { Text(libelle) },
        isError = v == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        singleLine = true,
        modifier = modifier.onFocusChanged { f ->
            if (avaitFocus && !f.isFocused) valider()
            avaitFocus = f.isFocused
        },
    )
}

private fun nombreSimple(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else v.toString().replace('.', ',')
