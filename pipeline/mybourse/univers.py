"""Construction de la liste des titres analysés (« l'univers »), à partir du filtre d'actions de Yahoo.

Une fois par semaine, l'analyse interroge Yahoo pays par pays et secteur par secteur, puis garde les
sociétés assez grandes et assez échangées (seuils en euros dans config.yaml). Les grandes valeurs
(CAC 40, S&P 500, plus de 10 Md€) restent analysées et achetables, mais n'entrent pas dans les tops.
"""
from __future__ import annotations

import logging
import re
import time

import yfinance as yf
from yfinance import EquityQuery as Q

log = logging.getLogger(__name__)

SECTEURS_FR = {
    "Basic Materials": "Matériaux",
    "Communication Services": "Communication",
    "Consumer Cyclical": "Consommation cyclique",
    "Consumer Defensive": "Consommation de base",
    "Energy": "Énergie",
    "Financial Services": "Finance",
    "Healthcare": "Santé",
    "Industrials": "Industrie",
    "Real Estate": "Immobilier",
    "Technology": "Technologie",
    "Utilities": "Services publics",
}

# Devise des capitalisations de chaque pays (pour convertir le seuil en devise locale)
DEVISE_PAYS = {"gb": "GBP", "ch": "CHF", "se": "SEK", "dk": "DKK", "no": "NOK", "us": "USD"}
DEVISES = ("USD", "GBP", "CHF", "SEK", "DKK", "NOK")

# Actions de préférence, bons de souscription, unités… (codes américains) : pas des actions ordinaires
_PAS_ACTION = re.compile(r"-(P[A-Z]?|W|WS|WT|U|UN|R|RT)$")


def taux_change() -> dict[str, float]:
    """Unités de chaque devise pour 1 euro (ex. USD : 1,16)."""
    symboles = [f"EUR{d}=X" for d in DEVISES]
    df = yf.download(symboles, period="5d", interval="1d", progress=False, group_by="ticker")
    fx = {"EUR": 1.0}
    for d, s in zip(DEVISES, symboles):
        serie = df[s]["Close"].dropna()
        if not serie.empty:
            fx[d] = float(serie.iloc[-1])
    manquantes = set(DEVISES) - set(fx)
    if manquantes:
        raise RuntimeError(f"Taux de change introuvables : {manquantes}")
    return fx


def en_euros(montant: float, devise: str, fx: dict[str, float]) -> float | None:
    """Montant converti en euros ; les pence (GBp) sont ramenés en livres."""
    if devise == "GBp":
        montant, devise = montant / 100, "GBP"
    taux = fx.get(devise)
    return montant / taux if taux else None


def filtrer(q: dict, zone: str, secteur: str, fx: dict[str, float], filtres: dict) -> dict | None:
    """Garde une cotation si c'est une action ordinaire assez grande, assez échangée et pas trop bon marché."""
    symbole, devise = q.get("symbol"), q.get("currency")
    capi, prix, volume = q.get("marketCap"), q.get("regularMarketPrice"), q.get("averageDailyVolume3Month")
    if q.get("quoteType") != "EQUITY" or not symbole or not devise or not capi or not prix or not volume:
        return None
    if _PAS_ACTION.search(symbole):
        return None
    devise_capi = "GBP" if devise == "GBp" else devise
    capi_eur = en_euros(capi, devise_capi, fx)
    prix_eur = en_euros(prix, devise, fx)
    if capi_eur is None or prix_eur is None:
        return None
    prix_min_ok = prix >= filtres["cours_min"] if devise in ("USD", "EUR") else prix_eur >= filtres["cours_min"]
    if capi_eur < filtres["capitalisation_min_eur"] or not prix_min_ok:
        return None
    if volume * prix_eur < filtres["echanges_min_eur_par_jour"]:
        return None
    nom = (q.get("longName") or q.get("shortName") or q.get("displayName") or symbole).strip()
    return {
        "ticker": symbole, "nom": nom, "zone": zone, "secteur": SECTEURS_FR.get(secteur, secteur),
        "devise": devise, "capi_eur": round(capi_eur), "echanges_eur": round(volume * prix_eur),
    }


def _interroger(region: str, places: list[str], secteur: str, seuil_local: float, pause: float) -> list[dict]:
    condition_place = Q("eq", ["exchange", places[0]]) if len(places) == 1 else Q("is-in", ["exchange", *places])
    requete = Q("and", [
        Q("eq", ["region", region]), condition_place, Q("eq", ["sector", secteur]),
        Q("gte", ["intradaymarketcap", seuil_local]), Q("gte", ["avgdailyvol3m", 1000]),
    ])
    cotations, debut = [], 0
    while True:
        r = yf.screen(requete, size=250, offset=debut, sortField="intradaymarketcap", sortAsc=False)
        lot = r.get("quotes") or []
        cotations += lot
        debut += 250
        if not lot or debut >= (r.get("total") or 0):
            return cotations
        time.sleep(pause)


def construire(zones: dict, filtres: dict, grandes: set[str], fx: dict[str, float], pause: float = 0.3) -> list[dict]:
    """zones : FR/EU/US -> {pays: [places de cotation]}. Renvoie la liste des titres retenus."""
    titres: dict[str, dict] = {}
    deja_vu: set[tuple] = set()
    for zone, pays in zones.items():
        for region, places in pays.items():
            seuil = filtres["capitalisation_min_eur"] * fx[DEVISE_PAYS.get(region, "EUR")] * 0.9
            n = 0
            for secteur in SECTEURS_FR:
                for q in _interroger(region, places, secteur, seuil, pause):
                    t = filtrer(q, zone, secteur, fx, filtres)
                    if t is None or t["ticker"] in titres:
                        continue
                    # Deux catégories d'actions d'une même société (ex. GOOG et GOOGL) : une seule suffit
                    cle = (zone, q.get("marketCap"))
                    if cle in deja_vu:
                        continue
                    deja_vu.add(cle)
                    t["grande"] = t["ticker"] in grandes or t["capi_eur"] >= filtres["grande_valeur_eur"]
                    titres[t["ticker"]] = t
                    n += 1
                time.sleep(pause)
            log.info("Univers %s/%s : %d titres", zone, region, n)
    return sorted(titres.values(), key=lambda t: (t["zone"], t["ticker"]))
