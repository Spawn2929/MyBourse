import math

import numpy as np
import pandas as pd

from mybourse import auto, backtest, technique

JOURS = pd.bdate_range("2023-01-02", periods=600)


def cours(pente, bruit=0.0, graine=0):
    rng = np.random.default_rng(graine)
    v = 100 * np.exp(pente * np.arange(len(JOURS)) + bruit * rng.standard_normal(len(JOURS)).cumsum())
    vol = 1000 + 500 * rng.random(len(JOURS))
    return pd.DataFrame({"Close": v, "Volume": vol}, index=JOURS)


def test_indicateurs_jour_par_jour_identiques_a_l_analyse_du_soir():
    df = cours(0.001, 0.02, graine=3)
    s = technique.series_indicateurs(df)
    for jour in (JOURS[250], JOURS[400], JOURS[-1]):
        attendu = technique.indicateurs(df.loc[:jour])
        obtenu = technique.ligne_vers_indicateurs(jour, s.loc[jour])
        assert obtenu.keys() == attendu.keys()
        for k, v in attendu.items():
            if isinstance(v, float):
                assert math.isclose(obtenu[k], v, rel_tol=1e-9, abs_tol=1e-9), k
            else:
                assert obtenu[k] == v, k
    assert technique.ligne_vers_indicateurs(JOURS[100], s.loc[JOURS[100]]) is None   # historique trop court


def univers():
    titres, ajustes = {}, {}
    for zone, devise in (("FR", "EUR"), ("EU", "GBp"), ("US", "USD")):
        for i, pente in enumerate((0.002, 0.0005, -0.001)):
            t = f"{zone}{i}"
            titres[t] = {"nom": t, "marche": zone, "secteur": "", "devise": devise}
            ajustes[t] = cours(pente, 0.01, graine=i + 10 * len(titres))
    cotes = {t: df["Close"] for t, df in ajustes.items()}
    change = {"GBp": pd.Series(0.0115, index=JOURS), "USD": pd.Series(0.9, index=JOURS)}
    return titres, ajustes, cotes, change


def lancer(titres, ajustes, cotes, change, cal):
    return backtest.simuler(titres, ajustes, cotes, change, cal, {"technique": 100},
                            {"FR": 1, "EU": 1, "US": 1}, None)


def test_simulation_hebdomadaire_aux_regles_du_portefeuille_automatique():
    titres, ajustes, cotes, change = univers()
    cal = list(JOURS[300:])
    e = lancer(titres, ajustes, cotes, change, cal)
    assert [v[0] for v in e["valeurs"]] == [j.strftime("%Y-%m-%d") for j in cal]
    # Les ordres ne sont passés que le premier jour de bourse de chaque semaine
    jours_ordres = {pd.Timestamp(o["date"], unit="ms").normalize() for o in e["operations"]}
    assert jours_ordres <= set(backtest.jours_de_reequilibrage(cal))
    # Premier jour : 3 achats à parts égales (un par zone), convertis en euros
    premiers = [o for o in e["operations"] if o["date"] == e["operations"][0]["date"]]
    assert sorted(o["ticker"][:2] for o in premiers) == ["EU", "FR", "US"]
    assert {o["change"] for o in premiers} == {1.0, 0.0115, 0.9}
    assert e["liquidites"] >= 0 and len(e["positions"]) == 3


def test_aucun_regard_vers_l_avenir():
    titres, ajustes, cotes, change = univers()
    cal = list(JOURS[300:])
    coupure = cal[150]
    e1 = lancer(titres, ajustes, cotes, change, cal)
    # On bouleverse les cours après la coupure : les ordres passés avant ne doivent pas changer
    for t in ajustes:
        f = ajustes[t].copy()
        f.loc[f.index > coupure, "Close"] *= np.linspace(0.2, 3, (f.index > coupure).sum())
        ajustes[t], cotes[t] = f, f["Close"]
    e2 = lancer(titres, ajustes, cotes, change, cal)
    avant = lambda e: [o for o in e["operations"] if o["date"] <= backtest._horodatage(coupure)]  # noqa: E731
    assert avant(e1) == avant(e2) and avant(e1)
    assert e1["valeurs"][:151] == e2["valeurs"][:151]


def test_titre_sans_cours_recent_non_classe():
    titres, ajustes, cotes, change = univers()
    ajustes["FR0"] = ajustes["FR0"].loc[:JOURS[350]]          # meilleur titre France, plus coté après
    cotes["FR0"] = ajustes["FR0"]["Close"]
    tableau = backtest.tableau_indicateurs(ajustes, [JOURS[352], JOURS[400]])
    assert "FR0" in backtest.indicateurs_du_jour(tableau, JOURS[352])
    assert "FR0" not in backtest.indicateurs_du_jour(tableau, JOURS[400])


def test_bilan():
    e = auto.nouveau(10_000, 0.1, 1.0, "2025-01-06")
    e["valeurs"] = [["2025-01-06", 10_000], ["2025-01-13", 12_000], ["2025-01-20", 9_000], ["2026-01-06", 11_000]]
    e["operations"] = [{"ticker": "A", "frais": 1.5}, {"ticker": "B", "frais": 2.0}, {"ticker": "A", "frais": 1.0}]
    idx = pd.to_datetime(["2025-01-02", "2025-01-06", "2025-06-01", "2026-01-06", "2026-02-01"])
    b = backtest.bilan(e, {"^X": pd.Series([50.0, 100, 80, 110, 999], index=idx)})
    assert b["perf_totale"] == 10.0
    assert abs(b["perf_annuelle"] - 10.0) < 0.05                 # un an pile (365 jours)
    assert b["pire_baisse"] == -25.0                              # de 12 000 à 9 000
    assert (b["semaines"], b["semaines_gagnantes"]) == (4, 50.0)   # 0 %, +20 %, −25 %, +22 %
    assert (b["operations"], b["frais"], b["titres_differents"]) == (3, 4.5, 2)
    assert b["indices"] == {"^X": 10.0}                           # du 6 janvier 2025 au 6 janvier 2026
