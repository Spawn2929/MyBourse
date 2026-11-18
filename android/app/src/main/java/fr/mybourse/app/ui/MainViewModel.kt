package fr.mybourse.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.mybourse.app.BuildConfig
import fr.mybourse.app.data.Backtest
import fr.mybourse.app.data.BaseMyBourse
import fr.mybourse.app.data.Cotation
import fr.mybourse.app.data.CoursEntity
import fr.mybourse.app.data.Devises
import fr.mybourse.app.data.Ligne
import fr.mybourse.app.data.Operation
import fr.mybourse.app.data.OperationEntity
import fr.mybourse.app.data.PortefeuilleEntity
import fr.mybourse.app.data.PortefeuilleSauve
import fr.mybourse.app.data.Reglages
import fr.mybourse.app.data.ReglagesStore
import fr.mybourse.app.data.Reseau
import fr.mybourse.app.data.ResultatRecherche
import fr.mybourse.app.data.Sauvegarde
import fr.mybourse.app.data.Selection
import fr.mybourse.app.data.Sens
import fr.mybourse.app.data.Valorisation
import fr.mybourse.app.data.Point
import fr.mybourse.app.data.bilan
import fr.mybourse.app.data.jourDe
import fr.mybourse.app.data.periodePour
import fr.mybourse.app.data.valeursQuotidiennes
import fr.mybourse.app.data.peutSupprimer
import fr.mybourse.app.data.simulerAchat
import fr.mybourse.app.data.simulerVente
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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

/** Un portefeuille, ses opérations et sa valeur au dernier cours connu. */
data class VuePortefeuille(
    val portefeuille: PortefeuilleEntity,
    val operations: List<Operation>,
    val valorisation: Valorisation,
) {
    val id: Long get() = portefeuille.id
    /** Portefeuille automatique (géré par l'analyse du soir). */
    val auto: Boolean get() = portefeuille.id == AUTO_ID
    /** Portefeuille du backtest (la méthode rejouée sur les années passées). */
    val test: Boolean get() = portefeuille.id == TEST_ID
    /** Portefeuille calculé par GitHub : en lecture seule, ni achat, ni vente, ni modification. */
    val robot: Boolean get() = portefeuille.id < 0
    fun ligne(ticker: String): Ligne? = valorisation.lignes.firstOrNull { it.position.ticker == ticker }
}

const val AUTO_ID = -1L
const val TEST_ID = -2L

