# Prompt: build the Saklolo 60-second demo reel in Remotion

You are building a 60-second hackathon demo reel for **Saklolo**, an offline 911 dispatcher for Android, as a **Remotion** project. Judges score it on problem and usefulness, on-device (local) AI, technical execution, innovation and demo quality. The reel must show the **real app**, and it must leave judges with no doubt about **why the AI has to run locally**.

## Read these first (repo root: `saklolo/`)
- `CLAUDE.md`: what the app is, the stack, and the safety rules
- `PRD.md` §1–2: the problem, and "Why it has to be local AI"
- `demo-video/saklolo-film.html`: the earlier all-animated 60 s film. Reuse its **design tokens** (the `:root` colours), its fonts (Archivo, JetBrains Mono), its motion style (lift and de-blur in, soft blur out, film grain, vignette) and its **real 22-byte record** (the bytes from `Record.encode()` near the comment "the real record"). Do **not** reuse its drawn phone mockups.
- `Android native mobile app design/saklolo-logo.svg`: the logo
- `app/src/main/assets/protocols.json`: the exact wording of the spoken steps and the "lighter" myth answer
- `app/src/main/java/ph/saklolo/MainActivity.kt`, around `Demo.REPORT` and `hexFields()`: the exact demo report and the field order of the 22 bytes

## Hard rules
1. **Never draw, mock or recreate the app's UI.** Every phone screen in the video comes from real footage files (listed below). Motion graphics are allowed only for the hook statistic, the 22-byte breakdown, callouts on top of real footage, and the end card.
2. **Missing footage gets a placeholder slate, never a fake.** The footage hasn't been shot yet. If a file in `public/footage/` doesn't exist, render a dark slate that shows the filename, the beat name and what the shot must show. A single `const footage = {...}` map plus `getStaticFiles()` (or a simple existence check) decides this.
3. **Name the AI correctly:** offline **Whisper** (speech to text) plus **EmbeddingGemma 300M** with small classifier heads. It *picks from closed lists*; it never writes the text people see. Don't say "Gemma 3", "LLM" or "generates".
4. **No unmeasured claims.** Don't add eval percentages or battery hours. Triage colour is a rule, not AI: if you label it, say "triage rule".
5. The end card includes "Hackathon prototype · demo, not medically reviewed" and the source "MMEIRS 2004".

## Spec
- 1920×1080, 30 fps, `durationInFrames = 1800`. One composition, `SakloloReel`.
- All beat timings live in **one** `src/timeline.ts` (start frame and duration per beat), so the team can retime after the shoot without touching the scenes. Lay out the scenes with `<Series>` or `<Sequence>`.
- Use `<OffthreadVideo>` for footage, `<Audio>` for the voice-over and music, `staticFile()` for assets, `@remotion/google-fonts` for Archivo and JetBrains Mono, and `interpolate`/`spring` for motion. Don't add any other dependencies.
- **Burned-in captions** from `src/captions.ts` (`{from, to, text}` in frames). Show the English subtitle under the Taglish report and the myth question. Size: at least 44 px, in a high-contrast bar, readable when muted.
- **Picture-in-picture:** in beats 3, 4 and 7, show the phone's screen recording large (about 60% of the height, rounded corners, soft shadow) next to or over the camera shot, so the UI is readable.
- **Keep the real status bar visible.** Never crop the phone status bar, because the airplane-mode icon there is proof of "no network". Add a small persistent top-corner chip from 0:07 onwards: `✈ Airplane mode · 0 bars`.

