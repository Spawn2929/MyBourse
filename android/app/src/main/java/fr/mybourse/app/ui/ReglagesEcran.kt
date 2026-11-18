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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.mybourse.app.BuildConfig
import fr.mybourse.app.data.Marches
import fr.mybourse.app.data.Theme
import java.time.LocalDate

@Composable
fun ReglagesEcran(vm: MainViewModel) {
    val reglages by vm.reglages.collectAsState()
    val portefeuilles by vm.portefeuilles.collectAsState()
    val r = reglages ?: return
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
            Titre("Zones affichées dans la sélection")
            for ((m, lib) in Marches.libelles) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(m !in r.masques, onCheckedChange = { c ->
                        vm.modifierReglages(r.copy(masques = if (c) r.masques - m else r.masques + m))
                    })
                    Text(lib, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                "Chaque soir, l'analyse classe les valeurs moyennes et petites (de 200 M€ à 10 Md€ de capitalisation, " +
                    "au moins 500 000 € échangés par jour). Les grandes valeurs (CAC 40, S&P 500, plus de 10 Md€) restent " +
                    "analysées et achetables, mais hors des tops.\n\nAjouter des titres à « Ma liste » ou changer le nombre " +
                    "d'actions retenues : fichiers pipeline/ma_liste.txt et pipeline/config.yaml sur GitHub.",
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
            Titre("Portefeuilles")
            Text(
                "Capital de départ et frais de courtage : propres à chaque portefeuille, dans l'onglet Portefeuille " +
                    "(menu ⋮ > « Nom, capital et frais »).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Carte {
            Titre("Sauvegarde")
            Text(
                "Les ${portefeuilles.size} portefeuille${if (portefeuilles.size > 1) "s sont enregistrés" else " est enregistré"} " +
                    "uniquement sur ce téléphone. Exportez-les de temps en temps (Drive, e-mail…).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { exporter.launch("mybourse-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Exporter")
                }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.weight(1f).height(48.dp)) { Text("Importer") }
            }
        }

        Text(
            "MyBourse ${BuildConfig.VERSION_NAME} · données : Yahoo Finance (via l'analyse du soir et en direct).\n$AVERTISSEMENT",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    aImporter?.let { uri ->
        AlertDialog(
            onDismissRequest = { aImporter = null },
            title = { Text("Importer cette sauvegarde ?") },
            text = { Text("Tous les portefeuilles actuels seront remplacés par ceux du fichier.") },
            confirmButton = { TextButton(onClick = { vm.importer(uri); aImporter = null }) { Text("Importer") } },
            dismissButton = { TextButton(onClick = { aImporter = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Titre(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