/** Courbe de performance d'un portefeuille et des indices, en % depuis le début de la période. */
data class EtatPerformance(
    val valeurs: List<Point> = emptyList(),
    val chargement: Boolean = false,
    val erreur: String? = null,
    /** Pour ne recalculer que si les opérations ont changé. */
    val cle: String = "",
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = BaseMyBourse.get(app).dao()
    private val store = ReglagesStore(app)
    private val cache = File(app.filesDir, "selection.json")
    private val cacheTest = File(app.filesDir, "backtest.json")

    val reglages: StateFlow<Reglages?> = store.flux.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selection = MutableStateFlow<Selection?>(null)
    /** Dernier backtest publié (null tant qu'aucun n'a été calculé). */
    val test = MutableStateFlow<Backtest?>(null)
    val chargement = MutableStateFlow(false)
    val erreurSelection = MutableStateFlow<String?>(null)
    private val messages = Channel<Message>(Channel.BUFFERED)
    val flux: Flow<Message> = messages.receiveAsFlow()

    private val portefeuillesBruts = dao.portefeuilles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val toutesOperations: StateFlow<List<Operation>> = dao.operations().map { l -> l.map { it.versOperation() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cours: StateFlow<Map<String, CoursEntity>> = dao.cours().map { l -> l.associateBy { it.ticker } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Tous les portefeuilles, valorisés, dans l'ordre de création. */
    val portefeuilles: StateFlow<List<VuePortefeuille>> =
        combine(portefeuillesBruts, toutesOperations, cours, selection, test) { ps, ops, c, s, bt ->
            val parPortefeuille = ops.groupBy { it.portefeuille }
            fun vue(p: PortefeuilleEntity, o: List<Operation>): VuePortefeuille {
                val b = bilan(p.capital, o)
                return VuePortefeuille(
                    p, o,
                    Valorisation(
                        capital = p.capital,
                        liquidites = b.liquidites,
                        lignes = b.positions.map { pos -> Ligne(pos, dernierCours(pos.ticker, c, s)?.prix, taux(pos.devise, c, s)) },
                        plusValuesRealisees = b.plusValuesRealisees,
                    ),
                )
            }
            val auto = s?.auto?.takeIf { it.operations.isNotEmpty() }?.let { a ->
                vue(PortefeuilleEntity(AUTO_ID, "🤖 Automatique", a.capital, a.fraisPct, a.fraisMin, Long.MAX_VALUE),
                    a.versOperations(AUTO_ID))
            }
            val essai = bt?.portefeuille?.takeIf { it.operations.isNotEmpty() }?.let { a ->
                vue(PortefeuilleEntity(TEST_ID, "🧪 Test sur ${bt.annees} ans", a.capital, a.fraisPct, a.fraisMin, Long.MAX_VALUE),
                    a.versOperations(TEST_ID))
            }
            ps.map { p -> vue(p, parPortefeuille[p.id].orEmpty()) } + listOfNotNull(auto, essai)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Portefeuille affiché dans l'onglet Portefeuille. */
    val actif: StateFlow<VuePortefeuille?> = combine(portefeuilles, reglages) { ps, r ->
        ps.firstOrNull { it.id == r?.actif } ?: ps.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pile = mutableStateListOf<Ecran>()
    val fiches = MutableStateFlow<Map<String, FicheEtat>>(emptyMap())

    private val requete = MutableStateFlow("")
    val recherche = MutableStateFlow(Recherche())

    init {
        viewModelScope.launch {
            // Premier lancement (ou passage de la v0.1) : le portefeuille « Principal » reprend l'ancien capital et les anciens frais
            if (dao.portefeuillesListe().isEmpty()) {
                val a = store.anciens()
                dao.enregistrerPortefeuille(PortefeuilleEntity(1, "Principal", a.capital, a.fraisPct, a.fraisMin, System.currentTimeMillis()))
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.Default) { runCatching { Selection.lire(cache.readText()) }.getOrNull() }
                ?.let { selection.value = it }
            withContext(Dispatchers.Default) { runCatching { Backtest.lire(cacheTest.readText()) }.getOrNull() }
                ?.let { test.value = it }
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
        val res = enParallele(bases) { b ->
            runCatching { CoursEntity("FX:$b", "$b → EUR", "EUR", Reseau.tauxVersEur(b), System.currentTimeMillis()) }.getOrNull()
        }
        if (res.isNotEmpty()) dao.enregistrerCours(res)
    }

    private suspend fun <T, R : Any> enParallele(items: Collection<T>, f: suspend (T) -> R?): List<R> =
        kotlinx.coroutines.coroutineScope { items.map { async { f(it) } }.awaitAll().filterNotNull() }

    // ---------- Actualisation ----------

    fun rafraichir() {
        if (chargement.value) return
        viewModelScope.launch {
            chargement.value = true
            try {
                try {
                    val texte = Reseau.texte(BuildConfig.DATA_URL + "?t=" + System.currentTimeMillis() / 300_000)
                    val s = withContext(Dispatchers.Default) { Selection.lire(texte) }
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
                try {
                    val texte = Reseau.texte(BuildConfig.DATA_URL.replace("selection.json", "backtest.json") +
                        "?t=" + System.currentTimeMillis() / 300_000)
                    test.value = withContext(Dispatchers.Default) { Backtest.lire(texte) }
                    withContext(Dispatchers.IO) { cacheTest.writeText(texte) }
                } catch (_: Exception) {
                    // Pas encore de backtest publié, ou hors connexion : on garde le dernier reçu
                }
                // Cours du jour : titres de tes portefeuilles, et positions actuelles des portefeuilles calculés
                val robots = listOfNotNull(selection.value?.auto, test.value?.portefeuille).flatMap { a ->
                    bilan(a.capital, a.versOperations(AUTO_ID)).positions.map { it.ticker }
                }
                val ops = toutesOperations.value
                val detenus = ops.map { it.ticker }.toSet() + robots
                val nouveaux = enParallele(detenus) { t ->
                    runCatching { Reseau.cotation(t, "5d") }.getOrNull()
                        ?.let { CoursEntity(t, it.nom, it.devise, it.prix, it.heure) }
                }
                if (nouveaux.isNotEmpty()) dao.enregistrerCours(nouveaux)
                majTaux(ops.map { it.devise } + listOfNotNull(selection.value?.auto, test.value?.portefeuille)
                    .flatMap { a -> a.operations.map { it.devise } } + "USD")
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
        val locaux = withContext(Dispatchers.Default) {
            selection.value?.titres.orEmpty().asSequence().filter { (t, v) ->
                Format.cle(t).startsWith(cle) || Format.cle(v.nom).contains(cle)
            }.map { (t, v) -> ResultatRecherche(t, v.nom, libelleMarcheCourt(v.marche), "Action") }.take(10).toList()
        }
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

    private fun vue(id: Long) = portefeuilles.value.firstOrNull { it.id == id }

    fun acheter(portefeuille: Long, ticker: String, nom: String, devise: String, quantite: Int, prix: Double) {
        viewModelScope.launch {
            val v = vue(portefeuille)?.takeIf { !it.robot } ?: return@launch
            val change = taux(devise) ?: return@launch messages.send(Message("Taux de change indisponible : réessayez"))
            val sim = simulerAchat(quantite, prix, change, v.portefeuille.frais, v.valorisation.liquidites)
            if (!sim.possible) return@launch messages.send(Message(sim.erreur!!))
            val id = dao.ajouter(OperationEntity.depuis(
                Operation(0, ticker, nom, devise, Sens.ACHAT, quantite, prix, change, sim.fraisEur, System.currentTimeMillis(), portefeuille)))
            messages.send(Message("Achat enregistré dans « ${v.portefeuille.nom} » : ${Format.quantite(quantite)} × $nom") { dao.supprimer(id) })
        }
    }

    fun vendre(portefeuille: Long, ticker: String, quantite: Int, prix: Double) {
        viewModelScope.launch {
            val v = vue(portefeuille)?.takeIf { !it.robot } ?: return@launch
            val pos = v.ligne(ticker)?.position
            val change = taux(pos?.devise ?: "EUR") ?: return@launch messages.send(Message("Taux de change indisponible : réessayez"))
            val sim = simulerVente(pos, quantite, prix, change, v.portefeuille.frais)
            if (!sim.possible || pos == null) return@launch messages.send(Message(sim.erreur ?: "Vente impossible"))
            val id = dao.ajouter(OperationEntity.depuis(
                Operation(0, ticker, pos.nom, pos.devise, Sens.VENTE, quantite, prix, change, sim.fraisEur, System.currentTimeMillis(), portefeuille)))
            messages.send(Message("Vente enregistrée dans « ${v.portefeuille.nom} » : ${Format.quantite(quantite)} × ${pos.nom}") { dao.supprimer(id) })
        }
    }

    fun supprimerOperation(op: Operation) {
        viewModelScope.launch {
            val v = vue(op.portefeuille)?.takeIf { !it.robot } ?: return@launch
            if (!peutSupprimer(v.portefeuille.capital, v.operations, op.id)) {
                return@launch messages.send(Message("Impossible : une vente ou un achat plus récent dépend de cette opération"))
            }
            dao.supprimer(op.id)
            messages.send(Message("Opération supprimée") { dao.ajouter(OperationEntity.depuis(op)) })
        }
    }

    // ---------- Portefeuilles ----------

    fun choisirPortefeuille(id: Long) {
        viewModelScope.launch { store.enregistrer(reglagesActuels().copy(actif = id)) }
    }

    fun creerPortefeuille(nom: String, capital: Double, fraisPct: Double, fraisMin: Double) {
        viewModelScope.launch {
            val id = dao.enregistrerPortefeuille(PortefeuilleEntity(0, nom.trim(), capital, fraisPct, fraisMin, System.currentTimeMillis()))
            store.enregistrer(reglagesActuels().copy(actif = id))
            messages.send(Message("Portefeuille « ${nom.trim()} » créé"))
        }
    }

    /** Nom, capital et frais ; le capital doit couvrir les achats déjà faits. */
    fun modifierPortefeuille(p: PortefeuilleEntity) {
        if (p.id < 0) return
        viewModelScope.launch {
            val ops = vue(p.id)?.operations.orEmpty()
            if (!bilan(p.capital, ops).coherent) {
                return@launch messages.send(Message("Capital trop faible pour les achats déjà faits"))
            }
            dao.modifierPortefeuille(p.copy(nom = p.nom.trim()))
            messages.send(Message("Portefeuille « ${p.nom.trim()} » enregistré"))
        }
    }

    fun reinitialiserPortefeuille(id: Long) {
        if (id < 0) return
        viewModelScope.launch {
            val v = vue(id) ?: return@launch
            val avant = dao.operationsDe(id)
            dao.vider(id)
            messages.send(Message("« ${v.portefeuille.nom} » réinitialisé") { dao.ajouterTout(avant) })
        }
    }

    fun supprimerPortefeuille(id: Long) {
        viewModelScope.launch {
            val tous = portefeuilles.value.filter { !it.robot }
            val v = tous.firstOrNull { it.id == id } ?: return@launch
            if (tous.size <= 1) return@launch messages.send(Message("Impossible de supprimer le dernier portefeuille"))
            val ops = dao.operationsDe(id)
            dao.supprimerPortefeuille(id)
            val r = reglagesActuels()
            if (r.actif == id) store.enregistrer(r.copy(actif = tous.first { it.id != id }.id))
            messages.send(Message("Portefeuille « ${v.portefeuille.nom} » supprimé") {
                dao.restaurer(v.portefeuille, ops)
                store.enregistrer(reglagesActuels().copy(actif = id))
            })
        }
    }

    // ---------- Réglages, sauvegarde ----------

    fun modifierReglages(r: Reglages) {
        viewModelScope.launch { store.enregistrer(r) }
    }

    fun exporter(uri: Uri) {
        viewModelScope.launch {
            val ps = portefeuilles.value.filter { !it.robot }
            val s = Sauvegarde(
                exporteLe = Instant.now().toString(),
                portefeuilles = ps.map { PortefeuilleSauve.depuis(it.portefeuille, it.operations) },
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(Sauvegarde.ecrire(s).toByteArray())
                    }
                }.isSuccess
            }
            val n = ps.sumOf { it.operations.size }
            messages.send(Message(if (ok) "Sauvegarde enregistrée (${ps.size} portefeuille${if (ps.size > 1) "s" else ""}, $n opérations)" else "Échec de l'enregistrement"))
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
                dao.remplacerTout(s.portefeuilles.map { p ->
                    p.versEntite() to p.operations.map { OperationEntity.depuis(it.versOperation()) }
                })
                dao.portefeuillesListe().firstOrNull()?.let { store.enregistrer(reglagesActuels().copy(actif = it.id)) }
                messages.send(Message("Sauvegarde importée : ${s.portefeuilles.size} portefeuille(s), ${s.portefeuilles.sumOf { it.operations.size }} opérations"))
            }.onFailure { messages.send(Message(it.message ?: "Fichier illisible")) }
        }
    }

    suspend fun reglagesActuels(): Reglages = reglages.value ?: store.flux.first()

    // ---------- Performance ----------

    val performances = MutableStateFlow<Map<Long, EtatPerformance>>(emptyMap())

    /**
     * Valeur quotidienne d'un portefeuille. Automatique : calculée chaque soir par l'analyse.
     * Les autres : rejouée sur le téléphone avec l'historique des cours Yahoo et des taux de change.
     */
    fun chargerPerformance(v: VuePortefeuille) {
        val a = if (v.test) test.value?.portefeuille else selection.value?.auto
        val cle = if (v.robot) "robot:" + test.value?.calculeLe + a?.valeurs?.size else v.operations.joinToString(",") { "${it.id}" }
        val actuel = performances.value[v.id]
        if (actuel != null && actuel.cle == cle && (actuel.chargement || actuel.erreur == null)) return
        if (v.robot) {
            performances.update { it + (v.id to EtatPerformance(a?.points.orEmpty(), cle = cle)) }
            return
        }
        if (v.operations.isEmpty()) {
            performances.update { it + (v.id to EtatPerformance(cle = cle)) }
            return
        }
        performances.update { it + (v.id to EtatPerformance(actuel?.valeurs.orEmpty(), chargement = true, cle = cle)) }
        viewModelScope.launch {
            try {
                val periode = periodePour(jourDe(v.operations.minOf { it.date }))
                val tickers = v.operations.map { it.ticker }.toSet()
                val historiques = enParallele(tickers) { t ->
                    runCatching { t to Reseau.cotation(t, periode).historique }.getOrNull()
                }.toMap()
                if (historiques.isEmpty()) throw java.io.IOException("Pas de cours")
                val devises = v.operations.map { it.devise }.filter { Devises.base(it).first != "EUR" }.toSet()
                val change = enParallele(devises) { d ->
                    val (base, div) = Devises.base(d)
                    runCatching { d to Reseau.cotation("${base}EUR=X", periode).historique.map { Point(it.date, it.valeur / div) } }.getOrNull()
                }.toMap()
                val jours = historiques.values.flatten().map { it.date } + LocalDate.now(Format.PARIS)
                val valeurs = withContext(Dispatchers.Default) {
                    valeursQuotidiennes(v.portefeuille.capital, v.operations, jours, historiques, change)
                }
                performances.update { it + (v.id to EtatPerformance(valeurs, cle = cle)) }
            } catch (e: Exception) {
                performances.update {
                    it + (v.id to EtatPerformance(actuel?.valeurs.orEmpty(), erreur = "Courbe indisponible : vérifiez la connexion.", cle = cle))
                }
            }
        }
    }
}

fun libelleMarcheCourt(m: String) = when (m) {
    "FR" -> "France"
    "EU" -> "Europe"
    "US" -> "États-Unis"
    else -> "Ma liste"
}