## Footage and audio (`public/`)
| File | What it shows |
|---|---|
| `footage/hook-no-service.mp4` | Camera: a dark room and a phone showing "No service", with a 911 call failing |
| `footage/airplane-open.mp4` | Screen recording: airplane mode turned on, then Saklolo opened to the SOS home |
| `footage/sos-report-cam.mp4` | Camera: a hand holding SOS while the person speaks the report |
| `footage/sos-report-screen.mp4` | Screen recording of the same take, ending on the demo overlay (Keywords vs Saklolo) |
| `footage/protocol-myth-screen.mp4` | Screen recording: the bleeding protocol, then the lighter question and its answer card |
| `footage/rescuer-board-screen.mp4` | Screen recording: RED at the top of the board, the briefing read aloud, the hot/cold meter |
| `footage/rescuer-walk-cam.mp4` | Camera: the rescuer walking, then tapping Ring phone |
| `footage/strobe-dark-cam.mp4` | Camera: the victim phone strobing and chirping in the dark (hero shot) |
| `footage/help-on-way-screen.mp4` | Screen recording: "On my way" tapped, and the victim screen showing "Help is on the way" |
| `audio/vo.wav` | The voice-over (optional; until it exists, the captions carry the voice-over text) |
| `audio/music.mp3` | Low drone that swells at the strobe and resolves at "Help is on the way". Duck it under speech |

Screen recordings keep their own audio (the app's TTS and the report). Keep that audio: the phone speaking is the most convincing proof.

## Beats (frames at 30 fps)
| # | Frames | Picture | Voice-over / audio | Feeling |
|---|---|---|---|---|
| 1 | 0–210 | `hook-no-service`, then a graphic: **M7.2 · ~34,000 lives** · "and the network is the first thing to go" · src MMEIRS 2004 | VO: "When the Big One hits, the towers go first. 911 can't answer." | Dread |
| 2 | 210–360 | `airplane-open`, then the Saklolo logo and wordmark landing for about 1 s | VO: "Saklolo is a 911 dispatcher that lives on your phone. No signal needed." | Curiosity |
| 3 | 360–660 | `sos-report-cam` with PiP `sos-report-screen`. Callout: "Beacon on, before the AI even finishes" | The real Taglish report, with an English subtitle (from `Demo.REPORT`) | Panic, empathy |
| 4 | 660–930 | Hold on the demo overlay frame. Animate a red ✗ over the Keywords column ("3 people · no injuries") and a teal ✓ over the Saklolo column (4 · pinned · heavy bleeding · child · elderly · Floor 2, bathroom → **RED**). Caption chip: "On-device AI · EmbeddingGemma 300M · offline" | VO: "A keyword list hears three people, no injuries. The on-device AI hears four, the bleeding, the floor and the room. And it can't be cloud AI. The cloud went down with the towers." | The "aha" moment |
| 5 | 930–1140 | `protocol-myth-screen` | Phone TTS: "Press hard on the wound with a cloth." · the question "Pwede bang sindihan ang lighter?" · the answer "No… Gas may be leaking…" | Protected |
| 6 | 1140–1290 | Graphic: a transcript block of about 200 words collapses into the 22 real bytes, which then split into labeled fields (id, lat/lon NO_FIX, kind, people, vuln, cond, place, battery, rev, age). "~200 words → 22 bytes" | VO: "The AI squeezes the whole emergency into 22 bytes, small enough for a Bluetooth beacon." | Clever |
| 7 | 1290–1590 | `rescuer-board-screen` (PiP) over `rescuer-walk-cam`, then a hard cut to `strobe-dark-cam`. Leave **about 1 s of silence** before the strobe | Phone briefing: "RED. Collapse. Four people… second floor, bathroom." VO: "No GPS under rubble. Follow the signal, then make it ring." | Hope |
| 8 | 1590–1800 | `help-on-way-screen`, ending on the green "Help is on the way" for about 1 s. End card: logo · **Works with zero bars** · "Every safe phone becomes a rescuer. Bayanihan, with no towers." · disclaimer and sources | VO: "Every safe phone becomes a rescuer. Bayanihan, with no towers." | Relief |

## Deliverables
1. The Remotion project in `saklolo/demo-video/remotion/` (TypeScript), runnable with `npm i && npx remotion studio`.
2. It renders with `npx remotion render SakloloReel out/saklolo-reel.mp4` (H.264, CRF about 18). With no footage present, it still renders, using the slates.
3. A short `README.md` with the footage checklist (the table above), how to retime beats in `timeline.ts`, and the render command.
4. Before finishing, render a still of the middle of each beat (`npx remotion still`) and look at it: text isn't clipped, captions are readable, and the slates show the right filename.

Keep it small: one file per beat at most, shared tokens in one `theme.ts`, and no abstractions beyond that.
