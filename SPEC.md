# SPEC: Saklolo
*The offline 911 dispatcher for when the network goes down. Derived from [PRD.md](PRD.md). If the two disagree, the PRD wins.*

## One-liner
When a typhoon or an earthquake takes down the network, trapped Filipinos can't call for help and rescuers search blind. Saklolo puts the dispatcher on the phone. A local AI model listens to a panicked report in whatever language comes out (English, Filipino, Taglish or Bisaya), asks for what's missing, and guides survival and first aid in English by default, with Filipino one tap away. It then compresses the emergency (hazard, headcount, injuries, **which floor and which room**) into a 22-byte Bluetooth beacon that lasts up to 72 hours. Rescuers can rank the beacons, hear a spoken briefing, follow a hot/cold meter, and make the victim's phone ring to pinpoint it. Every safe phone becomes a rescuer.

## Problem (make the judges feel it)
- **Who:** a family trapped inside a collapsed house after an earthquake, or on a roof as floodwater or storm surge rises. On the receiving end, neighbours, a barangay tanod, a BFP USAR team or a Coast Guard crew, with no network and many buildings or roofs to check.
- **Pain:**
  - The network fails exactly when it's needed. After Odette, 348 areas lost service, and Siargao had problems for more than 50 days. After earthquakes, even surviving towers jam because everyone calls at once.
  - Yolanda killed more than 6,300 people, mostly through storm surge. The MMEIRS study estimates about 34,000 deaths if an M7.2 hits Metro Manila on the West Valley Fault. Bohol (2013), Cebu and Davao Oriental (2025) were all in Cebuano-speaking regions. *(Verify figures and add sources before they go on a slide.)*
  - Survival of trapped people falls sharply after the first 72 hours, and phone batteries die long before that.
- **Today they:**
  - Shout until they lose their voice, bang on pipes, wave cloth, or wait.
  - Follow dangerous myths: light a lighter to see (gas leaks), climb into ceilings with no way out, swim through currents, treat storm surge as "just a big wave".
  - Rescuers go building to building without knowing who is RED, how many people are inside, which floor they're on, or who is already safe.
  - First-aid apps give English instructions through buttons, and mesh apps carry typed text. Neither does the dispatcher's job.

## Core flow (the demo path, earthquake scenario)
1. **SOS:** the victim holds SOS. After a 3-second cancel window, the beacon starts with the location (rev 1, YELLOW) and survival mode turns on.
2. **Understand:** the victim says *"Tulong! Gumuho ang bahay, naipit kami sa ikalawang palapag, sa may banyo. Tatlo kami, ay hindi, apat pala, kasama si lola. Hindi ko maigalaw ang binti ko, at ang daming dugo sa ulo ng anak ko!"* ("Help! The house collapsed, we're trapped on the second floor, by the bathroom. Three of us, no wait, four, including grandma. I can't move my leg, and there's so much blood on my child's head!")
   - **Gemma:** `COLLAPSE, floor=2, spot=bathroom, people=4, elderly, child, pinned, heavy_bleeding`. The triage rule makes it RED.
   - **Dictionary** (demo overlay only): 3 people, no injuries.
   - The app replies in English, the default, even though the report was in Taglish.
3. **Ask:** only if a required fact is still missing, at most 2 questions, and never above a life-threat protocol.
4. **Instruct:** the bleeding protocol starts immediately, followed by the collapse protocol. A myth question ("lighter?") gets an approved answer.
5. **Find:** on the rescuer phone, RED is at the top and is read aloud with the floor and room. There's no GPS indoors, so the rescuer follows the hot/cold meter, then taps **Ring phone** and the victim's phone chirps and strobes.
6. **Wow moment:** the rescuer taps "On my way" and the victim's phone, still in airplane mode, shows **"Help is on the way"**.

## Slices (each one demoable, each one <45 min)
Grouped by the PRD's 10-hour build plan (§10). Each slice ends with something visible on a real phone.

