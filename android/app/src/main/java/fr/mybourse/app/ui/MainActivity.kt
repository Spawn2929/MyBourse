package fr.mybourse.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import fr.mybourse.app.data.Theme
import fr.mybourse.app.ui.theme.MyBourseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val reglages by vm.reglages.collectAsState()
            MyBourseTheme(reglages?.theme ?: Theme.AUTO) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        vm.rafraichir()
    }
}

private data class Onglet(val titre: String, val icone: ImageVector)

private val ONGLETS = listOf(
    Onglet("Sélection", Icons.AutoMirrored.Outlined.ShowChart),
    Onglet("Portefeuille", Icons.Outlined.AccountBalanceWallet),
    Onglet("Réglages", Icons.Outlined.Settings),
)

@Composable
fun App(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { ONGLETS.size }

    // Messages : 5 secondes avec « Annuler » quand l'action peut être défaite.
    LaunchedEffect(Unit) {
        vm.flux.collect { m ->
            launch {
                snackbar.currentSnackbarData?.dismiss()
                val job = launch {
                    val r = snackbar.showSnackbar(
                        m.texte, actionLabel = if (m.annuler != null) "Annuler" else null,
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (r == SnackbarResult.ActionPerformed) m.annuler?.invoke()
                }
                delay(5000)
                snackbar.currentSnackbarData?.let { if (it.visuals.message == m.texte) it.dismiss() }
                job.join()
            }
        }
    }

    BackHandler(enabled = vm.pile.isNotEmpty()) { vm.retour() }
    BackHandler(enabled = vm.pile.isEmpty() && pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                Avertissement()
                if (vm.pile.isEmpty()) {
                    NavigationBar {
                        ONGLETS.forEachIndexed { i, o ->
                            NavigationBarItem(
                                selected = pager.currentPage == i,
                                onClick = { scope.launch { pager.animateScrollToPage(i) } },
                                icon = { Icon(o.icone, contentDescription = null) },
                                label = { Text(o.titre) },
                            )
                        }
                    }
                } else {
                    // Sous-écran : la barre de gestes reste dégagée sous le bandeau.
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    ) { padding ->
        AnimatedContent(targetState = vm.pile.lastOrNull(), label = "ecran") { ecran ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (ecran) {
                    null -> HorizontalPager(pager, beyondViewportPageCount = 1) { page ->
                        when (page) {
                            0 -> SelectionEcran(vm)
                            1 -> PortefeuilleEcran(vm)
                            else -> ReglagesEcran(vm)
                        }
                    }
                    is Ecran.Fiche -> FicheEcran(vm, ecran.ticker)
                    Ecran.Recherche -> RechercheEcran(vm)
                    Ecran.Historique -> HistoriqueEcran(vm)
                }
            }
        }
    }
}
