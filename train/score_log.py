"""Per-field scores from the phone's Eval log (adb logcat -s SakloloEval > model/eval_log.txt).

Compares rules alone, the AI alone and the app's combination on every set, and tries the other way of combining
(rules first, AI fills gaps) so the choice is made on measurements.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

log = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).parent / "model" / "eval_log.txt").read_text(encoding="utf-8")
F = re.compile(r"Facts\(hazard=(\w+), people=(\d+), trapped=(\w+), floor=(\d+), spot=(\w+), vuln=\[([^\]]*)\], cond=\[([^\]]*)\]\)")


def facts(s):
    m = F.search(s)
    h, n, t, fl, sp, v, c = m.groups()
    return {"hazard": h, "people": int(n), "trapped": t, "floor": int(fl), "spot": sp,
            "vuln": frozenset(x.strip() for x in v.split(",") if x.strip()),
            "cond": frozenset(x.strip() for x in c.split(",") if x.strip())}


cases = []
for block in re.split(r"\n(?=\S)", log):
    head = re.match(r"(.+?) #\d+ \[", block)
    if not head or "  want=" not in block or "  AI Facts" not in block:
        continue
    get = lambda k: facts(re.search(rf"^  {k}(?:=| )(Facts\(.*\))$", block, re.M).group(1))
    cases.append((head.group(1), get("want"), get("rules"), get("AI"), get("ai")))

FIELDS = ["hazard", "trapped", "cond", "vuln"]


def rules_first(r, a):  # the alternative: rules decide when they found something, the AI fills gaps
    return {**r, "hazard": r["hazard"] if r["hazard"] != "null" else a["hazard"],
            "trapped": r["trapped"] if r["trapped"] != "null" else a["trapped"],
            "cond": r["cond"] | a["cond"], "vuln": r["vuln"] | a["vuln"]}


by = defaultdict(list)
for c in cases:
    by[c[0]].append(c)
print(f"{'set':32s} {'field':8s} {'rules':>6s} {'AI':>6s} {'AI-first':>9s} {'rules-first':>12s}")
for name, cs in by.items():
    n = len(cs)
    for f in FIELDS + ["record"]:
        def acc(pick):
            if f == "record":
                return sum(pick(c) == c[1] for c in cs) / n
            return sum(pick(c)[f] == c[1][f] for c in cs) / n
        print(f"{name[:32]:32s} {f:8s} {acc(lambda c: c[2]):6.0%} {acc(lambda c: c[3]) if f != 'record' else float('nan'):6.0%} "
              f"{acc(lambda c: c[4]):9.0%} {acc(lambda c: rules_first(c[2], c[3])):12.0%}")
    print()
