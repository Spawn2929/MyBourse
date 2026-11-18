package fr.mybourse.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.mybourse.app.BuildConfig
import fr.mybourse.app.data.BaseMyBourse
import fr.mybourse.app.data.Cotation
import fr.mybourse.app.data.CoursEntity
import fr.mybourse.app.data.Devises
import fr.mybourse.app.data.Ligne
import fr.mybourse.app.data.Operation
import fr.mybourse.app.data.OperationEntity
import fr.mybourse.app.data.OperationSauvee
import fr.mybourse.app.data.Reglages
import fr.mybourse.app.data.ReglagesStore
import fr.mybourse.app.data.Reseau
import fr.mybourse.app.data.ResultatRecherche
import fr.mybourse.app.data.Sauvegarde
import fr.mybourse.app.data.Selection
import fr.mybourse.app.data.Sens
import fr.mybourse.app.data.Valorisation
import fr.mybourse.app.data.bilan
import fr.mybourse.app.data.peutSupprimer
import fr.mybourse.app.data.simulerAchat
import fr.mybourse.app.data.simulerVente
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sous-écrans ouverts par-dessus les onglets (le geste retour les ferme un par un). */
sealed interface Ecran {
    data class Fiche(val ticker: String) : Ecran
    data object Recherche : Ecran
    data object Historique : Ecran
}

/** Message affiché en bas de l'écran, avec éventuellement un bouton « Annuler ». */
data class Message(val texte: String, val annuler: (suspend () -> Unit)? = null)

/** Dernier cours connu d'un titre, et d'où il vient. */
data class CoursConnu(val prix: Double, val devise: String, val nom: String, val heure: Long?, val date: LocalDate)

data class FicheEtat(
    val cotation: Cotation? = null,
    val chargement: Boolean = true,
    val erreur: String? = null,
)

