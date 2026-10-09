"""Trains the classifier heads on EmbeddingGemma vectors and writes app/src/main/assets/heads.json.

    python make_training.py && python train_heads.py

Heads are plain logistic regression on the 768-number meaning vector: softmax for hazard and trapped, one sigmoid
per condition and vulnerable group. Thresholds are picked on a validation split for high precision (a miss is
safer than a wrong pick), then the frozen test sets are scored per field. The app applies the same heads to the
whole report and to each clause and keeps the highest probability per label (Triage.kt: applyHeads).
"""
import json
from pathlib import Path

import numpy as np

from embed import Embedder, clauses

HERE = Path(__file__).parent
ASSETS = HERE.parent / "app" / "src" / "main" / "assets"
CACHE = HERE / "model" / "vectors.npz"

HAZARD = ["NONE", "COLLAPSE", "FLOOD", "LANDSLIDE"]
TRAPPED = ["UNKNOWN", "YES", "NO"]
COND = ["NOT_BREATHING", "UNCONSCIOUS", "HEAVY_BLEEDING", "PINNED", "INJURED", "WATER_RISING"]
VULN = ["INFANT", "CHILD", "ELDERLY", "PREGNANT", "PWD"]
LIFE = {"NOT_BREATHING", "UNCONSCIOUS", "HEAVY_BLEEDING"}  # wrong picks start CPR / bleeding control: stricter


def load_cache():
    if CACHE.exists():
        z = np.load(CACHE, allow_pickle=True)
        return dict(zip(z["keys"].tolist(), z["vecs"]))
    return {}


def save_cache(c):
    np.savez(CACHE, keys=np.array(list(c), dtype=object), vecs=np.stack(list(c.values())))


def adam(X, Y, softmax, l2=1e-5, steps=4000, lr=0.1):
    """Logistic regression with Adam. Y is one-hot (softmax) or 0/1 (sigmoid). Returns W (d×k), b (k)."""
    n, d = X.shape
    k = Y.shape[1]
    W, b = np.zeros((d, k)), np.zeros(k)
    m = [np.zeros_like(W), np.zeros_like(b)]
    v = [np.zeros_like(W), np.zeros_like(b)]
    for t in range(1, steps + 1):
        Z = X @ W + b
        if softmax:
            Z -= Z.max(1, keepdims=True)
            P = np.exp(Z)
            P /= P.sum(1, keepdims=True)
        else:
            P = 1 / (1 + np.exp(-Z))
        G = (P - Y) / n
        grads = [X.T @ G + l2 * W, G.sum(0)]
        for i, (p, g) in enumerate(zip((W, b), grads)):
            m[i] = 0.9 * m[i] + 0.1 * g
            v[i] = 0.999 * v[i] + 0.001 * g * g
            p -= lr * (m[i] / (1 - 0.9 ** t)) / (np.sqrt(v[i] / (1 - 0.999 ** t)) + 1e-8)
    return W, b


def probs(X, W, b, softmax):
    Z = X @ W + b
    if softmax:
        Z = Z - Z.max(-1, keepdims=True)
        P = np.exp(Z)
        return P / P.sum(-1, keepdims=True)
    return 1 / (1 + np.exp(-Z))


def threshold(p, y, min_precision):
    """Lowest threshold whose precision on validation is >= min_precision (most recall at that precision)."""
    best = 0.99
    for t in np.arange(0.99, 0.2, -0.01):
        pick = p >= t
        if pick.sum() == 0:
            continue
        if y[pick].mean() >= min_precision:
            best = t
        else:
            break
    return round(float(best), 2)


