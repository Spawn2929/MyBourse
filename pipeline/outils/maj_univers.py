"""Met à jour les listes d'actions du CAC 40 et du S&P 500 depuis Wikipédia (licence CC BY-SA).

À lancer à la main de temps en temps (les indices changent quelques fois par an) :
    python pipeline/outils/maj_univers.py
"""
from io import StringIO
from pathlib import Path

import pandas as pd
import requests

DOSSIER = Path(__file__).resolve().parent.parent / "univers"
UA = {"User-Agent": "MyBourse/1.0 (outil personnel)"}

SECTEURS_FR = {
    "Information Technology": "Technologie", "Technology": "Technologie",
    "Health Care": "Santé", "Healthcare": "Santé",
    "Financials": "Finance", "Financial Services": "Finance",
    "Consumer Discretionary": "Consommation cyclique", "Consumer Cyclical": "Consommation cyclique", "Consumer Services": "Consommation cyclique",
    "Consumer Staples": "Consommation de base", "Consumer Defensive": "Consommation de base",
    "Industrials": "Industrie", "Energy": "Énergie", "Utilities": "Services publics",
    "Materials": "Matériaux", "Basic Materials": "Matériaux",
    "Real Estate": "Immobilier", "Communication Services": "Communication",
}


def lire(url: str) -> list[pd.DataFrame]:
    html = requests.get(url, headers=UA, timeout=30).text
    return pd.read_html(StringIO(html))


def sp500() -> pd.DataFrame:
    t = lire("https://en.wikipedia.org/wiki/List_of_S%26P_500_companies")[0]
    df = pd.DataFrame({
        "ticker": t["Symbol"].str.replace(".", "-", regex=False),
        "nom": t["Security"],
        "secteur": t["GICS Sector"].map(SECTEURS_FR).fillna(t["GICS Sector"]),
    })
    return df.sort_values("ticker")


def cac40() -> pd.DataFrame:
    for t in lire("https://en.wikipedia.org/wiki/CAC_40"):
        cols = {c.lower(): c for c in map(str, t.columns)}
        if "ticker" in cols and "company" in cols:
            secteur_col = next((cols[c] for c in cols if "sector" in c), None)
            tick = t[cols["ticker"]].astype(str).str.strip()
            tick = tick.where(tick.str.contains(r"\."), tick + ".PA")
            df = pd.DataFrame({
                "ticker": tick,
                "nom": t[cols["company"]],
                "secteur": t[secteur_col].map(SECTEURS_FR).fillna(t[secteur_col]) if secteur_col else "",
            })
            return df.sort_values("ticker")
    raise RuntimeError("Tableau du CAC 40 introuvable sur Wikipédia")


if __name__ == "__main__":
    DOSSIER.mkdir(exist_ok=True)
    for nom, f in (("cac40", cac40), ("sp500", sp500)):
        df = f()
        df.to_csv(DOSSIER / f"{nom}.csv", index=False)
        print(nom, len(df), "actions")
