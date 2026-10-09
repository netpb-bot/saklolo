# PRD: Saklolo
*The offline 911 dispatcher for when the network goes down*
Owner: Kenneth (solo) · Build: 10 hours · Android (Kotlin, Compose) · v0.3 hackathon MVP

> When a typhoon or an earthquake takes down the network, Saklolo still answers, and makes sure you're found.

## 1. Problem
In the Philippines, the people who most need help after a disaster are the ones who can't move and can't call: families on roofs as the water rises, and people trapped inside collapsed buildings. This is exactly when the network fails. Towers and power go down after typhoons, and after earthquakes even surviving towers jam because everyone calls at once. Cloud services, including cloud AI and the 911 dispatcher, fail with them.

**Floods and storm surge**
- **Yolanda/Haiyan (2013):** more than 6,300 dead, mostly from storm surge. Tacloban was cut off for days, and many residents didn't understand what "storm surge" meant *[source needed]*.
- **Ondoy (2009):** Metro Manila families waited on rooftops for rescue *[source needed]*.
- **Odette/Rai (2021):** 348 areas lost network service, and Siargao still had problems more than 50 days later ([ReliefWeb](https://reliefweb.int/report/philippines/philippines-super-typhoon-rai-odette-humanitarian-needs-and-priorities-dec-2021), [ETC](https://etcluster.org/emergency/philippines-super-typhoon-raiodette)).

**Earthquakes and collapse**
- **Bohol (2013):** M7.2 *[source needed]*.
- **Cebu and Davao Oriental (2025):** strong earthquakes hit Cebuano-speaking regions *[verify figures before use]*.
- **The "Big One":** the MMEIRS study estimated about 34,000 deaths in Metro Manila from an M7.2 on the West Valley Fault *[source needed]*.
- **Who gets people out:** in collapse disasters, neighbours rescue many of the people pulled out alive, in the first hours before formal teams arrive *[citation needed]*.

**Isolation even on a normal day:** 7,063 GIDA barangays are home to about 25 million people ([Globe/DICT](https://www.globe.com.ph/about-us/newsroom/corporate/gida-blueprint-proposal)).

**The golden 72 hours.** Survival of trapped people falls sharply after the first three days. For that whole window, three things are true:
- **Victims** can't call, don't know what to do, and watch their battery drain.
- **Rescuers** search blind. They don't know which roof or building, which floor, how many people, who is hurt worst, or who is already safe.
- **Neighbours** who are safe and willing to help have no way to know who needs them.

**Existing solutions:**
- **OASIS and MOASIS** give offline first aid in English, through buttons.
- **BLE mesh apps (Bridgefy, Meshtastic)** carry text someone typed, so the rescuer still has to read every message.
- **Nothing does the dispatcher's job offline:** understanding the caller, asking what's missing, prioritising, and guiding the rescuer to the exact spot.

## 2. Product
**Saklolo is a 911 dispatcher that runs entirely on the phone,** built for the trapped-and-waiting window after a flood or an earthquake.

| Dispatcher step | Saklolo | Local AI? |
|---|---|---|
| 1. Understand the caller | Speech in English, Filipino or Taglish (and Bisaya if the hour-0 test passes) → structured facts: hazard, how many, trapped, **which floor and which part of the building**, who is vulnerable, injuries and danger | **Yes**: extraction |
| 2. Ask for what's missing | Fixed, approved follow-up questions for only the missing facts | **Yes**: understands the answers |
| 3. Instructions until help arrives | Deterministic survival and first-aid protocols read aloud in English by default, or Filipino with one tap; unclear answers take the safer branch; survival-myth questions get approved answers only | **Yes**: classifies answers, matches questions |
| 4. Send help | The facts are compressed into a **22-byte record** and broadcast over low-power Bluetooth for up to 72 hours | Carries the AI's output |
| 5. Guide the rescuer to you | The rescuer can make the victim's phone **ring and strobe its flashlight** to pinpoint it in rubble or on a dark roof | No |
| 6. "Help is coming" | The rescuer's acknowledgement travels back over the same channel | No |

**Every safe phone becomes a rescuer (bayanihan mode).** One tap on "I'm safe" tells everyone you're safe and turns your phone into a dispatch board. Neighbours, barangay tanods, BFP and Coast Guard crews all use the same app.

**Why it has to be local AI:**
- **No network, no cloud.** A cloud model is unavailable exactly when the app is needed, whether the network is destroyed or jammed.
- **The AI is the compressor.** A spoken emergency is about 200 words, and a Bluetooth beacon carries about 22 bytes. Without the model, a trapped and panicking person would have to fill in a form. The same bytes also fit a LoRa packet, a single SMS or a satellite text.
- **A dictionary isn't enough, and we prove it.** Self-correction ("tatlo, ay hindi, apat pala", three, no wait, four), negation, injuries described without a keyword ("hindi ko maigalaw ang binti", I can't move my leg), places inside a building, and endless phrase variants ("ang daming dugo", there's so much blood) all break keyword matching. The app runs a dictionary next to the model and shows both results.
- **Closed lists make adding a language cheap.** The model only outputs codes, so it can *understand* Bisaya without anyone writing Bisaya medical text. Bohol, Cebu, Odette's path and Davao are all Cebuano-speaking.

## 3. Goal and success criteria
With zero signal, a trapped Filipino can talk to Saklolo like a dispatcher and (a) get correct survival and first-aid instructions in English (or Filipino if they prefer), and (b) be **found, prioritised and pinpointed** by rescuers, for up to 72 hours on one charge.

Demo-day criteria:
- **Extraction:** at least 8 of 10 test reports (a mix of English, Filipino and Taglish) become a correct record, including hazard and place. At least 3 of the 10 are written by someone other than Kenneth. The model beats the dictionary baseline on the same set by at least 3. Both scores are shown on the Eval screen.
- **Bisaya (only if the hour-0 test passes):** at least 2 of 3 Cebuano reports become correct records.
- **Unclear answers:** 5 of 5 unclear phrases take the safer branch, and 0 clear "no" answers are read as "yes".
- **Myth-buster:** at least 8 of 10 questions matched to the right approved answer, and **0 wrong answers**. `NONE` is acceptable; a wrong answer is not.
- **Beacon:** received by a rescuer phone in airplane mode within 10 seconds. The acknowledgement gets back to the victim within 10 seconds.
- **Ring phone:** the victim's phone rings and strobes within 10 seconds of the rescuer's tap (the beacon running continuously).
- **Dispatch board:** ranks the records, reads the top one aloud, and its hot/cold meter changes as the rescuer walks toward the victim.
- **Battery:** drain per hour in survival mode is measured on the demo phone and shown as projected beacon life. The target is at least 72 hours; report the real number either way.

## 4. Users
| Persona | Context | Needs |
|---|---|---|
| **Trapped survivor** (target user) | On a roof in rising water, or inside a collapsed house, school or mall. Panicked, injured or with injured family, often at night, battery limited, possibly a low-end phone | Speak naturally, in whatever language comes out under stress (English, Filipino, Taglish, Bisaya); be told what to do; be found and pinpointed; know help is coming; keep the phone alive |
| **Rescuer** (receiving end: neighbour in bayanihan mode, barangay tanod, BFP USAR, Coast Guard, MDRRMO volunteer) | Standing on a collapsed building or in a boat among roofs; no network; hands busy | Who first, where, which floor and room, how many, who is hurt; hands-free; pinpoint the exact spot |

**Who has the app when disaster hits (pitched, not built):** MDRRMOs and schools install it during earthquake and typhoon drills. Residents are prompted to install it, and to download the model, when PAGASA raises a Signal No. 1 warning. Because every safe user becomes a rescuer, each install also adds a rescuer.

## 5. Core features (P0)

**Local AI design rule: the AI only picks from closed lists, it never writes medical text.** Every AI task returns either an ID from a fixed list or a few validated fields:
- **Safe:** no invented medical steps.
- **Easy to validate:** anything not in the list falls back to the buttons.
- **Fast and battery-cheap:** about 10–40 output tokens per call, and the model runs only when the user speaks.
- **Easy to explain to judges:** we can say exactly where the AI is and isn't used.

We chose *not* to use AI for triage colour or ranking (a rule is easier to audit), photo diagnosis (misidentification risk), medicine doses, or summaries (a template does the job).

**Never wait for the AI to call for help.** The beacon starts the moment the user confirms SOS, using whatever is known (tiles, or just a location). Every fact learned afterwards goes out as a new revision of the same record.

**CPR and bleeding control never wait for questions.** When a life threat is heard, its protocol goes on top immediately. Follow-up questions wait below it, marked "answer when you can". Without a life threat, the app works like a dispatcher: it gives one safety tip (the first step), then asks its questions one at a time, then continues with the remaining steps. Saying "ok" during a question repeats the question and never skips a step.

| # | Feature | Description | Local AI? |
|---|---|---|---|
| **D1** | **Dispatcher interview** | The user speaks. `extract()` returns `{hazard, people, trapped, place:{floor, spot}, vulnerable[], condition[]}` from closed lists, and the validator checks it. If a **required fact** is missing (in dispatcher order: hazard, anyone hurt, trapped, people, and place for COLLAPSE, LANDSLIDE or FLOOD), the app asks that fact's fixed follow-up question: at most 5 questions, then the interview ends. The questions are "What happened?", "Is anyone hurt?", "Can you get out?", "How many of you are there?" and "Which floor, and which part of the building?" (Filipino versions in `protocols.json`). The wording follows the hazard: under rubble "Is anyone hurt or pinned?"; in a flood "Can you get to higher, dry ground?" and "Which floor are you on? Are you on the roof?"; in a landslide "Are you inside a house, or outside?". A YES to the flood step "Is the water still rising?" is also sent to rescuers. Unclear answers to "Can you get out?" count as trapped (the safer reading). Tiles are always visible as the manual fallback | **Yes** (extraction) |
| **D2** | **Triage rule** | Triage colour is computed from the facts, not chosen by the AI. **RED:** not breathing, unconscious, heavy bleeding, or trapped with water rising. **YELLOW:** trapped, pinned, injured, a vulnerable person, any hazard, or an SOS with no facts yet. **GREEN:** otherwise | No, deliberately |
| **D3** | **Dictionary baseline, side by side** | A keyword dictionary runs on every utterance next to the model. In the **demo overlay**, which judges see and victims never do, both results appear side by side. The dictionary is also the fallback when the model can't run | No (it's the comparison) |
| **D4** | **Instructions until help arrives** | Deterministic protocols in English (default) and Filipino, read aloud. The life-threat protocol goes first, then the hazard protocol. **Not breathing:** CPR, starting with rescue breaths when the hazard is FLOOD (drowning). **Heavy bleeding:** firm pressure, don't lift the cloth. **Trapped in a collapse:** cover your mouth from dust; never light a match or lighter (gas leaks); don't move debris above you; tap 3 times on a pipe or wall; shout only when you hear rescuers; turn on survival mode. **On a roof, water rising:** go to the highest point *with a way out*; never wade or swim in a current; stay away from power lines; don't drink floodwater; signal with light or cloth | No, deliberately |
| **D5** | **Understanding answers** | Free answers in any language ("I think so", "I don't know", "oo yata", "parang humihinga pero mahina" = seems to be breathing but weakly) are classified by `answer()` as YES, NO or UNSURE. **UNSURE always takes the safer branch**, which each question node defines in `protocols.json`. The app shows what it understood ("I understood: NO"), and the yes/no buttons are always shown | **Yes** (classification) |
| **D6** | **Myth-buster** | During a protocol, the user can ask a free question. `matchFaq()` gets **only the current protocol's approved answers** and returns one id or `NONE`. `NONE` shows *"I can't answer that safely. Follow the steps and wait for rescuers."* About 16 answers aimed at Philippine disaster myths: lighting a lighter to see (gas), shouting nonstop (wastes air and energy; tap instead), running outside during an aftershock, the "triangle of life", swimming out, climbing into the ceiling, drinking floodwater, "storm surge is just a big wave", turning a drowned person upside down, coffee or toothpaste on wounds | **Yes** (matching to approved answers) |
| **D7** | **SOS beacon and survival mode** | The record is packed into 22 bytes and advertised over connectionless BLE from a foreground service. SOS is hold-to-send with a 3-second cancel. **Survival mode** turns on automatically after SOS: a black screen that wakes on a tap, and a duty cycle that stretches as the battery drops (continuous low-power advertising above 30%, then 10 s on and 50 s off). The screen shows "Beacon will last ~N more hours", from the measured drain | Carries the AI's output |
| **D8** | **Live updates and "getting worse"** | Speaking again at any time ("the water is rising!" or "tumataas ang tubig!") runs `extract()` again, and every changed fact bumps the revision. **The victim phone decides "getting worse",** because only it knows which facts were unknown before. A new condition on a known situation, a higher headcount, or "not trapped" becoming "trapped" sets the record's *worse* bit. Filling in an unknown fact never does | **Yes** (extraction, then a rule) |
| **D9** | **Dispatch board** | Ranked RED, then YELLOW, then GREEN; vulnerable people higher; then oldest report first. Each row shows headcount, **floor and room**, flags, distance and bearing, battery, and "last heard N min ago". A silent phone's last location stays on the board, and a low battery shows as "may stop soon". Opening a record **reads it aloud** from a template (*"RED. Collapse. Four people, including an elderly person and a child. Second floor, bathroom. Heavy bleeding. Battery 31 percent."*). It shows a GPS arrow and distance, plus a smoothed RSSI hot/cold meter, which is the main guide indoors and in rubble. SAFE records are counted in a separate "Safe" section | No |
| **D10** | **Ring phone** | From the record detail, the rescuer taps "Ring phone" and the phone advertises RING for that id. The victim's phone plays a loud chirp and strobes its flashlight for 30 seconds. Sound carries through rubble; a strobe on a dark roof is visible from a boat | No |
| **D11** | **"Help is coming"** | The rescuer taps "On my way" and an ACK goes out for that record id. The victim's phone shows *"Help is on the way"* and switches to low-latency advertising so the hot/cold meter updates faster. "Rescued" sends RESC: the victim's beacon stops and the record leaves the board | No |
| **D12** | **"I'm safe" → bayanihan mode** | One tap sends a SAFE record that replaces any earlier SOS from that phone, then opens the dispatch board. Safe neighbours become the first responders | No |
| **D13** | **Eval screen** | Runs the test sets on the device and shows dictionary score, model score and median latency per task | Shows the AI's results |

## 6. Should-have (P1, only if time allows)
- **P1-1 Relay:** bayanihan phones re-advertise records they heard, rotating through them, up to 5 hops. `(id, revision)` is the duplicate key, and a newer revision replaces an older one. Needs a third phone to demo.
- **P1-2 SMS gateway:** when a phone gets a moment of signal, it sends the board's records to an MDRRMO number, only after a manual tap.
- **P1-3 Landslide protocol:** LANDSLIDE is a hazard code in the MVP and uses the collapse protocol.

## 7. Out of scope
Animal bites, leptospirosis and other everyday emergencies (people can call or travel on an ordinary day) · Bisaya *instruction text* (Bisaya input only, and only if the hour-0 test passes) · BLE-free transports (Nearby Connections, Wi-Fi mesh, LoRa) · maps · iOS · cloud dashboard · automatic 911 or SMS · an LLM on the rescuer phone · typhoon preparation checklist · medical review (labelled "demo, not medically reviewed") · packet signing and encryption (risk noted).

## 8. Requirements
- **Safety:** medical and survival steps and answers come only from `protocols.json`. Follow-up questions come only from a fixed list. Every AI output is an ID or field from a closed list and is validated; anything invalid falls back to the tiles or buttons. Unclear answers take the safer branch. Life-threat protocols never wait for questions. A "Call 911" button is on every screen, because an emergency call can sometimes get through a jammed network or another carrier.
- **Privacy:** nothing is broadcast until the user confirms SOS or "I'm safe". The broadcast holds a location and facts, no names.
- **Language: English first.** Every screen, spoken instruction, follow-up question, approved answer and rescuer briefing is in English by default. A one-tap EN/FIL toggle on every screen switches all of them to Filipino, and the choice is remembered. *Input* doesn't depend on the setting: the model understands English, Filipino and Taglish (and Bisaya if the hour-0 test passes) either way, because it only outputs codes. Every string in `protocols.json` has `en` (the default) and `fil`.
- **Offline:** nothing at runtime needs the internet. The model is pushed once with adb.
- **Binary record (22 bytes of BLE service data; a legacy advertisement has 24 bytes free after flags and the service-data header):**

  | Field | Size |
  |---|---|
  | ver | 1 B |
  | id | 4 B |
  | lat ×1e5 (int32; `Int.MIN_VALUE` = no GPS fix) | 4 B |
  | lon ×1e5 (int32) | 4 B |
  | kind: code (4 bits) + triage (2 bits) + trapped (1 bit) + worse (1 bit) | 1 B |
  | people (0 = unknown) | 1 B |
  | vulnerable: infant, child, elderly, pregnant, PWD (3 bits spare) | 1 B |
  | condition: not breathing, unconscious, heavy bleeding, pinned, injured, water rising (2 bits spare) | 1 B |
  | place: floor (4 bits; 0 = unknown, 15 = roof) + spot (4 bits: unknown, bathroom, stairs, under a table, room, kitchen, hallway, attic, outside) | 1 B |
  | battery % | 1 B |
  | hops | 1 B |
  | revision | 1 B |
  | age in minutes | 2 B |

  Codes are COLLAPSE, FLOOD, LANDSLIDE, OTHER, SAFE, ACK, RESC and RING. For ACK, RESC and RING, `id` is the id of the victim record being addressed. The advertisement carries no device name or TX power.
- **Performance:** a protocol step shows in under 300 ms. Extraction takes under 5 s, with the beacon and protocol already running while it does. `answer()` and `matchFaq()` each take under 2 s. The model loads when the app starts, not when the mic is tapped.
- **Battery:** in survival mode, the victim phone scans for ACK and RING only during its advertise-on windows, so a ring can take up to one cycle (~60 s) at low battery. This is stated in the UI.
- **Devices and tiers:** the beacon, tiles, protocols, acknowledgement, ring and bayanihan mode run on any Android 8+ phone with BLE peripheral support (Android 12+ permissions: BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN). The model needs about 4 GB of RAM (Gemma 3 1B) or about 6–8 GB (Gemma 3n E2B with audio). Without the model, the dictionary takes over. **The AI makes Saklolo better, but its absence never stops the beacon.**

## 9. Technical approach
Kotlin with Compose. Files:
- `Protocol.kt`: pure engine; life-threat protocol first, then the hazard protocol; entry step chosen from facts
- `Triage.kt`: record, triage rule, "getting worse" rule, binary codec, validator, missing-fact follow-ups
- `Dictionary.kt`: keyword baseline and fallback
- `Llm.kt`: one loaded model, three closed-list tasks: `extract()`, `answer()`, `matchFaq()`
- `Voice.kt`: speech input and TTS
- `Beacon.kt`: foreground service, BLE advertise with duty cycle, filtered scan, ACK/RESC/RING
- `Rescuer.kt`: ranking, GPS bearing, smoothed RSSI, spoken briefing template
- `Alert.kt`: ring tone and flashlight strobe
- `MainActivity.kt`: screens, including survival mode, the demo overlay and the Eval screen
- `assets/protocols.json`: the 4 protocols (each question node has a `safer` branch; each protocol has a `faqs` list of `{id, q_examples, answer:{en,fil}}`), plus the follow-up questions
- `assets/eval/`: test sets for extraction (10 mixed English/Filipino/Taglish + 3 Cebuano), unclear answers (5 + 3 clear-no) and myth questions (10)

**Model path (decided in hour 0):**
- **A, preferred:** Gemma 3n E2B takes English, Filipino or Taglish audio directly through LiteRT-LM. One local model hears and understands, with no separate speech recogniser.
- **B:** Gemma 3 1B (int4) on text through MediaPipe LLM Inference (`tasks-genai`). Speech comes from Android's offline SpeechRecognizer: the English pack (on most phones), plus `fil-PH` if available, since an English recogniser handles Taglish poorly. Typing is the fallback.

JVM tests cover:
- the engine: safer branches, life-threat ordering, entry steps
- the triage rule and the "getting worse" rule (unknown → known is not worse)
- missing-fact follow-up selection
- the codec round-trip (22 bytes, no-fix sentinel, place nibbles) and `(id, revision)` duplicate handling
- the validator rejecting IDs that aren't in the list

The pattern (deterministic protocol plus validated AI) is credited to OASIS/MOASIS; no code is copied, because that repo has no license.

## 10. Build plan (10 hours)
| Hours | Deliverable | Covers |
|---|---|---|
| **0–1** | **Five go/no-go checks:** (a) Gemma 3n English and Taglish audio in Google's AI Edge Gallery on the demo phone; (b) Gemma 3 1B on 10 mixed English/Taglish text reports and 3 Cebuano; (c) offline English and Filipino TTS; (d) BLE advertise and scan "hello" between the two phones in airplane mode; (e) tone plus flashlight strobe. Pick model path A or B, and decide whether Bisaya is in | Main risks |
| 1–2 | Engine with safer branches, life-threat ordering and entry steps; 4 protocols in English (default) and Filipino; EN/FIL toggle; Protocol screen with TTS | D4 |
| 2–4 | Dispatcher loop: `extract()` (with place) → validator → triage rule → follow-ups → `answer()`; dictionary alongside; tiles fallback | D1, D2, D3, D5 |
| 4–5.5 | 22-byte codec with tests; beacon foreground service with duty cycle; filtered scan; battery byte | D7 |
| 5.5–6.5 | Dispatch board: ranking, floor and room, last heard, battery, GPS arrow, smoothed RSSI, spoken briefing | D9 |
| 6.5–7.5 | ACK and low-latency switch; Ring phone; RESC; "getting worse"; "I'm safe" → board | D8, D10, D11, D12 |
| 7.5–8.5 | ~16 approved answers and `matchFaq()`; survival-mode screen and battery measurement; Eval screen; demo overlay | D6, D7, D13 |
| 8.5–9 | Buffer, or relay with a third phone | P1-1 |
| 9–10 | Rehearse, backup video (including the flood scenario), Devpost | — |

**Kill switches:**
- Gemma 3n audio fails in hour 0: use path B.
- Gemma 3 1B scores below 6/10: try Gemma 3n E2B on text. If that also fails, the dictionary drives the app and the AI is pitched as roadmap. (This is the outcome that most hurts the score.)
- Cebuano fails: English, Filipino and Taglish only, and Bisaya becomes a roadmap item.
- ~~No offline `fil-PH` speech recognition on path B~~ Solved (2026-10-10): offline Whisper (sherpa-onnx) hears Filipino and Taglish; the text box stays as the fallback.
- No offline Filipino TTS: Filipino mode shows text only; English audio is unaffected.
- Flashlight API problems: ring with sound only.
- BLE unreliable in the hall: phones close together; the hot/cold walk goes to the backup video.
- Short on time: myth-buster for the collapse and bleeding protocols only (8 answers); survival mode becomes black screen only, with a fixed interval.

## 11. Demo script (2 minutes, 5 beats, earthquake scenario)
The stage is indoors with no GPS, and BLE range is short. That's exactly the earthquake case, so the limits of the stage become the scenario.

1. **Hook (15 s):** "When the Big One hits Metro Manila, or a typhoon like Yolanda hits Leyte, the network is the first thing to go. No one answers 911." Both phones go into airplane mode with Bluetooth on.
2. **Understand and ask (35 s):** The victim holds SOS (the beacon starts) and says *"Tulong! Gumuho ang bahay, naipit kami sa ikalawang palapag, sa may banyo. Tatlo kami, ay hindi, apat pala, kasama si lola. Hindi ko maigalaw ang binti ko, at ang daming dugo sa ulo ng anak ko!"* ("Help! The house collapsed, we're trapped on the second floor, by the bathroom. Three of us, no wait, four, including grandma. I can't move my leg, and there's so much blood on my child's head!"). In the demo overlay, the dictionary says 3 people with no injuries, while Gemma says COLLAPSE, 2nd floor, bathroom, 4 people, elderly, child, pinned, heavy bleeding → RED. "That's why it has to be AI, and why it has to be local." The app answers in English, its default, even though the report was in Taglish: it understands whatever language comes out under stress. Optional: the same report in Cebuano.
3. **Instructions (25 s):** The bleeding protocol starts immediately: firm pressure, don't lift the cloth. Myth question: *"Pwede bang sindihan ang lighter para makakita?"* (Can I light a lighter to see?) → approved answer: **No**, there may be a gas leak; use the phone's light. Show the 22 bytes: "the whole emergency, compressed by AI."
4. **Find (25 s):** On the rescuer phone, RED is at the top and is read aloud: "…Second floor, bathroom. Heavy bleeding." There's no GPS indoors, so the rescuer walks the stage following the hot/cold meter, then taps **Ring phone** and the victim's phone chirps and strobes.
5. **Payoff and close (20 s):** The rescuer taps "On my way" and the victim's phone shows **"Help is on the way"**. "The beacon lasts [measured] hours on one charge. Every safe phone becomes a rescuer: bayanihan, with no towers."

Ready for Q&A: the flood scenario (in the backup video), the Eval screen, survival mode, and an invitation for a judge to speak their own report.

## 12. Risks
| Risk | Mitigation |
|---|---|
| The model's Tagalog or Cebuano is weak | Test at hour 0; two model paths; validator, dictionary and tiles as fallbacks |
| Model loading takes 5–15 s | Load when the app starts; the beacon never waits for it |
| The myth-buster matches the wrong answer | Only the current protocol's answers are in the prompt; prefer `NONE`; test target is 0 wrong |
| `answer()` reads a clear "no" as "yes" | Shown back to the user; buttons always visible; clear-no cases in the eval set |
| Self-written eval sets look overfit | At least 3 reports written by others; invite a judge to speak their own report |
| Android stops advertising or scanning in the background | Foreground service; service-UUID scan filter; one long-running scan |
| The 72-hour battery target isn't met | Measure and report the real number; the duty cycle is the knob to tune |
| BLE range (~10–50 m; less through concrete) | Collapse search happens at short range anyway; Ring phone adds sound; bayanihan phones and relays extend reach; LoRa is roadmap |
| Ringing at the wrong moment | Only an addressed rescuer command triggers it; it stops after 30 s or on a tap |
| False or accidental SOS | Hold-to-send with a 3-second cancel; "I'm safe" replaces it |
| Spoofed or spam beacons | Roadmap: signed records |
| Medical and survival accuracy | DOH, PHIVOLCS, Red Cross and WHO sources; "not medically reviewed" label |

## 13. Roadmap
LoRa/Meshtastic bridge and SMS gateway (the same 22 bytes) · Bisaya, Hiligaynon and Waray instruction text · landslide protocol · a larger model on rescuer phones answering questions like "How many RED are left in this building?" · signed records · MDRRMO dashboard fed by gateways · Philippine Red Cross and BFP USAR review · school and barangay drill integration · install campaign tied to PAGASA signals · LGU, BFP and Coast Guard pilots.
