"""Indicateurs techniques et score technique (0 à 100), avec des raisons en français clair."""
from __future__ import annotations

import math

import numpy as np
import pandas as pd

JOURS_MIN = 210  # il faut au moins ~10 mois de cours pour la moyenne 200 jours


def rsi(close: pd.Series, n: int = 14) -> pd.Series:
    """RSI de Wilder : 0-100, au-dessus de 70 = « surachat », sous 30 = « survente »."""
    delta = close.diff()
    hausse = delta.clip(lower=0).ewm(alpha=1 / n, adjust=False, min_periods=n).mean()
    baisse = (-delta.clip(upper=0)).ewm(alpha=1 / n, adjust=False, min_periods=n).mean()
    rs = hausse / baisse.replace(0, np.nan)
    return (100 - 100 / (1 + rs)).fillna(100)


def macd_hist(close: pd.Series) -> pd.Series:
    """Histogramme MACD (12, 26, 9) rapporté au cours, en % : positif = élan haussier."""
    macd = close.ewm(span=12, adjust=False).mean() - close.ewm(span=26, adjust=False).mean()
    signal = macd.ewm(span=9, adjust=False).mean()
    return (macd - signal) / close * 100


def perf(close: pd.Series, jours: int) -> float | None:
    if len(close) <= jours:
        return None
    return float(close.iloc[-1] / close.iloc[-1 - jours] - 1) * 100


def indicateurs(df: pd.DataFrame) -> dict | None:
    """df : colonnes Close et Volume, index de dates. Renvoie None si l'historique est insuffisant."""
    df = df.dropna(subset=["Close"])
    if len(df) < JOURS_MIN:
        return None
    close, vol = df["Close"], df["Volume"].fillna(0)
    sma50, sma200 = close.rolling(50).mean(), close.rolling(200).mean()
    hist = macd_hist(close)
    vol20, vol100 = vol.tail(20).mean(), vol.tail(100).mean()
    return {
        "date": df.index[-1].strftime("%Y-%m-%d"),
        "cours": round(float(close.iloc[-1]), 4),
        "var_1j": perf(close, 1),
        "perf_1m": perf(close, 21),
        "perf_3m": perf(close, 63),
        "perf_6m": perf(close, 126),
        "perf_1a": perf(close, 252),
        "rsi": float(rsi(close).iloc[-1]),
        "mm50": float(sma50.iloc[-1]),
        "mm200": float(sma200.iloc[-1]),
        "macd_hist": float(hist.iloc[-1]),
        "macd_hist_5j": float(hist.iloc[-6]),
        "ratio_volume": float(vol20 / vol100) if vol100 > 0 else None,
        "volatilite": volatilite(close),
    }


def series_indicateurs(df: pd.DataFrame) -> pd.DataFrame:
    """Les mêmes indicateurs que indicateurs(), calculés pour chaque jour d'un coup (backtest).

    La ligne du jour J ne dépend que des cours jusqu'à J : identique à indicateurs(df.loc[:J]).
    Colonne « points » : nombre de cours connus à cette date (JOURS_MIN au moins pour être analysé).
    """
    df = df.dropna(subset=["Close"])
    close, vol = df["Close"], df["Volume"].fillna(0)
    hist = macd_hist(close)
    vol20, vol100 = vol.rolling(20, min_periods=1).mean(), vol.rolling(100, min_periods=1).mean()

    def perf_n(n):
        return (close / close.shift(n) - 1) * 100

    rendements = np.log(close.where(close > 0)).diff()
    return pd.DataFrame({
        "cours": close.round(4),
        "var_1j": perf_n(1), "perf_1m": perf_n(21), "perf_3m": perf_n(63),
        "perf_6m": perf_n(126), "perf_1a": perf_n(252),
        "rsi": rsi(close),
        "mm50": close.rolling(50).mean(), "mm200": close.rolling(200).mean(),
        "macd_hist": hist, "macd_hist_5j": hist.shift(5),
        "ratio_volume": (vol20 / vol100).where(vol100 > 0),
        "volatilite": rendements.rolling(252, min_periods=60).std() * math.sqrt(252) * 100,
        "points": np.arange(1, len(close) + 1),
    }, index=df.index)


def ligne_vers_indicateurs(date: pd.Timestamp, ligne: pd.Series) -> dict | None:
    """Une ligne de series_indicateurs() au format de indicateurs() ; None si l'historique est trop court."""
    if ligne["points"] < JOURS_MIN:
        return None
    ind = {k: (None if pd.isna(v) else float(v)) for k, v in ligne.items() if k != "points"}
    ind["date"] = date.strftime("%Y-%m-%d")
    return ind


def volatilite(close: pd.Series, jours: int = 252) -> float | None:
    """Volatilité annualisée sur un an, en % : l'amplitude habituelle des variations du cours."""
    rendements = np.log(close.where(close > 0)).diff().dropna().tail(jours)
    if len(rendements) < 60:
        return None
    return float(rendements.std() * math.sqrt(252) * 100)