**Hour 0–1: go/no-go**
- [ ] S0a: Gemma 3n E2B in Google's AI Edge Gallery on the demo phone: speak 5 reports (English, Filipino and Taglish). **Understands them → path A (audio into LiteRT-LM). Doesn't → path B.**
- [ ] S0b: Gemma 3 1B (int4) through MediaPipe `tasks-genai` on 10 mixed English/Filipino/Taglish and 3 Cebuano text reports, scored. **Below 6/10 → Gemma 3n E2B on text. That also fails → the dictionary drives the app and the AI is pitched as roadmap. Cebuano below 2/3 → no Bisaya.**
- [ ] S0c: Offline English and Filipino TTS, and (path B only) offline English and `fil-PH` SpeechRecognizer, all in airplane mode. **No Filipino TTS → Filipino mode is text only. No `fil-PH` recogniser → English recogniser plus a text box for Taglish.**
- [ ] S0d: BLE advertise and scan "hello" between the two phones in airplane mode.
- [ ] S0e: Loud tone plus flashlight strobe on the victim phone. **Torch fails → sound only.**

**Hour 1–2: D4 instructions**
- [x] S1: Compose skeleton on both phones: SOS home, an "I'm safe" button, "Call 911" on every screen, an EN/FIL toggle (English by default, choice remembered), model loading at app start.
- [x] S2: `Protocol.kt` plus `protocols.json` with the 4 protocols (not breathing, heavy bleeding, trapped in collapse, on a roof in rising water) in English (default) and Filipino. JVM tests: `UNSURE` always takes `safer`; the life-threat protocol comes before the hazard protocol; CPR starts at rescue breaths when the hazard is FLOOD.
- [x] S3: Protocol screen with TTS; each step shows in under 300 ms.

**Hour 2–4: D1, D2, D3, D5 dispatcher loop**
- [x] S4: `Triage.kt` record, validator, triage rule, "getting worse" rule and missing-fact follow-ups; `Dictionary.kt` baseline. JVM tests: triage rule (a bare SOS is YELLOW), unknown → known is not worse, follow-up selection (place is required only for COLLAPSE), validator rejects unknown IDs.
- [ ] S5: Mic → `extract()` → validator → status card (with floor and room). Dictionary result logged for the demo overlay. Tiles are always visible: Collapse, Flood, Not breathing, Bleeding, Trapped. The protocol and beacon never wait for the model.
- [ ] S6: Follow-up loop (at most 2 questions, below any life-threat protocol) and `answer()` for YES/NO/UNSURE, showing "I understood: NO". Yes/no buttons always shown.

**Hour 4–5.5: D7 beacon**
- [x] S7: Binary codec (22 bytes: revision, worse bit, place nibbles, battery, no-fix sentinel). JVM round-trip and `(id, revision)` dedupe tests. Hex bytes shown in the demo overlay.
- [x] S8: Beacon foreground service (`connectedDevice`) with no device name or TX power; duty cycle by battery (continuous low-power above 30%, then 10 s on / 50 s off); rescuer scans with a service-UUID filter. Received in under 10 s with both phones in airplane mode and the victim's screen off.

**Hour 5.5–6.5: D9 dispatch board**
- [x] S9: Ranked list: RED > YELLOW > GREEN, vulnerable higher, then oldest report first. Rows show headcount, floor and room, flags, battery, and "last heard N min ago"; a silent record keeps its last location. A separate "Safe" count. Detail view: spoken briefing from a template (English by default), GPS arrow and distance, RSSI hot/cold meter (moving average over ~5 samples, shown as 4 bands).

**Hour 6.5–7.5: D8, D10, D11, D12**
- [ ] S10: "On my way" → ACK → victim shows "Help is on the way" and switches to low-latency advertising. "Ring phone" → RING → victim phone chirps and strobes for 30 s. "Rescued" → RESC → the beacon stops and the record leaves the board. Re-speaking with a new condition sets the worse bit → "getting worse" flag. "I'm safe" → SAFE record → the phone opens the dispatch board.

