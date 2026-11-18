"""Backtest de MyBourse : rejoue la méthode et le portefeuille automatique sur les années passées.

Usage : python backtest.py [dossier_de_sortie]
Écrit backtest.json (lu par l'appli). Lancé une fois par mois par GitHub, ou à la main.
Réglage : rubrique « backtest » de config.yaml (nombre d'années rejouées).
"""
from __future__ import annotations

import json
import logging
import sys
import time
from pathlib import Path

import pandas as pd

from mybourse import backtest, donnees
from run import DEVISES_CHANGE, ICI, grands_indices, lire_config, titres_analyses

log = logging.getLogger("mybourse")


def main(sortie: Path) -> None:
    depart = time.time()
    cfg = lire_config()
    annees = int(cfg.get("backtest", {}).get("annees", 2))
    titres = titres_analyses(cfg, sortie, grands_indices(cfg))
    indices = cfg["indices"]
    fx = [f"EUR{d}=X" for d in DEVISES_CHANGE]
    tl = cfg["telechargement"]
    log.info("Téléchargement de 5 ans de cours pour %d titres", len(titres))
    cours = donnees.telecharger(list(titres) + list(indices) + fx, periode="5y", ajuste=False,
                                taille_lot=tl["taille_lot"], pause=tl["pause_secondes"])

    fin = max(cours[s].index[-1] for s in indices if s in cours)
    debut = fin - pd.DateOffset(years=annees)
    amorce = debut - pd.Timedelta(days=500)  # assez d'historique pour la moyenne 200 jours et la volatilité
    cours = {t: df[df.index >= amorce] for t, df in cours.items()}

    cours_ajustes = {t: df[["Adj Close", "Volume"]].rename(columns={"Adj Close": "Close"})
                     for t, df in cours.items() if t in titres and "Adj Close" in df}
    cotes = {t: cours[t]["Close"] for t in cours_ajustes}
    change = {}
    for d in DEVISES_CHANGE:
        if f"EUR{d}=X" in cours:
            change[d] = 1 / cours[f"EUR{d}=X"]["Close"]
    if "GBP" in change:
        change["GBp"] = change["GBP"] / 100

    cal = backtest.calendrier(cours, list(indices), debut, fin)
    log.info("Backtest du %s au %s (%d jours de bourse)", cal[0].date(), cal[-1].date(), len(cal))
    a = cfg["auto"]
    etat = backtest.simuler(titres, cours_ajustes, cotes, change, cal, cfg["ponderations"],
                            cfg["top_par_marche"], cfg["filtres"].get("volatilite_max_top"),
                            a["capital"], a["frais_pct"], a["frais_min"])
    series_indices = {s: cours[s]["Close"] for s in indices if s in cours}
    doc = backtest.document(etat, series_indices, indices, annees)
    sortie.mkdir(parents=True, exist_ok=True)
    (sortie / "backtest.json").write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":")),
                                          encoding="utf-8")
    b = doc["bilan"]
    log.info("Résultat : %s € (%+.1f %%, %+.1f %% par an), pire baisse %.1f %%, %d opérations, %.0f € de frais",
             b["valeur_finale"], b["perf_totale"], b["perf_annuelle"], b["pire_baisse"], b["operations"], b["frais"])
    log.info("Indices sur la même période : %s", b["indices"])
    log.info("Durée : %.0f s", time.time() - depart)


if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else ICI.parent / "publication"))
