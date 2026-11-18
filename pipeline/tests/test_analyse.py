import numpy as np
import pandas as pd
import pytest

from mybourse import analyse, technique


def serie(valeurs, volume=1000.0):
    idx = pd.bdate_range("2024-01-01", periods=len(valeurs))
    return pd.DataFrame({"Close": valeurs, "Volume": volume}, index=idx)


def hausse(n=300, pente=0.002):
    return serie(100 * np.exp(pente * np.arange(n)) * (1 + 0.01 * np.sin(np.arange(n))))


def baisse(n=300):
    return serie(100 * np.exp(-0.002 * np.arange(n)))


def test_rsi_bornes():
    r = technique.rsi(hausse()["Close"])
    assert r.iloc[-1] > 50
    assert technique.rsi(baisse()["Close"]).iloc[-1] < 50
    assert ((r >= 0) & (r <= 100)).all()


def test_historique_insuffisant():
    assert technique.indicateurs(hausse(n=100)) is None


def test_hausse_mieux_notee_que_baisse():
    ih, ib = technique.indicateurs(hausse()), technique.indicateurs(baisse())
    sh, _ = technique.score_technique(ih, 1.0, 1.0)
    sb, _ = technique.score_technique(ib, 0.0, 0.0)
    assert 0 <= sb < sh <= 100


def test_score_global_ignore_familles_absentes():
    poids = {"technique": 40, "fondamental": 30, "sentiment": 15, "macro": 15}
    assert analyse.score_global({"technique": 70, "fondamental": None, "sentiment": None, "macro": None}, poids) == 70
    assert analyse.score_global({"technique": 80, "fondamental": 50, "sentiment": None, "macro": None}, poids) == \
        pytest.approx((40 * 80 + 30 * 50) / 70, abs=0.1)
    assert analyse.score_global({"technique": None}, poids) is None


def test_rangs():
    r = analyse.rangs({"a": 1.0, "b": 2.0, "c": None})
    assert r["b"] > r["a"] and r["c"] == 0.5


def test_classement_et_json():
    titres = {
        "UP.PA": {"nom": "Hausse", "marche": "FR", "secteur": "Industrie", "devise": "EUR"},
        "DOWN.PA": {"nom": "Baisse", "marche": "FR", "secteur": "Industrie", "devise": "EUR"},
        "UPUS": {"nom": "Up US", "marche": "US", "secteur": "Technologie", "devise": "USD"},
        "COURT.PA": {"nom": "Trop récent", "marche": "FR", "secteur": "", "devise": "EUR"},
    }
    cours = {"UP.PA": hausse(), "DOWN.PA": baisse(), "UPUS": hausse(pente=0.001), "COURT.PA": hausse(n=50),
             "^FCHI": hausse()}
    poids = {"technique": 40, "fondamental": 30, "sentiment": 15, "macro": 15}
    fiches, top = analyse.analyser(titres, cours, poids, {"FR": 5, "US": 5})
    assert top["FR"] == ["UP.PA", "DOWN.PA"]
    assert top["US"] == ["UPUS"]
    assert "COURT.PA" not in fiches
    assert 1 <= len(fiches["UP.PA"]["raisons"]) <= 3
    doc = analyse.construire_json(fiches, top, cours, {"^FCHI": "CAC 40"}, poids, 1.08, [])
    assert doc["titres"]["UP.PA"]["historique"][-1][1] == pytest.approx(cours["UP.PA"]["Close"].iloc[-1], rel=1e-4)
    assert doc["indices"]["^FCHI"]["nom"] == "CAC 40"
    assert doc["avertissement"].startswith("Outil personnel")


def test_egalite_departagee_par_code():
    titres = {t: {"nom": t, "marche": "FR", "secteur": "", "devise": "EUR"} for t in ("B.PA", "A.PA")}
    cours = {"A.PA": hausse(), "B.PA": hausse()}
    _, top = analyse.analyser(titres, cours, {"technique": 1}, {"FR": 1})
    assert top["FR"] == ["A.PA"]


def test_journal_remplace_meme_jour():
    fiches = {"X": {"score": 50, "cours": 10}}
    j = analyse.ajouter_journal([], "2026-11-18", {"FR": ["X"]}, fiches)
    j = analyse.ajouter_journal(j, "2026-11-18", {"FR": ["X"]}, fiches)
    j = analyse.ajouter_journal(j, "2026-11-19", {"FR": ["X"]}, fiches)
    assert [e["date"] for e in j] == ["2026-11-18", "2026-11-19"]