**Hour 7.5–8.5: D6, D7 survival mode, D13**
- [x] S11: ~16 approved myth answers (**minimum: collapse and bleeding, 8 answers**), `matchFaq()` returns an id or `NONE`, Ask button on the protocol screen.
- [ ] S12: Survival-mode screen: black, tap to wake, showing "Beacon will last ~N more hours". Drain per hour measured (`BatteryManager` start/end over a timed run, ACK/RING scan windows included) and projected against 72 h. Eval screen runs all sets and shows dictionary score, model score and median latency. Demo overlay toggle (long-press the title).

**Hour 8.5–9: buffer or stretch**
- [ ] S13 (P1-1): relay with a third phone. Otherwise fix whatever is broken.

**Hour 9–10: ship**
- [ ] S14: Rehearse the demo three times in the real hall, record the backup video (earthquake plus the flood scenario), submit to Devpost.

### Demo overlay and demo menu
The **demo overlay** (long-press the title) is a projection-friendly layer that a victim never sees. It shows:
- the on-device model badge
- the last AI call: input, closed-list output, validator result
- the dictionary vs. Gemma result for the last report
- the 22 bytes as coloured cells

The **demo menu**, in the same place, injects the scripted report, the myth question and the update as if spoken. They still go through the real model, validator and dictionary, so it's the same code path with only the mic skipped. It can also set a fixed GPS location. Voice in a noisy hall is the biggest demo risk.

## Acceptance checks (from PRD §3)
| Check | Target | How |
|---|---|---|
| Extraction | ≥ 8/10 correct incl. hazard and place; model beats dictionary by ≥ 3; ≥ 3 reports written by others | Eval screen, on device |
| Bisaya (if in) | ≥ 2/3 Cebuano reports correct | Eval screen |
| Unclear answers | 5/5 take the safer branch; 0 clear "no" read as "yes" | Eval screen |
| Myth-buster | ≥ 8/10 matched, **0 wrong** (`NONE` is fine) | Eval screen |
| Latency | Extraction < 5 s; `answer()` and `matchFaq()` < 2 s | Eval screen |
| Beacon | < 10 s, both phones in airplane mode, victim screen off | Stopwatch |
| Acknowledgement and ring | Victim shows "help is coming", and rings and strobes, within 10 s (continuous beacon) | Stopwatch |
| Dispatch board | Ranks, reads aloud with floor and room, hot/cold changes | Walk test in the hall |
| Language | Fresh install is English everywhere (screens, voice, briefing); the EN/FIL toggle switches all of it; a Taglish report works in either mode | Manual |
| Battery | Survival-mode drain per hour measured; projected beacon life shown (target ≥ 72 h; report the real number) | S12 |
| JVM tests | Engine (safer branch, life-threat order, entry step), triage rule, "getting worse", follow-up selection, codec round-trip and dedupe, validator | `./gradlew test` |

The eval sets live in `app/src/main/assets/eval/` so they can be re-run on device after any prompt change.

## Not building (explicitly out of scope)
- Animal bites, leptospirosis and other everyday emergencies
- Auth, user accounts, settings pages, onboarding
- Cloud anything: dashboard, sync, analytics. Nothing at runtime touches the internet.
- Maps and map tiles (arrow, distance, floor and room are enough)
- Bisaya instruction text (Bisaya *input* only, and only if the hour-0 test passes); iOS
- Transports other than BLE advertising (Nearby Connections, Wi-Fi mesh, LoRa)
- An LLM on the rescuer phone (the briefing is a template)
- AI-written medical text, AI triage or ranking, photo diagnosis, medicine doses
- A landslide protocol (LANDSLIDE uses the collapse protocol)
- Automatic 911 or automatic SMS; the SMS gateway unless the buffer hour is free
- Packet signing and encryption (risk stated on a slide)
- Medical review (the app is labelled "demo, not medically reviewed")

