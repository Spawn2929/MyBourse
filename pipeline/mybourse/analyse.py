"""Calcul des scores, du classement et du fichier JSON publié pour l'appli."""
from __future__ import annotations

from datetime import datetime, timezone

import pandas as pd

from . import technique

AVERTISSEMENT = "Outil personnel d'aide à l'analyse, pas un conseil en investissement."
FAMILLES = ("technique", "fondamental", "sentiment", "macro")


def score_global(scores: dict, ponderations: dict) -> float | None:
    """Moyenne pondérée des familles disponibles (les poids sont ramenés à 100 % sur celles-ci)."""
    dispo = {f: s for f, s in scores.items() if s is not None and ponderations.get(f, 0) > 0}
    total = sum(ponderations[f] for f in dispo)
    if not total:
        return None
    return round(sum(ponderations[f] * s for f, s in dispo.items()) / total, 1)


def rangs(valeurs: dict[str, float | None]) -> dict[str, float]:
    """Rang de 0 (pire) à 1 (meilleur) ; une valeur manquante vaut 0,5."""
    s = pd.Series({k: v for k, v in valeurs.items() if v is not None}, dtype=float)
    r = s.rank(pct=True, method="average") if len(s) > 1 else s * 0 + 1
    return {k: float(r.get(k, 0.5)) for k in valeurs}


def historique(df: pd.DataFrame, jours: int) -> list[list]:
    close = df["Close"].dropna().tail(jours)
    return [[d.strftime("%Y-%m-%d"), round(float(v), 4)] for d, v in close.items()]


def _arrondi(v):
    return None if v is None else round(v, 2)


def analyser(titres: dict[str, dict], cours: dict[str, pd.DataFrame], ponderations: dict,
             top_par_marche: dict[str, int],
             volatilite_max: float | None = None) -> tuple[dict, dict[str, list[str]]]:
    """titres : ticker -> {nom, marche, secteur, devise, capi_eur?, grande?}.

    Renvoie (fiches par titre, top par marché). Les rangs de performance sont calculés au sein
    de chaque marché ; les titres de « ma liste » (marché LISTE) sont comparés à l'ensemble.
    """
    ind = {t: technique.indicateurs(cours[t]) for t in titres if t in cours}
    ind = {t: i for t, i in ind.items() if i is not None}
    return classer(titres, ind, ponderations, top_par_marche, volatilite_max)


def classer(titres: dict[str, dict], ind: dict[str, dict], ponderations: dict, top_par_marche: dict[str, int],
            volatilite_max: float | None = None) -> tuple[dict, dict[str, list[str]]]:
    """Scores et tops à partir des indicateurs déjà calculés (utilisé aussi par le backtest)."""
    groupes: dict[str, list[str]] = {}
    for t in ind:
        groupes.setdefault(titres[t]["marche"], []).append(t)
    tous6 = rangs({t: ind[t]["perf_6m"] for t in ind})
    tous3 = rangs({t: ind[t]["perf_3m"] for t in ind})
    rang6, rang3 = {}, {}
    for marche, membres in groupes.items():
        if marche == "LISTE":
            rang6.update({t: tous6[t] for t in membres})
            rang3.update({t: tous3[t] for t in membres})
        else:
            rang6.update(rangs({t: ind[t]["perf_6m"] for t in membres}))
            rang3.update(rangs({t: ind[t]["perf_3m"] for t in membres}))

    fiches = {}
    for t, i in ind.items():
        st, notes = technique.score_technique(i, rang6[t], rang3[t])
        raisons = sorted(technique.raisons_technique(i, notes, rang6[t]), key=lambda x: -x[0])
        scores = {f: None for f in FAMILLES} | {"technique": st}
        meta = titres[t]
        fiches[t] = {
            "nom": meta["nom"], "marche": meta["marche"], "secteur": meta.get("secteur", ""),
            "devise": meta["devise"], "date": i["date"], "cours": i["cours"],
            "var_1j": _arrondi(i["var_1j"]),
            "score": score_global(scores, ponderations),
            "scores": scores,
            "raisons": [p for _, p in raisons[:3]],
            "indicateurs": {
                "perf_1m": _arrondi(i["perf_1m"]), "perf_3m": _arrondi(i["perf_3m"]),
                "perf_6m": _arrondi(i["perf_6m"]), "perf_1a": _arrondi(i["perf_1a"]),
                "rsi": _arrondi(i["rsi"]), "mm50": round(i["mm50"], 4), "mm200": round(i["mm200"], 4),
                "macd_hist": _arrondi(i["macd_hist"]), "ratio_volume": _arrondi(i["ratio_volume"]),
                "volatilite": _arrondi(i.get("volatilite")),
            },
            "notes_technique": notes,
        }
        if meta.get("capi_eur"):
            fiches[t]["capi"] = round(meta["capi_eur"] / 1e9, 2)  # en milliards d'euros
        if meta.get("grande"):
            fiches[t]["grande"] = True

    top = {}
    for marche, n in top_par_marche.items():
        # Exclues des tops : les grandes valeurs (CAC 40, S&P 500, plus de 10 Md€)
        # et les titres trop agités (volatilité annuelle au-delà du seuil)
        membres = [t for t in groupes.get(marche, [])
                   if fiches[t]["score"] is not None and not titres[t].get("grande")
                   and not trop_volatil(fiches[t], volatilite_max)]
        membres.sort(key=lambda t: (-fiches[t]["score"], t))
        top[marche] = membres[:n]
    return fiches, top


def trop_volatil(fiche: dict, volatilite_max: float | None) -> bool:
    v = fiche["indicateurs"].get("volatilite")
    return volatilite_max is not None and v is not None and v > volatilite_max


def construire_json(fiches: dict, top: dict, cours: dict[str, pd.DataFrame], indices: dict[str, str],
                    ponderations: dict, eurusd: float | None, liste: list[str],
                    maintenant: datetime | None = None) -> dict:
    maintenant = maintenant or datetime.now(timezone.utc)
    avec_historique = {t for ts in top.values() for t in ts} | set(liste)
    for t, f in fiches.items():
        if t in avec_historique and t in cours:
            f["historique"] = historique(cours[t], 260)
    dates = [f["date"] for f in fiches.values()]
    return {
        "format": 1,
        "genere_le": maintenant.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "date_marche": max(dates) if dates else None,
        "avertissement": AVERTISSEMENT,
        "ponderations": ponderations,
        "change": {"EURUSD": eurusd},
        "top": top,
        "ma_liste": [t for t in liste if t in fiches],
        "indices": {
            sym: {"nom": nom, "historique": historique(cours[sym], 520)}
            for sym, nom in indices.items() if sym in cours
        },
        "titres": fiches,
    }


def ajouter_journal(precedent: list[dict], date: str, top: dict, fiches: dict) -> list[dict]:
    """Journal des sélections passées (une entrée par jour de marché ; une relance le même jour remplace)."""
    entree = {"date": date, "top": {m: [{"ticker": t, "score": fiches[t]["score"], "cours": fiches[t]["cours"]}
                                        for t in ts] for m, ts in top.items()}}
    return [e for e in precedent if e["date"] != date] + [entree]