def test_toujours_au_moins_une_raison():
    ib = technique.indicateurs(baisse())
    _, notes = technique.score_technique(ib, 0.0, 0.0)
    r = technique.raisons_technique(ib, notes, 0.0)
    assert r and any("baissière" in p for _, p in r)


def test_format_pourcentage():
    assert technique._pct(9.97) == "+10 %"
    assert technique._pct(4.71) == "+4,7 %"
    assert technique._pct(-12.3) == "−12 %"


# ---------- Univers (filtre Yahoo) et grandes valeurs ----------

from mybourse import univers  # noqa: E402

FX = {"EUR": 1.0, "USD": 1.16, "GBP": 0.88, "SEK": 11.0}
FILTRES = {"capitalisation_min_eur": 200e6, "echanges_min_eur_par_jour": 500e3, "cours_min": 1,
           "grande_valeur_eur": 10e9}


def cotation(**kw):
    q = {"symbol": "ABC.PA", "currency": "EUR", "quoteType": "EQUITY", "marketCap": 500e6,
         "regularMarketPrice": 20.0, "averageDailyVolume3Month": 50_000, "longName": "Abc SA"}
    return q | kw


def test_filtre_univers_garde_une_valeur_moyenne():
    t = univers.filtrer(cotation(), "FR", "Industrials", FX, FILTRES)
    assert t["nom"] == "Abc SA" and t["secteur"] == "Industrie" and t["capi_eur"] == 500_000_000


def test_filtre_univers_ecarte_petites_illiquides_et_obligations():
    assert univers.filtrer(cotation(marketCap=150e6), "FR", "Industrials", FX, FILTRES) is None
    assert univers.filtrer(cotation(averageDailyVolume3Month=10_000), "FR", "Industrials", FX, FILTRES) is None  # 200 k€/j
    assert univers.filtrer(cotation(marketCap=None), "FR", "Industrials", FX, FILTRES) is None
    assert univers.filtrer(cotation(regularMarketPrice=0.8, averageDailyVolume3Month=10**7), "FR", "Industrials", FX, FILTRES) is None
    assert univers.filtrer(cotation(symbol="ABC-PA", currency="USD"), "US", "Industrials", FX, FILTRES) is None


def test_filtre_univers_convertit_les_devises():
    # Couronnes suédoises : 1 Md SEK ≈ 91 M€ -> trop petit
    assert univers.filtrer(cotation(symbol="X.ST", currency="SEK", marketCap=1e9), "EU", "Energy", FX, FILTRES) is None
    # Pence : 500 GBp = 5 £ ≈ 5,7 € ; 200 000 titres/jour ≈ 1,1 M€
    t = univers.filtrer(cotation(symbol="X.L", currency="GBp", marketCap=1e9, regularMarketPrice=500,
                                 averageDailyVolume3Month=200_000), "EU", "Energy", FX, FILTRES)
    assert t is not None and t["capi_eur"] == round(1e9 / 0.88)


def test_grandes_valeurs_exclues_du_top():
    titres = {"PETITE": {"nom": "P", "marche": "FR", "devise": "EUR"},
              "GEANT": {"nom": "G", "marche": "FR", "devise": "EUR", "grande": True}}
    cours = {"PETITE": hausse(pente=0.001), "GEANT": hausse(pente=0.003)}
    poids = {"technique": 100}
    fiches, top = analyse.analyser(titres, cours, poids, {"FR": 5})
    assert top["FR"] == ["PETITE"]
    assert fiches["GEANT"]["grande"] is True and fiches["GEANT"]["score"] is not None


def test_titres_trop_agites_exclus_du_top():
    agite = serie(100 * np.exp(0.003 * np.arange(300)) * (1 + 0.08 * np.sin(np.arange(300) * 1.7)))
    titres = {"CALME": {"nom": "C", "marche": "US", "devise": "USD"},
              "FUSEE": {"nom": "F", "marche": "US", "devise": "USD"}}
    cours = {"CALME": hausse(pente=0.001), "FUSEE": agite}
    fiches, top = analyse.analyser(titres, cours, {"technique": 100}, {"US": 5}, volatilite_max=80)
    assert fiches["FUSEE"]["indicateurs"]["volatilite"] > 80
    assert top["US"] == ["CALME"]
    _, top_sans_seuil = analyse.analyser(titres, cours, {"technique": 100}, {"US": 5})
    assert "FUSEE" in top_sans_seuil["US"]


def test_volatilite():
    calme = serie(100 + np.arange(300) * 0.01)
    agite = serie(100 * (1 + 0.05 * np.sin(np.arange(300) * 1.7)))
    assert technique.volatilite(calme["Close"]) < 2 < 30 < technique.volatilite(agite["Close"])