def _note_rsi(r: float) -> float:
    if r < 30:
        return 0.3
    if r < 45:
        return 0.4
    if r < 55:
        return 0.6
    if r <= 70:
        return 1.0
    if r <= 80:
        return 0.5
    return 0.2


def score_technique(ind: dict, rang_6m: float, rang_3m: float) -> tuple[float, dict]:
    """Score 0-100 et notes par composante (0-1).

    rang_6m / rang_3m : rang de la performance parmi les actions du même marché (0 = pire, 1 = meilleure).
    """
    c, mm50, mm200 = ind["cours"], ind["mm50"], ind["mm200"]
    tendance = 0.4 * (c > mm200) + 0.3 * (mm50 > mm200) + 0.3 * (c > mm50)
    momentum = 0.6 * rang_6m + 0.4 * rang_3m
    note_rsi = _note_rsi(ind["rsi"])
    h, h5 = ind["macd_hist"], ind["macd_hist_5j"]
    macd = 0.6 * (h > 0) + 0.4 * (h > h5)
    ratio, p1m = ind["ratio_volume"] or 1.0, ind["perf_1m"] or 0.0
    if p1m > 0:
        volume = 1.0 if ratio > 1.1 else 0.6
    else:
        volume = 0.1 if ratio > 1.1 else 0.4
    notes = {"tendance": tendance, "momentum": momentum, "rsi": note_rsi, "macd": macd, "volume": volume}
    poids = {"tendance": 0.30, "momentum": 0.35, "rsi": 0.15, "macd": 0.10, "volume": 0.10}
    score = 100 * sum(poids[k] * float(v) for k, v in notes.items())
    return round(score, 1), {k: round(float(v), 2) for k, v in notes.items()}


def _pct(x: float) -> str:
    a = round(abs(x), 1)
    s = f"{a:.0f}" if a >= 10 else f"{a:.1f}".replace(".", ",")
    return f"{'+' if x >= 0 else '−'}{s} %"


def raisons_technique(ind: dict, notes: dict, rang_6m: float) -> list[tuple[float, str]]:
    """Liste de (importance, phrase). Les phrases les plus importantes sont affichées en premier."""
    r: list[tuple[float, str]] = []
    c, mm50, mm200 = ind["cours"], ind["mm50"], ind["mm200"]
    p6, p3 = ind["perf_6m"], ind["perf_3m"]
    if p6 is not None and rang_6m >= 0.8:
        part = max(1, math.ceil((1 - rang_6m) * 10)) * 10
        r.append((0.9, f"{_pct(p6)} sur 6 mois : parmi les {part} % les plus fortes hausses de son marché"))
    elif p3 is not None and p3 > 5:
        r.append((0.6, f"{_pct(p3)} sur 3 mois"))
    if c > mm200 and mm50 > mm200:
        ecart = (c / mm200 - 1) * 100
        r.append((0.8, f"Tendance de fond haussière : cours {_pct(ecart)} au-dessus de sa moyenne 200 jours"))
    elif c > mm50 and c < mm200:
        r.append((0.4, "Repasse au-dessus de sa moyenne 50 jours, mais reste sous sa moyenne 200 jours"))
    rs = ind["rsi"]
    if 55 <= rs <= 70:
        r.append((0.6, f"Élan acheteur sans excès (RSI à {rs:.0f})"))
    elif rs > 75:
        r.append((0.85, f"Attention : RSI à {rs:.0f}, forte hausse récente, un repli est possible"))
    elif rs < 30:
        r.append((0.7, f"Attention : RSI à {rs:.0f}, l'action a beaucoup baissé récemment"))
    if ind["macd_hist"] > 0 and ind["macd_hist_5j"] <= 0:
        r.append((0.75, "Le MACD vient de repasser à la hausse (signal d'achat technique)"))
    elif notes["macd"] >= 1:
        r.append((0.4, "Le MACD confirme l'accélération à la hausse"))
    if notes["volume"] >= 1:
        r.append((0.5, "Volumes d'échanges en hausse pendant la progression"))
    # Points faibles (utiles sur la fiche d'une action mal classée)
    if c < mm200:
        ecart = (c / mm200 - 1) * 100
        r.append((0.55, f"Tendance de fond baissière : cours {_pct(ecart)} sous sa moyenne 200 jours"))
    if p6 is not None and rang_6m <= 0.2:
        r.append((0.5, f"{_pct(p6)} sur 6 mois : parmi les plus faibles de son marché"))
    if notes["volume"] <= 0.1:
        r.append((0.3, "Volumes en hausse pendant la baisse (pression vendeuse)"))
    if not r:
        r.append((0.1, "Aucun signal technique marquant pour l'instant"))
    return r
