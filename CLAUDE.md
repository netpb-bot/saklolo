# Saklolo

This is an offline 911 dispatcher for Android, built in a 10-hour solo hackathon. A local model turns a panicked spoken report (English, Filipino or Taglish; Bisaya dropped by decision, 2026-10-10) into closed-list facts. The app reads deterministic survival protocols aloud and broadcasts a 22-byte BLE beacon. Rescuer phones rank the beacons, read a briefing aloud, follow a hot/cold meter, and can ring the victim's phone.

- [PRD.md](PRD.md) is the source of truth. [SPEC.md](SPEC.md) is the build checklist (slices S0–S14). **If they disagree, the PRD wins.**
- [DESIGN_PROMPT.md](DESIGN_PROMPT.md) and `Android native mobile app design/` hold the UI mocks (HTML). Match them for screens.
- Work slice by slice in SPEC order. Each slice must end with something visible on a real phone. Tick the slice's checkbox in SPEC.md when it's done.

## Stack
- Kotlin + Jetpack Compose, Android 8+ (Android 12+ needs BLUETOOTH_ADVERTISE/BLUETOOTH_SCAN). No backend, and nothing touches the internet at runtime.
- Model: **path A** is Gemma 3n E2B (audio) through LiteRT-LM. **Path B** is Gemma 3 1B int4 through MediaPipe `tasks-genai` plus offline SpeechRecognizer. ~~Decision: path B~~ **Decision (2026-10-10): EmbeddingGemma 300M** (`embeddinggemma-300M_seq256_mixed-precision.tflite` + `sentencepiece.model`, LiteRT 2.2.0 CPU, ~180 MB, runs on 3–4 GB phones). Gemma 3 1B failed the Eval set on the S24. The model never generates text. Reports: small classifier heads trained on labeled examples (`train/`, output `assets/heads.json`) read hazard, trapped, conditions and vulnerable people; rules decide first and the AI only adds above per-label bars (`Llm.bars`). Answers and FAQs: nearest example phrase from `protocols.json`, or nothing. Copy files into `/sdcard/Android/data/ph.saklolo/files/` with `run-as ph.saklolo` so the app owns them.
- The model file is sideloaded with adb and never bundled in the APK.
- Speech input: **offline Whisper small int8 via sherpa-onnx** (`app/libs/sherpa-onnx-1.13.8.aar`; `small-encoder.int8.onnx`, `small-decoder.int8.onnx`, `small-tokens.txt` from the `asr-models` release `sherpa-onnx-whisper-small`, sideloaded next to EmbeddingGemma). Language is auto-detected, so English, Filipino and Taglish work with no signal. Without these files it falls back to Android's SpeechRecognizer, which on the S24 has English packs only (2026-10-10).
- Hands-free mic: `silero_vad.onnx` (sherpa-onnx `asr-models` release, sideloaded next to Whisper) tells speech from noise. After SOS the mic waits with no time limit and reopens by itself, ignores the app's own TTS and the ring (`Voice.ownSound`), drops Whisper's noise hallucinations (`Dictionary.noise`), keeps the screen on while listening, and in survival mode (battery > `STANDBY_MIN_PCT`) speech brings the conversation back. Without the VAD file it uses energy endpointing and survival mode doesn't listen.

## Commands
- `./gradlew test`: JVM tests (engine, triage, "getting worse" rule, follow-ups, codec, dedupe, validator)
- `./gradlew installDebug` / `assembleRelease`: build and install
- On-device evals: the Eval screen runs `app/src/main/assets/eval/`. `real.json` (anonymized #RescuePH tweets) and `hard.json` are frozen: never tune rules or training data on them.
- Retrain the AI: see [train/README.md](train/README.md) (`make_training.py` → `train_heads.py` → `simulate.py`), then re-run the Eval screen.

## Files (keep to this set; don't add layers)
| File | Role |
|---|---|
| `Protocol.kt` | Pure engine: life-threat protocol first, then the hazard protocol; entry step from facts. No Android imports |
| `Triage.kt` | Record, validator, triage rule, "getting worse" rule, missing-fact follow-ups, 22-byte codec. No Android imports |
| `Dictionary.kt` | Keyword baseline and fallback when the model can't run |
| `Llm.kt` | One loaded model, three tasks: `extract()`, `answer()`, `matchFaq()` |
| `Voice.kt` | Speech input and TTS |
| `Beacon.kt` | Foreground service (`connectedDevice`): duty-cycled advertise, service-UUID-filtered scan, ACK/RESC/RING |
| `Rescuer.kt` | Ranking, GPS bearing, smoothed RSSI (moving average of ~5 samples, 4 bands), briefing template |
| `Alert.kt` | Chirp and flashlight strobe for 30 s |
| `MainActivity.kt` | All screens: home, protocol, survival mode, dispatch board, Eval, demo overlay |
| `assets/protocols.json` | 4 protocols, `safer` branch on every question node, follow-up questions, FAQs. Every string has `en` and `fil` |

## Safety rules (non-negotiable)
- **The AI only picks from closed lists. It never writes text the user sees.** Medical and survival text, follow-up questions and myth answers come only from `protocols.json`.
- Every model output goes through the validator. Anything invalid or missing falls back to the tiles or buttons and the dictionary.
- **UNSURE always takes the node's `safer` branch.** An unclear answer to "Can you get out?" counts as trapped.
- `matchFaq()` only sees the **current protocol's** FAQs and returns an id or `NONE`. A wrong answer is worse than `NONE`.
- Triage colour and ranking are **rules, not AI** (see PRD D2 and D9).
- **Never wait on the model.** The beacon starts on SOS confirm, and life-threat protocols (CPR, bleeding) go on top immediately, above any follow-up question. The model loads at app start, not when the mic is tapped.
- At most 5 follow-up questions (`MAX_FOLLOWUPS`). They are asked after the first safety tip, and they still wait below CPR or bleeding control. Wording and tiles follow the hazard (`by_hazard` in `protocols.json`). Place is asked for COLLAPSE, LANDSLIDE (spot) and FLOOD (floor or roof). A step with a `fact` (the "Is the water still rising?" step) also reports its YES or UNSURE to rescuers.
- "Call 911" appears on every screen. Nothing is broadcast before SOS or "I'm safe". There are no names in the beacon.
- The app is labelled "Demo, not medically reviewed".

## Invariants to test
- **Codec:** exactly 22 bytes, laid out as in PRD §8. lat/lon are ×1e5 int32, with `Int.MIN_VALUE` meaning no fix. The kind byte is code 4b, triage 2b, trapped 1b, worse 1b. The place byte is floor 4b (15 = roof), spot 4b. No device name or TX power in the advertisement. `(id, revision)` is the dedupe key, and a newer revision replaces an older one.
- **"Getting worse"** is decided only on the victim phone. Unknown → known is **not** worse. A new condition, a higher headcount, or not-trapped → trapped **is** worse.
- A bare SOS with no facts is YELLOW. CPR starts with rescue breaths when the hazard is FLOOD.
- ACK, RESC and RING carry the **victim's** record id.

## Language
English is the default everywhere: screens, TTS, briefings, questions. The EN/FIL toggle on every screen switches all of them, and the choice is remembered. Input understanding does not depend on this setting.

## Out of scope
Don't build any of these: maps, cloud, auth or onboarding, an LLM on the rescuer phone, AI-written medical text or triage, Bisaya instruction text, transports other than BLE, automatic 911 or SMS, packet signing, iOS. Full list in SPEC "Not building".
