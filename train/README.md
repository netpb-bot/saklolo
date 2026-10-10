# Training Saklolo's on-device AI

The app reads a report with **EmbeddingGemma 300M** (a meaning vector, on the phone) plus small **classifier heads**
trained here (`app/src/main/assets/heads.json`, ~110 KB): hazard, trapped, each condition, each vulnerable group.
Nothing here generates text; every output is a label from a closed list. Rules (`Dictionary.kt`) decide first, and the
AI only adds labels above per-label bars (`Llm.bars`).

## Setup (once)
```
pip install ai-edge-litert sentencepiece numpy
```
Copy the Gemma-licensed model files into `train/model/` (git-ignored; never commit them):
`embeddinggemma-300M_seq256_mixed-precision.tflite` and `sentencepiece.model`
(e.g. `adb pull /sdcard/Download/<file> train/model/`).

`python embed.py` checks that PC vectors match the phone's (needs `model/phone_vectors.bin`, the app's saved example
vectors: `adb exec-out "run-as ph.saklolo cat files/examples-*.bin" > model/phone_vectors.bin`). Measured
2026-10-10: cosine min 0.998, mean 0.999 (CPU rounding differences only).

## Retrain
```
python make_training.py    # writes reports.jsonl: ~3,000 generated reports + ~1,300 single clauses, labeled
python train_heads.py      # embeds (cached in model/vectors.npz), trains, writes heads.json, scores frozen sets
```
Then build the app and run the Eval screen on a phone; save the log and compare policies offline:
```
adb logcat -d -s SakloloEval:* | sed 's/^.*SakloloEval: //' > model/eval_log.txt
python score_log.py        # per field: rules vs AI vs combined, per set
python simulate.py         # tries confidence bars for "rules first, AI fills gaps"
```

## Data and honesty rules
- **Training data** is generated from phrase banks in `make_training.py` (EN / FIL / Taglish, then text-speak, typos,
  caps and punctuation loss). Labels come with the parts, so they're exact. Add wording there, not in the app.
- **Frozen test sets** (never used for training, never used to edit rules):
  - `eval/real.json`: 58 #RescuePH tweets from CrisisLexT26 (2012 Habagat floods and other Philippine events),
    anonymized (names → `[name]`, street addresses → `[address]`, phone numbers → `[number]`, handles removed),
    labeled from the text only. Cite: Olteanu, Vieweg & Castillo, *What to Expect When the Unexpected Happens*,
    CSCW 2015. Source: github.com/sajao/CrisisLex (CrisisLexT26-v1.0.zip).
  - `eval/hard.json`: 29 reports written apart from the training data: wording without keywords, negation,
    corrections, collapse and landslide, breathing cases that must never trigger CPR.
- **Known contamination**, so the numbers are read correctly: the bars in `Llm.bars` were chosen by `simulate.py`
  over these sets, and the "my wife", "nanay" and "crack in the wall" training phrases were added after seeing test
  errors. Fresh reports from new people are the only fully independent check: add them to `eval/extract.json` with
  `"by": "others"` before changing anything.

## Results (Samsung S24 Ultra, 2026-10-10, whole record correct)
| Set | Rules only | Rules + AI |
|---|---|---|
| Hard cases (29) | 28% | 41% |
| Real #RescuePH tweets (58) | 52% | 55% |
| Reports by others (10; rules were tuned on these) | 100% | 100% |
| Tuning set (10; rules were tuned on these) | 100% | 100% |

Heads v3 (with the "my wife" / "nanay" / "crack" fixes). v2 measured 45% / 53% / 80% / 90% on the same sets.

No wrong NOT_BREATHING / UNCONSCIOUS / HEAVY_BLEEDING in any final record.

**v4 tried, not shipped (2026-10-10):** +948 Gemini-written reports (`gen_llm.py` → `llm_reports.jsonl`, aimed at
keyword-less hazards, signs-only breathing, negation, trapped without "trapped", self-corrections). AI alone on hard:
hazard 79→90%, trapped 59→76%, cond 72→76%, vuln 86→93%. But whole record at the app's bars (PC simulation, `simulate.py`)
stayed 41% hard, 55→57% real, and a new UNCONSCIOUS appeared on "lips turning blue, chest isn't moving" (want
NOT_BREATHING). Kept v3; v4 is in `model/heads_v4.json`. Most remaining hard misses are rule-only fields (people, floor, spot).