data class Recherche(
    val requete: String = "",
    val resultats: List<ResultatRecherche> = emptyList(),
    val chargement: Boolean = false,
    val erreur: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = BaseMyBourse.get(app).dao()
    private val store = ReglagesStore(app)
    private val cache = File(app.filesDir, "selection.json")

    val reglages: StateFlow<Reglages?> = store.flux.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selection = MutableStateFlow<Selection?>(null)
    val chargement = MutableStateFlow(false)
    val erreurSelection = MutableStateFlow<String?>(null)
    private val messages = Channel<Message>(Channel.BUFFERED)
    val flux: kotlinx.coroutines.flow.Flow<Message> = messages.receiveAsFlow()

    val operations: StateFlow<List<Operation>> = dao.operations().map { l -> l.map { it.versOperation() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cours: StateFlow<Map<String, CoursEntity>> = dao.cours().map { l -> l.associateBy { it.ticker } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val valorisation: StateFlow<Valorisation?> = combine(reglages, operations, cours, selection) { r, ops, c, s ->
        if (r == null) return@combine null
        val b = bilan(r.capital, ops)
        Valorisation(
            capital = r.capital,
            liquidites = b.liquidites,
            lignes = b.positions.map { p ->
                val cc = dernierCours(p.ticker, c, s)
                Ligne(p, cc?.prix, taux(p.devise, c, s))
            },
            plusValuesRealisees = b.plusValuesRealisees,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pile = mutableStateListOf<Ecran>()
    val fiches = MutableStateFlow<Map<String, FicheEtat>>(emptyMap())

    private val requete = MutableStateFlow("")
    val recherche = MutableStateFlow(Recherche())

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Selection.lire(cache.readText()) }.getOrNull() }
                ?.let { selection.value = it }
            rafraichir()
        }
        viewModelScope.launch {
            requete.debounce(400).mapLatest { q -> chercher(q) }.collect { }
        }
    }

    // ---------- Cours et taux ----------

    /** Le plus récent entre le cours Yahoo enregistré et celui de l'analyse du soir. */
    fun dernierCours(ticker: String, c: Map<String, CoursEntity> = cours.value, s: Selection? = selection.value): CoursConnu? {
        val enr = c[ticker]?.let {
            CoursConnu(it.prix, it.devise, it.nom, it.heure, Instant.ofEpochMilli(it.heure).atZone(Format.PARIS).toLocalDate())
        }
        val sel = s?.titres?.get(ticker)?.takeIf { it.cours > 0 }?.let {
            CoursConnu(it.cours, it.devise, it.nom, null, runCatching { LocalDate.parse(it.date) }.getOrDefault(LocalDate.MIN))
        }
        return when {
            enr == null -> sel
            sel == null -> enr
            sel.date.isAfter(enr.date) -> sel
            else -> enr
        }
    }

    /** Euros pour 1 unité de la devise ; null si inconnu. */
    fun taux(devise: String, c: Map<String, CoursEntity> = cours.value, s: Selection? = selection.value): Double? {
        val (base, div) = Devises.base(devise)
        if (base == "EUR") return 1.0 / div
        val t = c["FX:$base"]?.prix ?: if (base == "USD") s?.eurParUsd else null
        return t?.let { it / div }
    }

    private suspend fun majTaux(devises: Collection<String>) {
        val bases = devises.map { Devises.base(it).first }.filter { it != "EUR" }.toSet()
        val res = coroutineScopeAll(bases) { b ->
            runCatching { CoursEntity("FX:$b", "$b → EUR", "EUR", Reseau.tauxVersEur(b), System.currentTimeMillis()) }.getOrNull()
        }
        if (res.isNotEmpty()) dao.enregistrerCours(res)
    }

    private suspend fun <T, R : Any> coroutineScopeAll(items: Collection<T>, f: suspend (T) -> R?): List<R> =
        kotlinx.coroutines.coroutineScope { items.map { async { f(it) } }.awaitAll().filterNotNull() }

    // ---------- Actualisation ----------

    fun rafraichir() {
        if (chargement.value) return
        viewModelScope.launch {
            chargement.value = true
            try {
                try {
                    val texte = Reseau.texte(BuildConfig.DATA_URL + "?t=" + System.currentTimeMillis() / 300_000)
                    val s = Selection.lire(texte)
                    withContext(Dispatchers.IO) { cache.writeText(texte) }
                    selection.value = s
                    erreurSelection.value = null
                } catch (e: Exception) {
                    erreurSelection.value = if (selection.value == null) {
                        "Impossible de charger la sélection du jour. Vérifiez la connexion puis tirez l'écran vers le bas."
                    } else {
                        "Hors connexion : affichage de la dernière sélection reçue."
                    }
                }
                val detenus = operations.value.map { it.ticker }.toSet()
                val nouveaux = coroutineScopeAll(detenus) { t ->
                    runCatching { Reseau.cotation(t, "5d") }.getOrNull()
                        ?.let { CoursEntity(t, it.nom, it.devise, it.prix, it.heure) }
                }
                if (nouveaux.isNotEmpty()) dao.enregistrerCours(nouveaux)
                majTaux(operations.value.map { it.devise } + "USD")
            } finally {
                chargement.value = false
            }
        }
    }

    // ---------- Navigation ----------

    fun ouvrir(e: Ecran) {
        pile.add(e)
        if (e is Ecran.Fiche) chargerFiche(e.ticker)
    }

    fun retour(): Boolean = if (pile.isEmpty()) false else { pile.removeAt(pile.lastIndex); true }

    fun chargerFiche(ticker: String) {
        fiches.update { it + (ticker to (it[ticker]?.copy(chargement = true, erreur = null) ?: FicheEtat())) }
        viewModelScope.launch {
            try {
                val c = Reseau.cotation(ticker, "2y")
                dao.enregistrerCours(listOf(CoursEntity(c.ticker, c.nom, c.devise, c.prix, c.heure)))
                fiches.update { it + (ticker to FicheEtat(c, chargement = false)) }
                majTaux(listOf(c.devise))
            } catch (e: Exception) {
                fiches.update {
                    it + (ticker to FicheEtat(it[ticker]?.cotation, chargement = false,
                        erreur = "Cours en direct indisponible : dernier cours connu affiché."))
                }
                if (taux(selection.value?.titres?.get(ticker)?.devise ?: "EUR") == null) {
                    majTaux(listOfNotNull(selection.value?.titres?.get(ticker)?.devise))
                }
            }
        }
    }

    // ---------- Recherche ----------

    fun chercherTexte(q: String) {
        recherche.update { it.copy(requete = q, chargement = q.isNotBlank()) }
        requete.value = q
    }

    private suspend fun chercher(q: String) {
        val texte = q.trim()
        if (texte.length < 2) {
            recherche.update { it.copy(resultats = emptyList(), chargement = false, erreur = null) }
            return
        }
        val cle = Format.cle(texte)
        val locaux = selection.value?.titres.orEmpty().filter { (t, v) ->
            Format.cle(t).startsWith(cle) || Format.cle(v.nom).contains(cle)
        }.map { (t, v) -> ResultatRecherche(t, v.nom, libelleMarcheCourt(v.marche), "Action") }.take(10)
        val distants = runCatching { Reseau.rechercher(texte) }
        val tous = (locaux + distants.getOrDefault(emptyList())).distinctBy { it.ticker }
        recherche.update {
            if (it.requete != q) it else it.copy(
                resultats = tous, chargement = false,
                erreur = if (distants.isFailure && locaux.isEmpty()) "Recherche impossible : vérifiez la connexion." else null,
            )
        }
    }

    // ---------- Ordres ----------

    fun acheter(ticker: String, nom: String, devise: String, quantite: Int, prix: Double) {
        viewModelScope.launch {
            val r = reglages.value ?: return@launch
            val change = taux(devise) ?: return@launch messages.send(Message("Taux de change indisponible : réessayez"))
            val liq = bilan(r.capital, operations.value).liquidites
            val sim = simulerAchat(quantite, prix, change, r.frais, liq)
            if (!sim.possible) return@launch messages.send(Message(sim.erreur!!))
            val id = dao.ajouter(OperationEntity.depuis(
                Operation(0, ticker, nom, devise, Sens.ACHAT, quantite, prix, change, sim.fraisEur, System.currentTimeMillis())))
            messages.send(Message("Achat enregistré : ${Format.quantite(quantite)} × $nom") { dao.supprimer(id) })
        }
    }

    fun vendre(ticker: String, quantite: Int, prix: Double) {
        viewModelScope.launch {
            val r = reglages.value ?: return@launch
            val pos = bilan(r.capital, operations.value).positions.firstOrNull { it.ticker == ticker }
            val change = taux(pos?.devise ?: "EUR") ?: return@launch messages.send(Message("Taux de change indisponible : réessayez"))
            val sim = simulerVente(pos, quantite, prix, change, r.frais)
            if (!sim.possible || pos == null) return@launch messages.send(Message(sim.erreur ?: "Vente impossible"))
            val id = dao.ajouter(OperationEntity.depuis(
                Operation(0, ticker, pos.nom, pos.devise, Sens.VENTE, quantite, prix, change, sim.fraisEur, System.currentTimeMillis())))
            messages.send(Message("Vente enregistrée : ${Format.quantite(quantite)} × ${pos.nom}") { dao.supprimer(id) })
        }
    }

    fun supprimerOperation(op: Operation) {
        viewModelScope.launch {
            val r = reglages.value ?: return@launch
            if (!peutSupprimer(r.capital, operations.value, op.id)) {
                return@launch messages.send(Message("Impossible : une vente ou un achat plus récent dépend de cette opération"))
            }
            dao.supprimer(op.id)
            messages.send(Message("Opération supprimée") { dao.ajouter(OperationEntity.depuis(op)) })
        }
    }

    // ---------- Réglages, sauvegarde ----------

    fun modifierReglages(r: Reglages) {
        viewModelScope.launch {
            if (!bilan(r.capital, operations.value).coherent) {
                return@launch messages.send(Message("Capital trop faible pour les achats déjà faits"))
            }
            store.enregistrer(r)
        }
    }

    fun reinitialiser() {
        viewModelScope.launch {
            val avant = dao.operationsListe()
            dao.vider()
            messages.send(Message("Portefeuille réinitialisé") { dao.ajouterTout(avant.map { it.copy(id = 0) }) })
        }
    }

    fun exporter(uri: Uri) {
        viewModelScope.launch {
            val r = reglages.value ?: return@launch
            val s = Sauvegarde(
                exporteLe = Instant.now().toString(),
                reglages = r,
                operations = operations.value.map { OperationSauvee.depuis(it) },
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(Sauvegarde.ecrire(s).toByteArray())
                    }
                }.isSuccess
            }
            messages.send(Message(if (ok) "Sauvegarde enregistrée (${s.operations.size} opérations)" else "Échec de l'enregistrement"))
        }
    }

    fun importer(uri: Uri) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val texte = getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().decodeToString()
                    }
                    Sauvegarde.lire(texte)
                }
            }
            res.onSuccess { s ->
                val theme = reglages.value?.theme
                dao.remplacer(s.operations.map { OperationEntity.depuis(it.versOperation()) })
                store.enregistrer(if (theme != null) s.reglages.copy(theme = theme) else s.reglages)
                messages.send(Message("Sauvegarde importée : ${s.operations.size} opérations"))
            }.onFailure { messages.send(Message(it.message ?: "Fichier illisible")) }
        }
    }

    suspend fun reglagesActuels(): Reglages = reglages.value ?: store.flux.first()
}

fun libelleMarcheCourt(m: String) = when (m) {
    "FR" -> "Paris · CAC 40"
    "US" -> "New York · S&P 500"
    else -> "Ma liste"
}
