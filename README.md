# MyBourse

Appli Android personnelle d'aide à la sélection d'actions, avec un portefeuille virtuel.
**Outil personnel d'aide à l'analyse, pas un conseil en investissement.**

100 % gratuit : analyse dans GitHub Actions, données Yahoo Finance (via yfinance), appli sans serveur ni compte.

## Comment ça marche

1. Chaque soir de semaine à 23 h 30 (heure de Paris en hiver), GitHub lance l'**analyse** (`pipeline/`) :
   cours des 40 actions du CAC 40, des 503 du S&P 500 et de « Ma liste », calcul d'un score sur 100,
   choix du **top 5 France** et du **top 5 États-Unis**, 2 ou 3 raisons en français pour chacune.
2. Le résultat est publié dans la branche `data` du dépôt (`selection.json`, `journal.json`).
3. L'appli lit ce fichier à l'ouverture (et quand on tire l'écran vers le bas).

## Les écrans

| Onglet | Ce qu'on y fait |
|---|---|
| **Sélection** | Le top 5 France et le top 5 États-Unis du jour, puis « Ma liste ». Score, cours, variation, mini-graphique 3 mois, raisons. Toucher une carte ouvre la fiche. |
| **Portefeuille** | Valeur totale, plus ou moins-value depuis le départ, liquidités, positions (quantité, prix de revient, valeur, +/− en € et %). Bouton « Acheter un titre » : **n'importe quelle action ou ETF** coté (recherche par nom ou code). Icône horloge : historique des transactions (date et heure, suppression possible). |
| **Réglages** | Capital de départ, frais de courtage simulés (% et minimum), marchés affichés, thème clair / sombre / automatique, export / import de la sauvegarde, réinitialisation. |

**Fiche d'un titre** : cours (et équivalent en euros pour les titres en dollars), graphique 1 mois / 6 mois / 1 an / 2 ans,
ma position, score global et détail des signaux, indicateurs, boutons « Acheter (virtuel) » et « Vendre ».

## Règles du portefeuille virtuel

- Achat et vente **au dernier cours connu** (Yahoo en direct, sinon clôture de l'analyse du soir).
- Achat en **montant (€)** ou en **nombre de titres** ; pas de fraction de titre.
- **Frais** : 0,1 % du montant, minimum 1 € par ordre (modifiables dans Réglages).
- Titres en dollars (ou autre devise) : convertis en euros au taux du moment, enregistré avec l'opération.
- Prix de revient (PRU) frais compris ; à la vente, méthode du coût moyen.
- Tout achat, vente, suppression ou réinitialisation s'**annule** pendant 5 secondes (bouton « Annuler »).
- Le portefeuille n'existe que sur le téléphone : **Réglages > Exporter** de temps en temps.

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
- `pipeline/config.yaml` : nombre de titres par marché, pondérations.

## Feuille de route

| Étape | Contenu | État |
|---|---|---|
| 1 | Analyse technique, top 10, fiche, portefeuille virtuel (tout titre), export/import | ✅ v0.1 |
| 2 | Portefeuille automatique (rééquilibré chaque lundi sur le top 10) et courbes comparées au CAC 40 et au S&P 500 | à venir |
| 3 | Fondamentaux (PER, croissance, marges, surprises de résultats) et récap du vendredi | à venir |
| 4 | Sentiment des actualités (FinBERT) et macro (FRED, GDELT, secteurs) | à venir |
| 5 | Backtest sur 2 ans | à venir |

## Dossiers

- `pipeline/` : analyse Python (tests : `cd pipeline && python -m pytest`)
- `android/` : appli Kotlin + Jetpack Compose + Room
- `.github/workflows/` : analyse quotidienne, fabrication de l'APK
- `secrets/` : clé de signature (jamais publiée, **à sauvegarder**)
