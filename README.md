# MyBourse

Appli Android personnelle d'aide à la sélection d'actions, avec un portefeuille virtuel.
**Outil personnel d'aide à l'analyse, pas un conseil en investissement.**

100 % gratuit : analyse dans GitHub Actions, données Yahoo Finance (via yfinance), appli sans serveur ni compte.

## Comment ça marche

1. Chaque soir de semaine à 23 h 30 (heure de Paris en hiver), GitHub lance l'**analyse** (`pipeline/`) :
   cours d'environ **5 400 actions** de France, d'Europe et des États-Unis, calcul d'un score sur 100,
   choix d'un **top 5 France**, d'un **top 5 Europe** et d'un **top 5 États-Unis** parmi les
   **valeurs moyennes et petites**, 2 ou 3 raisons en français pour chacune.
2. Le résultat est publié dans la branche `data` du dépôt (`selection.json`, `journal.json`, `univers.json`).
3. L'appli lit ce fichier à l'ouverture (et quand on tire l'écran vers le bas).

## Les écrans

| Onglet | Ce qu'on y fait |
|---|---|
| **Sélection** | Les tops France, Europe et États-Unis du jour, puis « Ma liste ». Score, cours, variation, capitalisation, mini-graphique 3 mois, **niveau de risque** (volatilité), raisons. Toucher une carte ouvre la fiche. |
| **Portefeuille** | **Plusieurs portefeuilles** : toucher le nom en haut (« Principal ▾ ») pour en changer ou en créer un ; menu ⋮ : nom, capital et frais, réinitialiser, supprimer. Valeur totale, plus ou moins-value, liquidités, positions. Bouton « Acheter un titre » : **n'importe quelle action ou ETF** coté. Icône horloge : historique des transactions du portefeuille affiché. |
| **Réglages** | Zones affichées, thème clair / sombre / automatique, export / import de tous les portefeuilles. |

**Fiche d'un titre** : cours (et équivalent en euros pour les titres en dollars), graphique 1 mois / 6 mois / 1 an / 2 ans,
ma position dans chaque portefeuille, score global et détail des signaux, indicateurs (dont la volatilité),
boutons « Acheter (virtuel) » et « Vendre » avec le choix du portefeuille.

## Règles du portefeuille virtuel

- Achat et vente **au dernier cours connu** (Yahoo en direct, sinon clôture de l'analyse du soir).
- Achat en **montant (€)** ou en **nombre de titres** ; pas de fraction de titre.
- Chaque portefeuille a son **capital de départ** et ses **frais** (par défaut 0,1 % du montant, minimum 1 € par ordre).
- Titres en dollars (ou autre devise) : convertis en euros au taux du moment, enregistré avec l'opération.
- Prix de revient (PRU) frais compris ; à la vente, méthode du coût moyen.
- Tout achat, vente, suppression ou réinitialisation s'**annule** pendant 5 secondes (bouton « Annuler »).
- Les portefeuilles n'existent que sur le téléphone : **Réglages > Exporter** de temps en temps.

## Les actions analysées

- **Zones** : France (Euronext Paris), Europe hors France (Xetra, Londres, Zurich, Amsterdam, Milan, Madrid,
  Stockholm, Copenhague, Oslo, Helsinki, Bruxelles, Vienne, Dublin, Lisbonne), États-Unis (NYSE, Nasdaq).
- **Filtres** : capitalisation d'au moins 200 M€, au moins 500 000 € échangés par jour, cours d'au moins 1 € (ou 1 $).
- **Tops** : seulement les valeurs moyennes et petites. Les **grandes valeurs** (CAC 40, S&P 500, plus de 10 Md€)
  sont analysées et achetables, mais hors des tops.
- La liste est refaite **chaque semaine** avec le filtre d'actions de Yahoo (gratuit) ; si Yahoo ne répond pas,
  la liste précédente est gardée. Pour la refaire tout de suite : Actions > Analyse quotidienne > Run workflow,
  case « Refaire la liste ».
- **À savoir** : les petites valeurs ne battent pas systématiquement les grands indices et chutent plus fort
  dans les crises. Le repère de **risque** sur chaque carte indique l'amplitude habituelle des variations.

## Le score (étape 1 : signaux techniques)

| Composante | Poids | Ce qui est bien noté |
|---|---|---|
| Tendance | 30 % | Cours au-dessus de ses moyennes 50 et 200 jours, moyenne 50 au-dessus de la 200 |
| Élan | 35 % | Hausse sur 6 mois et 3 mois, comparée aux autres actions du même marché |
| RSI | 15 % | Entre 55 et 70 (achats soutenus sans excès) |
| MACD | 10 % | Positif et en accélération |
| Volumes | 10 % | Échanges en hausse quand le cours monte |

Les familles fondamental, sentiment et macro/secteur arriveront aux étapes suivantes ; le score global
est la moyenne pondérée des familles disponibles (poids dans `pipeline/config.yaml`).

## Modifier l'analyse (sans rien installer)

Sur github.com, dans le dépôt, ouvrir le fichier puis l'icône crayon ✏️ et « Commit changes » :
- `pipeline/ma_liste.txt` : titres supplémentaires à analyser (codes Yahoo, un par ligne, ex. `CW8.PA`) ;
- `pipeline/config.yaml` : zones, filtres, nombre de titres par zone, pondérations.

## Feuille de route

| Étape | Contenu | État |
|---|---|---|
| 1 | Analyse technique, top 10, fiche, portefeuille virtuel (tout titre), export/import | ✅ v0.1 |
| 1 bis | Plusieurs portefeuilles, valeurs moyennes et petites de France, d'Europe et des États-Unis | ✅ v0.2 |
| 2 | Portefeuille automatique (rééquilibré chaque lundi sur les tops) et courbes comparées au CAC 40, au S&P 500, au Russell 2000 et au STOXX Europe 600 | à venir |
| 3 | Fondamentaux (PER, croissance, marges, surprises de résultats) et récap du vendredi | à venir |
| 4 | Sentiment des actualités (FinBERT) et macro (FRED, GDELT, secteurs) | à venir |
| 5 | Backtest sur 2 ans (avancé juste après l'étape 2) | à venir |

## Dossiers

- `pipeline/` : analyse Python (tests : `cd pipeline && python -m pytest`)
- `android/` : appli Kotlin + Jetpack Compose + Room
- `.github/workflows/` : analyse quotidienne, fabrication de l'APK
- `secrets/` : clé de signature (jamais publiée, **à sauvegarder**)