## Stack
- **Platform:** Android, Kotlin + Jetpack Compose; no backend, everything on device
- **Model:** ~~path A Gemma 3n E2B / path B Gemma 3 1B~~ (1B failed the Eval set on the S24). **EmbeddingGemma 300M** (180 MB, LiteRT, CPU, runs on 3–4 GB phones) + small classifier heads trained on ~4,400 labeled English/Filipino/Taglish reports (`train/`). Never generates text; rules first, AI adds what the word lists miss.
- **Content:** `assets/protocols.json` (4 protocols, safer branches, entry steps, follow-up questions, FAQs; every string in English, the default, and Filipino)
- **Beacon:** a 22-byte BLE service-data record
- **Install:** APK and model sideloaded with adb

| File | Role |
|---|---|
| `Protocol.kt` | Pure protocol engine: life-threat first, entry step from facts (JVM-testable) |
| `Triage.kt` | Record, validator, triage rule, "getting worse", follow-ups, binary codec |
| `Dictionary.kt` | Keyword baseline and fallback |
| `Llm.kt` | One loaded model, three closed-list tasks: `extract()`, `answer()`, `matchFaq()` |
| `Voice.kt` | Speech input and TTS |
| `Beacon.kt` | Foreground service, duty-cycled BLE advertise, filtered scan, ACK/RESC/RING |
| `Rescuer.kt` | Ranking, GPS bearing, smoothed RSSI, spoken briefing |
| `Alert.kt` | Ring tone and flashlight strobe |
| `MainActivity.kt` | Screens, including survival mode, the demo overlay and Eval |

**Hardware:** 2 Android phones (3 to show a relay). The victim phone needs about 4 GB of RAM for path B or about 6–8 GB for path A, plus a flashlight. Write down the exact models once chosen.

## Judging criteria → how we hit them
| Criterion | Weight | Our answer |
|---|---|---|
| Problem & Usefulness | 25% | One target user: the person trapped and waiting after a flood or an earthquake, when the network is gone or jammed. It covers the golden 72 hours, from the first report to being pinpointed in rubble or on a dark roof. Philippine moments: Yolanda, Ondoy, Odette, Bohol, the Big One. Bayanihan mode turns every safe user into a rescuer, which also answers who has the app. The beacon runs on any BLE phone, so the AI never decides who can call for help. |
| Local AI Implementation | 25% | Local inference *is* the dispatcher: without it there's only a form with buttons, and cloud AI is impossible when the network is down. The model compresses a panicked spoken report into 22 bytes, including **which floor and which room**, a fact GPS can't give and keyword matching handles badly. It asks for what's missing, understands unclear answers and matches myth questions. It understands English, Filipino and Taglish and answers in English by default. Closed lists let it understand Bisaya without Bisaya medical text. The dictionary runs side by side, and the Eval screen shows the measured gap. |
| Technical Execution | 20% | Everything runs live on real phones in airplane mode: a duty-cycled beacon in a foreground service, with drain measured and projected against 72 hours. JVM tests cover the engine, triage rule, "getting worse" rule, codec and validator. Eval scores are measured on the device. Every AI path has a deterministic fallback, and the beacon never waits for the AI. |
| Innovation | 15% | An offline dispatcher with closed-list safety, and a local model used as a compressor for a channel with almost no bandwidth. Mesh apps carry text someone typed; Saklolo carries triage the AI understood. Ring phone lets rescuers make a phone under rubble ring and flash. The victim phone decides "getting worse" because only it knows what was unknown. The record carries the phone's battery, so rescuers know which beacon will die first. |
| Product & Demo Quality | 15% | The earthquake scenario fits the indoor stage exactly: no GPS, short range. Five beats: the AI vs. dictionary moment, bleeding control that doesn't wait, the spoken briefing with floor and room, the hot/cold walk with the phone ringing at the end, and "Help is on the way". The demo overlay and menu cover a noisy hall; a backup video shows the flood scenario. |