def main():
    emb = Embedder()
    cache = load_cache()
    data = [json.loads(l) for l in (HERE / "reports.jsonl").read_text(encoding="utf-8").splitlines()]
    todo = [d["text"] for d in data if d["text"] not in cache]
    for i, t in enumerate(todo):
        cache[t] = emb(t)
        if i % 250 == 0:
            print(f"embedding {i}/{len(todo)}")
    if todo:
        save_cache(cache)
    X = np.stack([cache[d["text"]] for d in data])

    rng = np.random.default_rng(0)
    idx = rng.permutation(len(data))
    tr, va = idx[: int(0.8 * len(idx))], idx[int(0.8 * len(idx)):]

    heads = {"dim": int(X.shape[1]), "softmax": {}, "sigmoid": {}}
    for name, labels in (("hazard", HAZARD), ("trapped", TRAPPED)):
        Y = np.array([[d[name] == l for l in labels] for d in data], dtype=float)
        W, b = adam(X[tr], Y[tr], softmax=True)
        P = probs(X[va], W, b, True)
        pred, true = P.argmax(1), Y[va].argmax(1)
        # Threshold on the winning non-default label's probability.
        mask = pred != 0
        winner = P[np.arange(len(va)), pred]
        t = threshold(winner[mask], (pred == true)[mask].astype(float), 0.95) if mask.any() else 0.9
        heads["softmax"][name] = {"labels": labels, "W": W.T.round(5).tolist(), "b": b.round(5).tolist(), "min": t}
        print(f"{name:8s} val acc {(pred == true).mean():.3f}  threshold {t}")
    for group, labels in (("cond", COND), ("vuln", VULN)):
        for l in labels:
            y = np.array([l in d[group] for d in data], dtype=float)[:, None]
            W, b = adam(X[tr], y[tr], softmax=False)
            p = probs(X[va], W, b, False)[:, 0]
            t = threshold(p, y[va, 0], 0.97 if l in LIFE else 0.92)
            pick = p >= t
            prec = y[va, 0][pick].mean() if pick.any() else 0
            rec = pick[y[va, 0] == 1].mean()
            heads["sigmoid"][l] = {"w": W[:, 0].round(5).tolist(), "b": round(float(b[0]), 5), "min": t}
            print(f"{l:15s} val precision {prec:.3f} recall {rec:.3f} threshold {t}")

    (ASSETS / "heads.json").write_text(json.dumps(heads, separators=(",", ":")), encoding="utf-8")
    print("heads.json", (ASSETS / "heads.json").stat().st_size // 1024, "KB")
    score_tests(emb, heads)


def apply_heads(emb, heads, text):
    """Same as applyHeads in Triage.kt: whole report + each clause, highest probability per label."""
    texts = [text] + (clauses(text) if len(clauses(text)) > 1 else [])
    V = np.stack([emb(t) for t in texts])
    out = {}
    for name, h in heads["softmax"].items():
        P = probs(V, np.array(h["W"]).T, np.array(h["b"]), True)
        best = max(((P[:, i].max(), l) for i, l in enumerate(h["labels"]) if i > 0), default=(0, None))
        out[name] = best[1] if best[0] >= h["min"] else h["labels"][0]
    out["cond"] = {l for l, h in heads["sigmoid"].items() if l in COND and probs(V, np.array(h["w"])[:, None], np.array([h["b"]]), False).max() >= h["min"]}
    out["vuln"] = {l for l, h in heads["sigmoid"].items() if l in VULN and probs(V, np.array(h["w"])[:, None], np.array([h["b"]]), False).max() >= h["min"]}
    return out


def score_tests(emb, heads):
    sets = {
        "real tweets": json.loads((ASSETS / "eval" / "real.json").read_text(encoding="utf-8")),
        "hard": json.loads((ASSETS / "eval" / "hard.json").read_text(encoding="utf-8")),
        "by others": [c for c in json.loads((ASSETS / "eval" / "extract.json").read_text(encoding="utf-8")) if c.get("by") == "others"],
    }
    print("\nFrozen test sets, AI heads alone (per field: hazard / trapped / conditions / vulnerable):")
    for name, cases in sets.items():
        ok = np.zeros(4)
        wrong_life = []
        for c in cases:
            e, a = c["expect"], apply_heads(emb, heads, c["text"])
            ok += [a["hazard"] == e["hazard"], a["trapped"] == e["trapped"], a["cond"] == set(e["condition"]), a["vuln"] == set(e["vulnerable"])]
            wrong_life += [f"{l} ← {c['text'][:60]}" for l in a["cond"] & LIFE - set(e["condition"])]
        print(f"  {name:12s} n={len(cases):3d}  " + "  ".join(f"{k} {v / len(cases):.0%}" for k, v in zip(("hazard", "trapped", "cond", "vuln"), ok)))
        for w in wrong_life:
            print(f"     WRONG LIFE-THREAT: {w}")


if __name__ == "__main__":
    main()
