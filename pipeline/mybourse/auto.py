"""Portefeuille automatique : investi à parts égales dans les tops, mis à jour chaque début de semaine.

Règles (mêmes calculs que l'appli) :
- départ : 10 000 € répartis à parts égales sur les titres des tops ;
- chaque semaine (première analyse de la semaine, normalement le lundi soir) : vente des titres sortis
  des tops, conservation de ceux qui y restent, achat des nouveaux entrants à parts égales avec
  les liquidités disponibles ;
- au cours de clôture, titres entiers, frais simulés (pourcentage avec minimum), conversion en euros ;
- valeur enregistrée chaque soir pour la courbe de performance.
"""
from __future__ import annotations

import math
from datetime import date


def centimes(v: float) -> float:
    """Arrondi au centime « au plus proche » (comme Math.round côté appli)."""
    return math.floor(v * 100 + 0.5) / 100


def frais_pour(montant: float, pct: float, minimum: float) -> float:
    return 0.0 if montant <= 0 else centimes(max(montant * pct / 100, minimum))


def quantite_pour(budget: float, unitaire_eur: float, pct: float, minimum: float) -> int:
    """Plus grand nombre entier de titres achetables avec ce budget, frais compris."""
    if budget <= 0 or unitaire_eur <= 0:
        return 0
    q = int(budget // unitaire_eur)
    while q > 0 and centimes(q * unitaire_eur) + frais_pour(centimes(q * unitaire_eur), pct, minimum) > budget + 0.005:
        q -= 1
    return q


def nouveau(capital: float, pct: float, minimum: float, debut: str) -> dict:
    return {"capital": capital, "frais_pct": pct, "frais_min": minimum, "debut": debut,
            "liquidites": capital, "positions": {}, "operations": [], "valeurs": [],
            "dernier_reequilibrage": None}


def _semaine(d: str) -> tuple[int, int]:
    return date.fromisoformat(d).isocalendar()[:2]


def _ordre(etat: dict, sens: str, ticker: str, info: dict, quantite: int, horodatage: int) -> None:
    prix, change = info["prix"], info["change"]
    brut = centimes(quantite * prix * change)
    frais = frais_pour(brut, etat["frais_pct"], etat["frais_min"])
    pos = etat["positions"].get(ticker, {"nom": info["nom"], "devise": info["devise"], "quantite": 0})
    if sens == "ACHAT":
        etat["liquidites"] = centimes(etat["liquidites"] - brut - frais)
        pos["quantite"] += quantite
    else:
        etat["liquidites"] = centimes(etat["liquidites"] + brut - frais)
        pos["quantite"] -= quantite
    pos.update(prix=prix, change=change)
    if pos["quantite"] > 0:
        etat["positions"][ticker] = pos
    else:
        etat["positions"].pop(ticker, None)
    etat["operations"].append({
        "date": horodatage, "ticker": ticker, "nom": info["nom"], "devise": info["devise"], "sens": sens,
        "quantite": quantite, "prix": prix, "change": change, "frais": frais,
    })


def reequilibrer(etat: dict, cibles: list[str], infos: dict[str, dict], horodatage: int) -> None:
    """Vend les titres sortis des tops, achète les entrants à parts égales avec les liquidités."""
    for t in list(etat["positions"]):
        if t not in cibles and t in infos:
            _ordre(etat, "VENTE", t, infos[t], etat["positions"][t]["quantite"], horodatage)
    entrants = [t for t in cibles if t not in etat["positions"] and t in infos]
    if not entrants:
        return
    part = etat["liquidites"] / len(entrants)
    for t in entrants:
        i = infos[t]
        q = quantite_pour(min(part, etat["liquidites"]), i["prix"] * i["change"], etat["frais_pct"], etat["frais_min"])
        if q > 0:
            _ordre(etat, "ACHAT", t, i, q, horodatage)


def valeur(etat: dict, infos: dict[str, dict]) -> float:
    """Liquidités + titres au dernier cours connu (celui de la veille si le titre n'a pas coté)."""
    total = etat["liquidites"]
    for t, p in etat["positions"].items():
        if t in infos:
            p.update(prix=infos[t]["prix"], change=infos[t]["change"])
        total += centimes(p["quantite"] * p["prix"] * p["change"])
    return centimes(total)


def mettre_a_jour(etat: dict | None, jour: str, cibles: list[str], infos: dict[str, dict], horodatage: int,
                  capital: float = 10_000.0, pct: float = 0.1, minimum: float = 1.0) -> dict:
    """Une analyse du soir : création au premier passage, mise à jour une fois par semaine, valeur du jour."""
    if etat is None:
        etat = nouveau(capital, pct, minimum, jour)
    dernier = etat["dernier_reequilibrage"]
    if dernier is None or _semaine(jour) != _semaine(dernier):
        reequilibrer(etat, cibles, infos, horodatage)
        etat["dernier_reequilibrage"] = jour
    v = valeur(etat, infos)
    etat["valeurs"] = [x for x in etat["valeurs"] if x[0] != jour] + [[jour, v]]
    return etat


def pour_appli(etat: dict) -> dict:
    """Ce que l'appli lit : capital, frais, opérations et valeurs quotidiennes."""
    return {k: etat[k] for k in ("capital", "frais_pct", "frais_min", "debut", "dernier_reequilibrage",
                                 "operations", "valeurs")}