## Demo script (2 min)
1. **Hook (15 s):** "When the Big One hits Metro Manila, or a typhoon like Yolanda hits Leyte, the network is the first thing to go. No one answers 911." Both phones go into airplane mode with Bluetooth on, in view of the audience.
2. **Understand and ask (35 s):** SOS → the collapse report → demo overlay: the dictionary says 3 people and no injuries; Gemma says COLLAPSE, 2nd floor, bathroom, 4, elderly, child, pinned, heavy bleeding → RED. "That's why it has to be AI, and why it has to be local." The phone answers in English, its default. Optional: one line in Cebuano.
3. **Instructions (25 s):** Bleeding control starts immediately. *"Pwede bang sindihan ang lighter para makakita?"* (Can I light a lighter to see?) → approved answer **No**, there may be gas. Show the 22 bytes.
4. **Find (25 s):** RED at the top, read aloud with floor and room. With no GPS, the rescuer walks the stage following the hot/cold meter → **Ring phone** → the victim's phone chirps and strobes.
5. **Payoff and close (20 s):** "On my way" → **"Help is on the way."** "The beacon lasts [measured] hours on one charge. Every safe phone becomes a rescuer: bayanihan, with no towers."

Ready for Q&A: the flood scenario (backup video), the Eval screen, survival mode, and an invitation for a judge to speak their own report.

## Q&A prep
| Question | Answer |
|---|---|
| "Couldn't a keyword list do this?" | Show the Eval screen. Hand-written rules only cover phrasings someone already saw: each fresh batch of real reports scored 0–1/5 until rules were patched. On reports nobody tuned for (hard set, 29), rules + trained AI went from 28% to 45% fully correct, with zero wrong life-threat labels (S24, 2026-10-10). On 58 real #RescuePH tweets (CrisisLexT26, anonymized) they tie (52% → 53%). |
| "BLE only reaches ~30 m." | Collapse search happens at short range: rescuers are already on the building, and the problem is which void. Ring phone adds sound. On a flood, boats pass within range, and bayanihan phones and relays extend reach. The same bytes fit LoRa next. |
| "Who has this app installed when disaster hits?" | Drills in schools and barangays, the PAGASA Signal No. 1 prompt, and bayanihan: every safe user is also a rescuer. The beacon works even without the model. |
| "Will the battery last?" | Show the measured drain and projected hours. The duty cycle stretches as the battery drops, and the record carries the battery level so rescuers prioritise phones that are about to die. |
| "What if the AI is wrong?" | It can only pick from closed lists; the validator checks it; triage is a rule; buttons always work; unsure goes to the safer branch; myth-buster target is 0 wrong. |
| "Why not Bisaya instructions?" | Understanding Bisaya is nearly free with closed lists; writing reviewed Bisaya medical text isn't. It's first on the roadmap. |
| "Does it only work in Filipino?" | No. Every screen and spoken instruction is in English by default, with Filipino one tap away. Input doesn't depend on the setting: the model understands English, Filipino and Taglish either way. |
| "Can someone spoof beacons?" | Yes, today. Signed records are on the roadmap. |

## Demo-day checklist
- [ ] Both phones charged, model pushed with adb, APK is the release build
- [ ] Airplane mode **on**, Bluetooth **on**, location permission granted, scan running before going on stage
- [ ] Model already loaded (app opened before going on stage)
- [ ] Victim phone volume up, torch tested, Do Not Disturb off
- [ ] Demo overlay and demo menu tested (injected sentences) in case the hall is noisy
- [ ] Hot/cold walk and Ring phone rehearsed in the real hall
- [ ] App language on English (the default) on both phones
- [ ] Eval scores and battery projection on a slide
- [ ] Backup video queued (earthquake plus flood)
- [ ] The "Demo, not medically reviewed" label is visible
