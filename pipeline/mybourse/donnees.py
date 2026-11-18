"""Téléchargement des cours (yfinance), par lots avec une pause entre deux lots."""
from __future__ import annotations

import logging
import time

import pandas as pd
import yfinance as yf

log = logging.getLogger(__name__)

SUFFIXES_EURO = (".PA", ".AS", ".DE", ".MI", ".MC", ".BR", ".LS", ".HE", ".VI", ".IR", ".F")


def _decouper(df: pd.DataFrame, tickers: list[str]) -> dict[str, pd.DataFrame]:
    res = {}
    for t in tickers:
        try:
            sub = df[t] if isinstance(df.columns, pd.MultiIndex) else df
        except KeyError:
            continue
        if "Close" not in sub:
            continue
        sub = sub[["Close", "Volume"]].dropna(subset=["Close"])
        if not sub.empty:
            sub.index = pd.to_datetime(sub.index).tz_localize(None)
            res[t] = sub
    return res


def telecharger(tickers: list[str], periode: str = "2y", taille_lot: int = 100,
                pause: float = 3.0) -> dict[str, pd.DataFrame]:
    """Cours quotidiens (clôture ajustée, volume) par titre. Les titres en échec sont retentés une fois."""
    res: dict[str, pd.DataFrame] = {}
    a_faire = list(dict.fromkeys(tickers))
    for essai in range(2):
        for i in range(0, len(a_faire), taille_lot):
            lot = a_faire[i:i + taille_lot]
            if i or essai:
                time.sleep(pause)
            df = yf.download(lot, period=periode, interval="1d", auto_adjust=True,
                             group_by="ticker", threads=True, progress=False)
            res.update(_decouper(df, lot))
        a_faire = [t for t in a_faire if t not in res]
        if not a_faire:
            break
        log.warning("Nouvel essai pour %d titres : %s", len(a_faire), a_faire[:10])
    if a_faire:
        log.warning("Cours introuvables pour : %s", a_faire)
    return res


def nom_titre(ticker: str) -> str:
    """Nom d'un titre hors univers (ma liste)."""
    try:
        info = yf.Ticker(ticker).info
        return info.get("shortName") or info.get("longName") or ticker
    except Exception:  # noqa: BLE001 - un nom manquant ne doit pas arrêter l'analyse
        return ticker


def devise(ticker: str) -> str:
    """Devise de cotation, déduite du suffixe Yahoo (sinon demandée à Yahoo)."""
    if ticker.endswith(SUFFIXES_EURO):
        return "EUR"
    if ticker.endswith(".L"):
        return "GBp"
    autres = {".SW": "CHF", ".ST": "SEK", ".CO": "DKK", ".OL": "NOK"}
    for suffixe, dev in autres.items():
        if ticker.endswith(suffixe):
            return dev
    if "." not in ticker:
        return "USD"
    try:
        return yf.Ticker(ticker).fast_info["currency"]
    except Exception:  # noqa: BLE001
        return "USD"
