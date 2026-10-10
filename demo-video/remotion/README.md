# Saklolo · 60-second demo reel (Remotion)

One composition, `SakloloReel`: 1920×1080, 30 fps, 1800 frames.

```
npm i
npx remotion studio                                   # preview and scrub
npx remotion render SakloloReel out/saklolo-reel.mp4  # H.264, CRF 18 (remotion.config.ts)
npx remotion still SakloloReel out/f.jpg --frame=795  # check a single frame
```

It renders with no footage at all: every missing file shows a dark slate with its filename, its beat and what the shot must show. Drop the real file into `public/` under the same name and it replaces the slate, with no code change. The map lives in `src/footage.tsx` (`footage`); the existence check uses `getStaticFiles()`.

## Footage checklist (`public/`)

| File | What it shows | Audio |
|---|---|---|
| `footage/hook-no-service.mp4` | Camera: dark room, a phone showing "No service", a 911 call failing | kept |
| `footage/airplane-open.mp4` | Screen rec: airplane mode turned on, then Saklolo opened to the SOS home | kept |
| `footage/sos-report-cam.mp4` | Camera: a hand holding SOS while the person speaks the report | 0.35 (room tone) |
| `footage/sos-report-screen.mp4` | Screen rec of the same take, **ending on the demo overlay** (Keywords vs Saklolo) | kept |
| `footage/protocol-myth-screen.mp4` | Screen rec: bleeding protocol, then the lighter question and its answer card | kept (TTS) |
| `footage/rescuer-board-screen.mp4` | Screen rec: RED at the top of the board, briefing read aloud, hot/cold meter | kept (TTS) |
| `footage/rescuer-walk-cam.mp4` | Camera: the rescuer walking, then tapping Ring phone | 0.25 |
| `footage/strobe-dark-cam.mp4` | Camera: the victim phone strobing and chirping in the dark (hero shot) | kept |
| `footage/help-on-way-screen.mp4` | Screen rec: "On my way" tapped, victim screen shows "Help is on the way" | kept |
| `audio/vo.wav` | Voice-over (optional; until it exists the captions carry the VO text) | |
| `audio/music.mp3` | Low drone; auto-ducked under speech, silent 1 s before the strobe, swells at the strobe, resolves at "Help is on the way" | |

Shot notes:
- Screen recordings are shown with `objectFit: contain` and are never cropped, so the **status bar (airplane icon) stays visible**. Record at the phone's native resolution; if it isn't 1080×2340, change `PHONE_ASPECT` in `src/theme.ts`.
- Each clip plays from its first frame when its beat starts. Trim the files to start on the action.
- Per-clip volume is the `volume` field in `src/footage.tsx`. The report voice is assumed to come from the screen recording. If the screen recorder didn't capture the mic, raise `sosReportCam.volume` to 1 and set `sosReportScreen.volume` to 0.

## Retiming after the shoot

Every timing is in **`src/timeline.ts`**:

```ts
report: { from: 360, dur: 300, beacon: 54 },
//        ^ start  ^ length    ^ offsets inside the beat (callouts, cuts)
```

- Beats must stay back to back (`from` = previous `from + dur`) and add up to `DURATION`.
- `OVERLAY_FRAME` is the frame of `sos-report-screen.mp4` that beat 4 freezes on. Set it to the frame where the Keywords vs Saklolo overlay is fully on screen.
- `rescue.silence` → `rescue.strobe` is the ~1 s of silence before the hard cut to the strobe.
- Captions are in **`src/captions.ts`** in absolute frames (`from`, `to`, `text`, optional `en` subtitle). The music ducks under any active caption, so keep captions in sync with the speech.
- The ✗ and ✓ marks drawn on the frozen overlay use `MARKS` in `src/scenes/Aha.tsx` (fractions of the phone picture). Nudge them once the real recording exists.

## Files

```
src/timeline.ts   all beat timings
src/captions.ts   burned-in captions
src/theme.ts      tokens, fonts, motion (rise = lift + de-blur in, soft blur out)
src/footage.tsx   footage map, slate, phone frame
src/Root.tsx      composition, grain, vignette, captions, airplane chip, audio
src/scenes/*.tsx  one file per beat
```

Content rules followed: the app UI is never drawn (phone screens are footage or slates). The AI is named as offline Whisper plus EmbeddingGemma 300M, and it picks from closed lists. Triage colour is labelled as a rule. There are no eval or battery numbers. The end card carries the disclaimer and the MMEIRS 2004 source.
