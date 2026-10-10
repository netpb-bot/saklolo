// Every timing in the reel lives here (frames, 30 fps). Retime after the shoot by editing this file only.
// `from`/`dur` place the beat in the reel; every other key is an offset from the beat's own start.
export const FPS = 30;
export const DURATION = 1800;

export const T = {
  hook: { from: 0, dur: 210, stat: 96, network: 150 }, // footage, then the M7.2 graphic, then "the network…"
  intro: { from: 210, dur: 150, logo: 104 }, // airplane-open, then logo + wordmark (~1.5 s)
  report: { from: 360, dur: 300, beacon: 54 }, // SOS pressed → "Beacon on" callout
  aha: { from: 660, dur: 270, wrong: 40, right: 92, red: 150, cloud: 196 },
  myth: { from: 930, dur: 210, ask: 84, match: 118, answer: 140 },
  bytes: { from: 1140, dur: 150, collapse: 28, split: 62 },
  rescue: { from: 1290, dur: 300, silence: 196, strobe: 226 }, // 1 s of silence, then a hard cut to the strobe
  help: { from: 1590, dur: 210, end: 108 }, // help-on-way, then the end card
};

// The frame inside sos-report-screen.mp4 that beat 4 freezes on (the demo overlay, Keywords vs Saklolo).
// Default: the clip's last frame shown in beat 3. Change it if the overlay settles earlier in the take.
export const OVERLAY_FRAME = T.report.dur - 1;

// The persistent "✈ Airplane mode · 0 bars" chip appears at 0:07.
export const CHIP_FROM = 210;
