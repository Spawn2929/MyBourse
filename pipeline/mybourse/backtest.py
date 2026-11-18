"""Backtest : rejoue la sélection et le portefeuille automatique sur les années passées.

Chaque début de semaine, le score de chaque titre est recalculé avec les seuls cours connus ce
jour-là (aucun regard vers l'avenir), puis le portefeuille applique exactement les règles du
portefeuille automatique (auto.py) : 15 titres à parts égales, sortants vendus, entrants achetés,
titres entiers, frais simulés, conversion en euros au taux du jour.

Les indicateurs sont calculés sur les cours ajustés des dividendes (comme l'analyse du soir) ;
les achats, les ventes et la valeur du portefeuille utilisent le cours réellement coté
(dividendes non comptés, comme pour les indices de comparaison).

Limite connue : la liste des titres est celle d'aujourd'hui. Les sociétés disparues depuis
(faillites, rachats) n'y sont pas, ce qui rend le résultat un peu flatteur.
"""
from __future__ import annotations

from datetime import datetime, timezone

import pandas as pd

from . import analyse, auto, technique

ZONES = ("FR", "EU", "US")
FRAICHEUR_JOURS = 7  # un titre sans cours depuis plus longtemps n'est pas classé ce jour-là


def calendrier(cours: dict[str, pd.DataFrame], indices: list[str], debut: pd.Timestamp,
               fin: pd.Timestamp) -> list[pd.Timestamp]:
    """Jours de bourse : ceux où au moins un indice de comparaison a coté."""
    jours: set[pd.Timestamp] = set()
    for sym in indices:
        if sym in cours:
            jours |= set(cours[sym].index)
    return sorted(j for j in jours if debut <= j <= fin)


def jours_de_reequilibrage(cal: list[pd.Timestamp]) -> list[pd.Timestamp]:
    """Premier jour de bourse de chaque semaine."""
    res, vues = [], set()
    for j in cal:
        s = j.isocalendar()[:2]
        if s not in vues:
            vues.add(s)
            res.append(j)
    return res


def tableau_indicateurs(cours: dict[str, pd.DataFrame], dates: list[pd.Timestamp]) -> pd.DataFrame:
    """Indicateurs de chaque titre aux dates demandées : lignes = dates, colonnes = (titre, indicateur)."""
    idx = pd.DatetimeIndex(dates)
    morceaux = {}
    for t, df in cours.items():
        s = technique.series_indicateurs(df)
        if s.empty:
            continue
        s["jour"] = (s.index - pd.Timestamp("1970-01-01")).days
        morceaux[t] = s.reindex(idx, method="ffill")
    return pd.concat(morceaux, axis=1) if morceaux else pd.DataFrame(index=idx)


def indicateurs_du_jour(tableau: pd.DataFrame, jour: pd.Timestamp) -> dict[str, dict]:
    """Indicateurs connus ce jour-là, au format de technique.indicateurs()."""
    if tableau.empty:
        return {}
    lignes = tableau.loc[jour].unstack()
    res = {}
    aujourd_hui = (jour - pd.Timestamp("1970-01-01")).days
    for t, ligne in lignes.iterrows():
        if pd.isna(ligne["jour"]) or aujourd_hui - ligne["jour"] > FRAICHEUR_JOURS:
            continue
        date = pd.Timestamp("1970-01-01") + pd.Timedelta(days=int(ligne["jour"]))
        ind = technique.ligne_vers_indicateurs(date, ligne.drop("jour"))
        if ind:
            res[t] = ind
    return res


def _horodatage(jour: pd.Timestamp) -> int:
    """Le soir du jour (22 h 30 UTC), en millisecondes, comme l'analyse quotidienne."""
    return int(datetime(jour.year, jour.month, jour.day, 22, 30, tzinfo=timezone.utc).timestamp() * 1000)


