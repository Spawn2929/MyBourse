"""Analyse quotidienne de MyBourse : télécharge les cours, calcule les scores, écrit les fichiers JSON.

Usage : python run.py [dossier_de_sortie]
Fichiers écrits : selection.json (lu par l'appli) et journal.json (sélections passées).
"""
from __future__ import annotations

import json
import logging
import sys
from pathlib import Path

import pandas as pd
import yaml

from mybourse import analyse, donnees

ICI = Path(__file__).resolve().parent
logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
log = logging.getLogger("mybourse")


def lire_liste(chemin: Path) -> list[str]:
    if not chemin.exists():
        return []
    lignes = (l.split("#")[0].strip().upper() for l in chemin.read_text(encoding="utf-8").splitlines())
    return list(dict.fromkeys(l for l in lignes if l))


def main(sortie: Path) -> None:
    cfg = yaml.safe_load((ICI / "config.yaml").read_text(encoding="utf-8"))
    titres: dict[str, dict] = {}
    for marche, fichier in cfg["univers"].items():
        df = pd.read_csv(ICI / fichier, keep_default_na=False)
        for r in df.itertuples():
            titres[r.ticker] = {"nom": r.nom, "marche": marche, "secteur": r.secteur,
                                "devise": donnees.devise(r.ticker)}
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

    fiches, top = analyse.analyser(titres, cours, cfg["ponderations"], cfg["top_par_marche"])
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
