from mybourse import auto


def info(prix, change=1.0, devise="EUR"):
    return {"prix": prix, "change": change, "nom": "N", "devise": devise}


def test_depart_a_parts_egales():
    infos = {"A": info(10.0), "B": info(100.0), "C": info(33.0)}
    e = auto.mettre_a_jour(None, "2026-11-18", ["A", "B", "C"], infos, 1)   # mercredi
    # 3 333,33 € par titre, frais 0,1 % (minimum 1 €)
    assert e["positions"]["A"]["quantite"] == 333     # 3 330 + 3,33 de frais = 3 333,33
    assert e["positions"]["B"]["quantite"] == 33
    assert e["positions"]["C"]["quantite"] == 100
    achats = sum(o["quantite"] * o["prix"] + o["frais"] for o in e["operations"])
    assert abs(e["liquidites"] - (10_000 - achats)) < 0.01 and e["liquidites"] >= 0
    assert e["valeurs"] == [["2026-11-18", auto.valeur(e, infos)]]


def test_meme_semaine_pas_de_nouvel_ordre_puis_lundi_suivant():
    infos = {"A": info(10.0), "B": info(100.0), "C": info(50.0)}
    e = auto.mettre_a_jour(None, "2026-11-18", ["A", "B"], infos, 1)
    n = len(e["operations"])
    e = auto.mettre_a_jour(e, "2026-11-19", ["B", "C"], infos, 2)            # jeudi : rien ne bouge
    assert len(e["operations"]) == n and "A" in e["positions"]
    e = auto.mettre_a_jour(e, "2026-11-23", ["B", "C"], infos, 3)            # lundi : A vendu, C acheté, B gardé
    assert "A" not in e["positions"] and e["positions"]["C"]["quantite"] > 0
    assert e["positions"]["B"]["quantite"] == 49
    assert [o["sens"] for o in e["operations"][n:]] == ["VENTE", "ACHAT"]
    assert [v[0] for v in e["valeurs"]] == ["2026-11-18", "2026-11-19", "2026-11-23"]


def test_relance_le_meme_soir_ne_double_pas():
    infos = {"A": info(10.0)}
    e = auto.mettre_a_jour(None, "2026-11-23", ["A"], infos, 1)
    e = auto.mettre_a_jour(e, "2026-11-23", ["A"], infos, 2)
    assert len(e["operations"]) == 1 and len(e["valeurs"]) == 1


def test_pence_et_titre_sans_cours():
    # 500 pence à 1,15 € la livre : 5,75 € le titre
    infos = {"L": info(500.0, 1.15 / 100, "GBp")}
    e = auto.mettre_a_jour(None, "2026-11-23", ["L"], infos, 1)
    assert e["positions"]["L"]["quantite"] == 1737          # 9 987,75 + 9,99 de frais
    v = auto.valeur(e, {})                                   # pas de cours ce soir : dernier cours connu
    assert abs(v - auto.valeur(e, infos)) < 0.01


def test_arrondi_comme_l_appli():
    assert auto.centimes(0.125) == 0.13 and auto.frais_pour(100, 0.1, 1) == 1.0 and auto.frais_pour(5000, 0.1, 1) == 5.0
