"""Tries ways of combining rules and AI offline: rules from the phone's Eval log, AI probabilities on the PC.

    python simulate.py                      # after train_heads.py and a phone Eval run (model/eval_log.txt)

Policy "rules-first": rules decide hazard/trapped when they found something; the AI fills only what they left
unknown, and only above a bar. Injuries and vulnerable people: rules ∪ AI-above-bar. Prints whole-record accuracy
per set for a few bars, so the bar is picked on all sets together, not on one.
"""
import json
import re
from pathlib import Path

import numpy as np

from embed import Embedder, clauses
from score_log import FIELDS, facts, log
from train_heads import ASSETS, COND, VULN, probs

heads = json.loads((ASSETS / "heads.json").read_text(encoding="utf-8"))
emb = Embedder()

rules = {}
for block in re.split(r"\n(?=\S)", log):
    m = re.match(r"(.+?) #\d+ \[[^\]]*\] .*?\"(.*)\"$", block.splitlines()[0]) if block.strip() else None
    if m and "  rules=" in block:
        rules[m.group(2)] = facts(re.search(r"^  rules=(Facts\(.*\))$", block, re.M).group(1))


def ai_probs(text):
    cs = clauses(text)
    V = np.stack([emb(t) for t in [text] + (cs if len(cs) > 1 else [])])
    p = {}
    for name, h in heads["softmax"].items():
        P = probs(V, np.array(h["W"]).T, np.array(h["b"]), True).max(0)
        p[name] = dict(zip(h["labels"], P))
    for l, h in heads["sigmoid"].items():
        p[l] = probs(V, np.array(h["w"])[:, None], np.array([h["b"]]), False).max()
    return p


def combine(r, p, hz, tr, cond_k, vuln_k):
    out = dict(r)
    if r["hazard"] == "null":
        best = max(((v, k) for k, v in p["hazard"].items() if k != "NONE"))
        if best[0] >= hz:
            out["hazard"] = best[1]
    if r["trapped"] == "null":
        y, n = p["trapped"]["YES"], p["trapped"]["NO"]
        if max(y, n) >= tr:
            out["trapped"] = "true" if y >= n else "false"
    out["cond"] = r["cond"] | {l for l in COND if p[l] >= max(heads["sigmoid"][l]["min"], cond_k)}
    if "NOT_BREATHING" in out["cond"] - r["cond"] and re.search(r"breath|huminga|makahinga|dibdib", r.get("_t", ""), re.I):
        out["cond"] = out["cond"] - {"NOT_BREATHING"}
    out["vuln"] = r["vuln"] | {l for l in VULN if p[l] >= max(heads["sigmoid"][l]["min"], vuln_k)}
    return out


def want(c):
    e = c["expect"]
    return {"hazard": e["hazard"] if e["hazard"] != "NONE" else "null", "people": e["people"],
            "trapped": {"YES": "true", "NO": "false"}.get(e["trapped"], "null"), "floor": e["floor"], "spot": e["spot"],
            "vuln": frozenset(e["vulnerable"]), "cond": frozenset(e["condition"])}


sets = {"tuning": [c for c in json.loads((ASSETS / "eval" / "extract.json").read_text(encoding="utf-8")) if c.get("by") != "others"],
        "others": [c for c in json.loads((ASSETS / "eval" / "extract.json").read_text(encoding="utf-8")) if c.get("by") == "others"],
        "real": json.loads((ASSETS / "eval" / "real.json").read_text(encoding="utf-8")),
        "hard": json.loads((ASSETS / "eval" / "hard.json").read_text(encoding="utf-8"))}
P = {c["text"]: ai_probs(c["text"]) for cs in sets.values() for c in cs}

print(f"{'bars (hazard, trapped, cond, vuln)':36s} " + " ".join(f"{k:>7s}" for k in sets) + "   (whole-record accuracy)")
print(f"{'rules only':36s} " + " ".join(f"{sum(rules[c['text']] == want(c) for c in cs) / len(cs):7.0%}" for cs in sets.values()))
for bars in [(0.5, 0.6, 0, 0), (0.7, 0.7, 0, 0.5), (0.8, 0.8, 0.5, 0.7), (0.9, 0.9, 0.7, 0.8), (0.95, 0.9, 0.8, 0.9)]:
    row = []
    for cs in sets.values():
        ok = 0
        for c in cs:
            r = {**rules[c["text"]], "_t": c["text"]}
            got = combine(r, P[c["text"]], *bars)
            got.pop("_t", None)
            ok += got == want(c)
        row.append(ok / len(cs))
    print(f"{str(bars):36s} " + " ".join(f"{x:7.0%}" for x in row))

# Per field on the sets nothing was tuned on, for the chosen bar (edit to match the line you pick above).
