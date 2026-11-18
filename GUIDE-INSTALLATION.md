# Guide d'installation de MyBourse

🙋 = à faire par Fred · 🤖 = fait par Claude

## Ce qui est en place

| Élément | Où |
|---|---|
| Dépôt GitHub (public) | https://github.com/Spawn2929/MyBourse |
| Fichier lu par l'appli | branche `data` : https://raw.githubusercontent.com/Spawn2929/MyBourse/data/selection.json |
| Analyse quotidienne | onglet **Actions** > « Analyse quotidienne » (lun.–ven. 22 h 30 UTC) |
| Backtest sur 2 ans | onglet **Actions** > « Backtest » (le 1er de chaque mois, 6 h UTC) |
| APK | onglet **Releases** du dépôt, et copie dans `apk/` sur le PC |
| Secrets GitHub | `SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` |
| Coût | 0 € |

## 🙋 Installer l'appli sur le téléphone

1. Sur le téléphone, ouvrir le lien de l'APK (Releases) et toucher **MyBourse-X.Y.Z.apk** pour le télécharger.
2. Toucher la notification « Téléchargement terminé » (ou ouvrir **Fichiers > Téléchargements**).
3. Si Android le demande : **Paramètres** > autoriser **Installer des applis inconnues** pour le navigateur, puis revenir.
4. Toucher **Installer**. Si Play Protect affiche un avertissement : **Plus de détails** > **Installer quand même**.
5. Ouvrir **MyBourse** : la sélection du jour s'affiche.

## 🙋 Mettre à jour plus tard

Même chose avec le nouvel APK : **installer par-dessus**, les portefeuilles sont conservés.
Ne jamais désinstaller l'appli : les portefeuilles seraient effacés (sauf sauvegarde exportée).
En passant de la v0.1 à la v0.2, l'ancien portefeuille devient « Principal » avec son capital et ses frais.
Par prudence, faire **Réglages > Exporter** avant la mise à jour.

## 🙋 Sauvegarder les portefeuilles

**Réglages > Exporter** : choisir Drive ou le dossier Téléchargements (tous les portefeuilles sont dans le fichier).
Pour restaurer : **Réglages > Importer** (une sauvegarde de la v0.1 est aussi acceptée).

## 🤖 Lancer l'analyse à la main

github.com > dépôt MyBourse > **Actions** > **Analyse quotidienne** > **Run workflow**
(cocher « Refaire la liste des titres analysés » pour la reconstruire sans attendre la semaine suivante).

## 🤖 Relancer le backtest à la main

github.com > dépôt MyBourse > **Actions** > **Backtest** > **Run workflow** (environ 15 minutes).

## 🤖 Fabriquer un APK

```bash
cd /c/Developpement_Claude/MyBourse && git tag v0.2.1 && git push origin v0.2.1
```

## 🤖 Essayer sur le PC

```bash
cd /c/Developpement_Claude/MyBourse/pipeline && ../.venv/Scripts/python run.py ../publication
```

Puis servir `publication/` (`python -m http.server 8000`) et fabriquer l'appli d'essai avec
`-PdataUrl=http://10.0.2.2:8000/selection.json`. Gradle doit être lancé avec
`TMP=C:\Developpement_Claude\tmp` (le dossier temporaire Windows contient un accent).

## 🙋 Sauvegarder le dossier `secrets/`

| Fichier | Si on le perd |
|---|---|
| `secrets/mybourse-release.jks` + `keystore.properties` | Impossible de mettre à jour l'appli sans la désinstaller (et perdre le portefeuille non exporté) |

Copier le dossier `C:\Developpement_Claude\MyBourse\secrets` sur une clé USB ou un Drive personnel.
