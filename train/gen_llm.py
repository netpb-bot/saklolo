"""Writes train/llm_reports.jsonl: labeled reports written by Gemini, for wording the phrase banks don't cover.

    $env:GEMINI_API_KEY="..."; python gen_llm.py

Training time only; the app never calls this. The LLM never sees the frozen test sets (eval/*.json). Every row is
checked against the closed label lists and dropped if anything is off.
"""
import json
import os
import re
import urllib.request
from pathlib import Path

from train_heads import COND, HAZARD, TRAPPED, VULN

OUT = Path(__file__).parent / "llm_reports.jsonl"
URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"


def ask(prompt):
    req = urllib.request.Request(URL, json.dumps({"contents": [{"parts": [{"text": prompt}]}]}).encode(),
                                 {"Content-Type": "application/json", "x-goog-api-key": os.environ["GEMINI_API_KEY"]})
    with urllib.request.urlopen(req, timeout=300) as r:
        c = json.load(r).get("candidates", [{}])[0]
    return "".join(p.get("text", "") for p in c.get("content", {}).get("parts", []))  # empty when blocked or cut off


FOCUS = [
    "a building/house/wall/ceiling/stairs coming down WITHOUT the words collapse, gumuho, lindol or earthquake (gave way, fell, nagiba, bumigay, beam fell)",
    "LANDSLIDE vs COLLAPSE: earth, mud, rocks, hill or mountain coming down (LANDSLIDE) next to buildings falling from shaking (COLLAPSE)",
    "someone NOT_BREATHING or UNCONSCIOUS described only by signs (blue lips, chest not moving, eyes rolled back, limp, not answering). "
    "Someone who breathes with difficulty is INJURED, never NOT_BREATHING",
    "negation and reassurance: no one hurt, nobody bleeding, we got out, water stopped rising, mentioned to say it is NOT the case",
    "trapped without the word trapped: can't stand up, exit blocked, door jammed, nowhere to go, surrounded by water",
    "self-corrections: the caller says one thing then takes it back (ay hindi, pala, wait, sorry, i mean, ay mali, teka). Label the "
    "CORRECTED fact, except a retracted NOT_BREATHING/UNCONSCIOUS/HEAVY_BLEEDING stays labeled (safer), and trapped becomes NO only if clearly said",
]
PROMPT = """Write {n} different emergency reports a panicked person in the Philippines might type or say to an offline 911 app.
Focus: {focus}
Mix English, Filipino and Taglish; some short, some rambling; some with text-speak (ung, kmi, d2, pls), typos, no punctuation, or ALL CAPS.
Vary people (lola, baby, buntis, PWD, kids, neighbors, coworkers), places and wording. Don't reuse sentence patterns.

Label each one from what the text says, using ONLY these values:
hazard: one of {hazard}  (NONE if not stated)
trapped: one of {trapped}  (UNKNOWN if not stated)
cond: list from {cond}  (HEAVY_BLEEDING only for serious bleeding; small cuts are INJURED; WATER_RISING only if still rising)
vuln: list from {vuln}  (INFANT under 1, CHILD, ELDERLY, PREGNANT, PWD; a plain wife/mother/nanay is none)

Output JSON lines only, one per report: {{"text": "...", "hazard": "...", "trapped": "...", "cond": [...], "vuln": [...]}}"""


def valid(r):
    return (isinstance(r.get("text"), str) and r["text"].strip() and r.get("hazard") in HAZARD and r.get("trapped") in TRAPPED
            and isinstance(r.get("cond"), list) and set(r["cond"]) <= set(COND)
            and isinstance(r.get("vuln"), list) and set(r["vuln"]) <= set(VULN))


def main(rounds=4, n=50):
    seen = {json.loads(l)["text"] for l in OUT.read_text(encoding="utf-8").splitlines()} if OUT.exists() else set()
    with OUT.open("a", encoding="utf-8") as f:
        for i in range(rounds):
            for focus in FOCUS:
                text = ask(PROMPT.format(n=n, focus=focus, hazard=HAZARD, trapped=TRAPPED, cond=COND, vuln=VULN))
                kept = 0
                for line in text.splitlines():
                    if not re.match(r"\s*\{", line):
                        continue
                    try:
                        r = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    if valid(r) and r["text"] not in seen:
                        seen.add(r["text"])
                        f.write(json.dumps({k: r[k] for k in ("text", "hazard", "trapped", "cond", "vuln")}, ensure_ascii=False) + "\n")
                        kept += 1
                print(f"round {i} kept {kept:3d}  {focus[:60]}")


if __name__ == "__main__":
    main()