def simuler(titres: dict[str, dict], cours_ajustes: dict[str, pd.DataFrame], cotes: dict[str, pd.Series],
            change: dict[str, pd.Series], cal: list[pd.Timestamp], ponderations: dict,
            top_par_marche: dict[str, int], volatilite_max: float | None,
            capital: float = 10_000.0, pct: float = 0.1, minimum: float = 1.0) -> dict:
    """Rejoue le portefeuille automatique jour par jour ; renvoie son état final (format auto.py).

    titres : ticker -> {nom, marche, devise, grande?} ; cours_ajustes : Close (ajusté) et Volume ;
    cotes : cours réellement coté ; change : devise -> euros pour 1 unité (EUR absent = 1).
    """
    tableau = tableau_indicateurs(cours_ajustes, jours_de_reequilibrage(cal))

    def info(t: str, jour: pd.Timestamp) -> dict | None:
        s = cotes.get(t)
        prix = s.asof(jour) if s is not None and not s.empty else None
        devise = titres[t]["devise"]
        ch = 1.0 if devise == "EUR" else (change[devise].asof(jour) if devise in change else None)
        if prix is None or ch is None or pd.isna(prix) or pd.isna(ch) or prix <= 0:
            return None
        return {"prix": round(float(prix), 4), "change": float(ch), "nom": titres[t]["nom"], "devise": devise}

    etat = None
    for j in cal:
        jour = j.strftime("%Y-%m-%d")
        cibles: list[str] = []
        if etat is None or auto._semaine(jour) != auto._semaine(etat["dernier_reequilibrage"]):
            ind = indicateurs_du_jour(tableau, j)
            _, top = analyse.classer({t: titres[t] for t in ind}, ind, ponderations, top_par_marche, volatilite_max)
            cibles = [t for z in ZONES for t in top.get(z, [])]
        a_coter = set(cibles) | set(etat["positions"] if etat else ())
        infos = {t: i for t in a_coter if (i := info(t, j)) is not None}
        etat = auto.mettre_a_jour(etat, jour, cibles, infos, _horodatage(j), capital, pct, minimum)
    return etat


def bilan(etat: dict, indices: dict[str, pd.Series]) -> dict:
    """Chiffres clés : performance, pire baisse, semaines gagnantes, opérations, frais, indices."""
    capital = etat["capital"]
    vals = pd.Series({pd.Timestamp(d): v for d, v in etat["valeurs"]}, dtype=float)
    debut, fin = vals.index[0], vals.index[-1]
    v_fin = float(vals.iloc[-1])
    jours = max((fin - debut).days, 1)
    avec_depart = pd.concat([pd.Series([capital], dtype=float), vals.reset_index(drop=True)])
    pire = float((avec_depart / avec_depart.cummax() - 1).min() * 100)
    semaines = vals.groupby([vals.index.isocalendar().year, vals.index.isocalendar().week]).last()
    variations = pd.concat([pd.Series([capital], dtype=float), semaines.reset_index(drop=True)]).pct_change().dropna()
    perf_indices = {}
    for sym, s in indices.items():
        s = s[(s.index >= debut) & (s.index <= fin)].dropna()
        if len(s) >= 2:
            perf_indices[sym] = round(float(s.iloc[-1] / s.iloc[0] - 1) * 100, 2)
    return {
        "valeur_finale": round(v_fin, 2),
        "perf_totale": round((v_fin / capital - 1) * 100, 2),
        "perf_annuelle": round(((v_fin / capital) ** (365.25 / jours) - 1) * 100, 2),
        "pire_baisse": round(pire, 2),
        "semaines_gagnantes": round(float((variations > 0).mean() * 100), 1) if len(variations) else None,
        "semaines": int(len(variations)),
        "operations": len(etat["operations"]),
        "frais": round(sum(o["frais"] for o in etat["operations"]), 2),
        "titres_differents": len({o["ticker"] for o in etat["operations"]}),
        "indices": perf_indices,
    }


def document(etat: dict, indices: dict[str, pd.Series], noms: dict[str, str], annees: int,
             maintenant: datetime | None = None) -> dict:
    """Contenu de backtest.json, lu par l'appli."""
    maintenant = maintenant or datetime.now(timezone.utc)
    debut, fin = etat["valeurs"][0][0], etat["valeurs"][-1][0]
    return {
        "format": 1,
        "calcule_le": maintenant.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "annees": annees,
        "debut": debut,
        "fin": fin,
        "portefeuille": auto.pour_appli(etat),
        "bilan": bilan(etat, indices),
        "indices": {
            sym: {"nom": noms.get(sym, sym),
                  "historique": [[d.strftime("%Y-%m-%d"), round(float(v), 4)]
                                 for d, v in s[(s.index >= debut) & (s.index <= fin)].dropna().items()]}
            for sym, s in indices.items()
        },
    }
