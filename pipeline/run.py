"""Analyse quotidienne de MyBourse : télécharge les cours, calcule les scores, écrit les fichiers JSON.

Usage : python run.py [dossier_de_sortie]
Fichiers écrits : selection.json (lu par l'appli), journal.json (sélections passées)
et univers.json (liste des titres analysés, reconstruite chaque semaine).
Variable MYBOURSE_UNIVERS=reconstruire : force la reconstruction de la liste.
"""
from __future__ import annotations

import json
import logging
import os
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import pandas as pd
import yaml

from mybourse import analyse, donnees, univers

ICI = Path(__file__).resolve().parent
logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
log = logging.getLogger("mybourse")


def lire_liste(chemin: Path) -> list[str]:
    if not chemin.exists():
        return []
    lignes = (l.split("#")[0].strip().upper() for l in chemin.read_text(encoding="utf-8").splitlines())
    return list(dict.fromkeys(l for l in lignes if l))


def grands_indices(cfg: dict) -> dict[str, dict]:
    """Membres du CAC 40 et du S&P 500 : ticker -> {nom, secteur, zone}."""
    res = {}
    for fichier in cfg["grands_indices"]:
        zone = "FR" if "cac" in fichier else "US"
        for r in pd.read_csv(ICI / fichier, keep_default_na=False).itertuples():
            res[r.ticker] = {"nom": r.nom, "secteur": r.secteur, "zone": zone}
    return res


def charger_univers(cfg: dict, sortie: Path, grands: dict) -> list[dict]:
    """Liste de la semaine ; reconstruite si trop ancienne. En cas d'échec, l'ancienne est gardée."""
    chemin = sortie / "univers.json"
    ancien = json.loads(chemin.read_text(encoding="utf-8")) if chemin.exists() else None
    if ancien and os.environ.get("MYBOURSE_UNIVERS") != "reconstruire":
        age = (date.today() - date.fromisoformat(ancien["construit_le"][:10])).days
        if age < cfg["univers_jours"]:
            log.info("Univers du %s gardé (%d titres)", ancien["construit_le"][:10], len(ancien["titres"]))
            return ancien["titres"]
    try:
        fx = univers.taux_change()
        nouveau = univers.construire(cfg["zones"], cfg["filtres"], set(grands), fx)
    except Exception as e:  # noqa: BLE001 - Yahoo indisponible : on garde la liste précédente
        log.warning("Reconstruction de l'univers impossible : %s", e)
        nouveau = []
    if ancien and len(nouveau) < 0.7 * len(ancien["titres"]):
        log.warning("Univers incomplet (%d titres contre %d) : liste précédente gardée",
                    len(nouveau), len(ancien["titres"]))
        return ancien["titres"]
    if not nouveau:
        raise SystemExit("Impossible de construire la liste des titres : publication annulée.")
    sortie.mkdir(parents=True, exist_ok=True)
    doc = {"construit_le": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "titres": nouveau}
    chemin.write_text(json.dumps(doc, ensure_ascii=False, indent=0), encoding="utf-8")
    log.info("Univers reconstruit : %d titres", len(nouveau))
    return nouveau


def main(sortie: Path) -> None:
    cfg = yaml.safe_load((ICI / "config.yaml").read_text(encoding="utf-8"))
    grands = grands_indices(cfg)
    titres: dict[str, dict] = {}
    for u in charger_univers(cfg, sortie, grands):
        titres[u["ticker"]] = {"nom": u["nom"], "marche": u["zone"], "secteur": u["secteur"],
                               "devise": u["devise"], "capi_eur": u["capi_eur"], "grande": u["grande"]}
    # Les membres du CAC 40 et du S&P 500 absents du filtre restent analysés (achetables, hors tops)
    for t, g in grands.items():
        if t not in titres:
            titres[t] = {"nom": g["nom"], "marche": g["zone"], "secteur": g["secteur"],
                         "devise": donnees.devise(t), "grande": True}
        else:
            titres[t]["grande"] = True
    liste = lire_liste(ICI / cfg["ma_liste"])
    for t in liste:
        if t not in titres:
            titres[t] = {"nom": donnees.nom_titre(t), "marche": "LISTE", "secteur": "",
                         "devise": donnees.devise(t)}
    indices = cfg["indices"]
    tl = cfg["telechargement"]
    log.info("Téléchargement de %d titres", len(titres))
    cours = donnees.telecharger(list(titres) + list(indices) + ["EURUSD=X"],
                                taille_lot=tl["taille_lot"], pause=tl["pause_secondes"])
    eurusd = round(float(cours["EURUSD=X"]["Close"].iloc[-1]), 5) if "EURUSD=X" in cours else None

    fiches, top = analyse.analyser(titres, cours, cfg["ponderations"], cfg["top_par_marche"],
                                   cfg["filtres"].get("volatilite_max_top"))
    if not any(top.values()):
        raise SystemExit("Aucun titre classé : publication annulée pour ne pas écraser la veille.")
    doc = analyse.construire_json(fiches, top, cours, indices, cfg["ponderations"], eurusd, liste)

    sortie.mkdir(parents=True, exist_ok=True)
    (sortie / "selection.json").write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":")),
                                           encoding="utf-8")
    chemin_journal = sortie / "journal.json"
    journal = json.loads(chemin_journal.read_text(encoding="utf-8")) if chemin_journal.exists() else []
    journal = analyse.ajouter_journal(journal, doc["date_marche"], top, fiches)
    chemin_journal.write_text(json.dumps(journal, ensure_ascii=False, indent=1), encoding="utf-8")
    log.info("%d titres analysés, top : %s", len(fiches), top)


if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else ICI.parent / "publication"))
