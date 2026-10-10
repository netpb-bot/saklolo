# Saklolo pitch

Use this alongside `demo-video/saklolo-demo.mp4` (source: `demo-video/saklolo-film.html`). Every number here is measured. If a number changes, update it in both places.

## The framing
- **Pain point: no dispatcher, not no network.** In the first 72 hours after the Big One, the people first on the street (neighbours and tanods, before BFP USAR arrives) search blind. They don't know which house, which floor, how many people, or who is bleeding. A pinned, panicking person can't type a form. Mesh apps (Bridgefy, Meshtastic) carry raw text that someone still has to read. Nothing does the dispatcher's job offline.
- **One line:** *Saklolo is the 911 dispatcher that still works when 911 can't. It listens, gives instructions, and guides rescuers through the last 50 metres.*
- **The last 50 metres.** BLE range is the design, not a flaw: collapse and flood searches happen at close range. The same 22 bytes also fit a LoRa packet or an SMS (roadmap).
- **Hero scenario:** an M7.2 on the West Valley Fault in Metro Manila (MMEIRS 2004). It's a Tagalog/Taglish area, which matches what Saklolo understands. Odette is used only as evidence that networks fail.
- **Users, in this order:**
  1. The trapped survivor, who speaks.
  2. Whoever's phone hears the beacon: a neighbour in bayanihan mode, or a responder (tanod, BFP, Coast Guard) using the same app as a receiver. It knows you need help and finds you.
  3. Barangay DRRM offices and schools, who deploy it during the quarterly earthquake drills.
- **Local AI is two on-device models: one hears, one understands.** Whisper does offline Taglish speech recognition, which Android's own recogniser can't do on the S24 (English packs only). EmbeddingGemma 300M with classifier heads catches life threats described without the keyword.
- **Line to repeat:** "Rules first. The AI catches what the rules can't, and it has never overridden a correct rule."

## 30-second version
"When the Big One hits Manila, the towers go first and 911 can't answer. The people who dig survivors out in the first hours are neighbours, and they search blind. Saklolo is the dispatcher that still works with no signal. The trapped person just talks, in Taglish. Two models on the phone hear and understand it. Rules pull out the exact facts: how many people, which floor. The AI catches what has no keyword, like 'the towel is soaked', which means heavy bleeding. It reads first aid aloud and broadcasts the whole emergency in 22 bytes over Bluetooth. A neighbour's phone ranks who's worst, follows the signal through the last 50 metres, and makes the victim's phone ring."

## Numbers (Samsung S24 Ultra, 2026-10-10, source: `train/README.md`)
| | Rules only | Rules + AI |
|---|---|---|
| 29 hard cases, whole record correct | 28% | 41% |
| 58 real #RescuePH tweets | 52% | 55% |

- 0 wrong life-threat labels (not breathing, unconscious, heavy bleeding) in any final record.
- 0 cases where the AI turned a correct rule result wrong. 6 test reports where rules missed and the AI caught it.
- 8/8 unclear answers took the safer branch.
- The demo report itself is fully read by rules. The AI adds nothing there, and the film says so with RULES and RULES + AI tags.

## Pushback
| Judge asks | Answer |
|---|---|
| Who will have it installed? | Barangay and school earthquake drills, plus an install prompt at PAGASA Signal No. 1. Without the model, the beacon, tiles and protocols still run on any Android 8+ phone. Each install is also a rescuer. |
| BLE only reaches 10–50 m. | By design: we solve the last 50 m (which floor, which room, ring the phone). Long range means LoRa or SMS carrying the same 22 bytes. |
| The AI only adds 3–13 points. | Those are the hardest cases: bleeding or unconsciousness described without the keyword. And it never made a record worse. |
| Why not a bigger LLM? | Gemma 3 1B failed our eval on the S24, needs more RAM, and could invent a life threat. Closed lists can't. |
| Isn't this Bridgefy? | Bridgefy carries typed text. We do the dispatcher's job: understand, instruct, prioritise, pinpoint. |
| Bisaya, Waray? | Not yet. Because outputs are closed lists, adding a language is a training-data job, not a medical-text job. |
| Weren't the bars tuned on your test sets? | Yes, and `train/README.md` says so. Speak your own report right now. That's the independent test. |
| Medical liability? | Every instruction comes from fixed protocols; the AI never writes text people see. Unclear answers take the safer branch. Labelled "not medically reviewed". |
| Battery? | **TODO: measure survival-mode drain on the demo phone before the pitch.** |

## Live demo additions
- Invite a judge to speak their own report.
- If asked "where's the AI?", run the Eval screen and point to hard case #4 (the bleeding line from the film): rules=false, ai=true.
