import { AbsoluteFill, Audio, Composition, Series, interpolate, staticFile, useCurrentFrame } from "remotion";
import { captions } from "./captions";
import { has } from "./footage";
import { ARCHIVO, ARCHIVO_I, C, H, MONO, W, ramp, rise } from "./theme";
import { CHIP_FROM, DURATION, FPS, T } from "./timeline";
import { Hook } from "./scenes/Hook";
import { Intro } from "./scenes/Intro";
import { Report } from "./scenes/Report";
import { Aha } from "./scenes/Aha";
import { Myth } from "./scenes/Myth";
import { Bytes } from "./scenes/Bytes";
import { Rescue } from "./scenes/Rescue";
import { Help } from "./scenes/Help";

export const Root = () => (
  <Composition id="SakloloReel" component={Reel} durationInFrames={DURATION} fps={FPS} width={W} height={H} />
);

const BEATS = [
  [T.hook, Hook, "01 · The Big One"],
  [T.intro, Intro, "02 · Offline"],
  [T.report, Report, "03 · The call"],
  [T.aha, Aha, "04 · On-device AI"],
  [T.myth, Myth, "05 · While they wait"],
  [T.bytes, Bytes, "06 · 22 bytes"],
  [T.rescue, Rescue, "07 · The rescuer"],
  [T.help, Help, "08 · Found"],
] as const;

const Reel = () => (
  <AbsoluteFill style={{ background: `radial-gradient(ellipse 110% 90% at 50% 38%, #0B1834 0%, ${C.night} 58%, #02050b 100%)`, fontFamily: ARCHIVO, color: C.ink }}>
    <Series>
      {BEATS.map(([b, Scene], i) => (
        <Series.Sequence key={i} durationInFrames={b.dur} offset={i === 0 ? b.from : b.from - BEATS[i - 1][0].from - BEATS[i - 1][0].dur}>
          <Scene />
        </Series.Sequence>
      ))}
    </Series>
    <Grain />
    <AbsoluteFill style={{ background: "radial-gradient(ellipse 85% 75% at 50% 50%, transparent 55%, rgba(0,0,0,.55) 100%)" }} />
    <BeatLabel />
    <AirplaneChip />
    <Captions />
    {has("audio/vo.wav") && <Audio src={staticFile("audio/vo.wav")} />}
    {has("audio/music.mp3") && <Audio src={staticFile("audio/music.mp3")} volume={music} />}
  </AbsoluteFill>
);

// Film grain: fresh noise every frame.
const Grain = () => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill style={{ opacity: 0.075, mixBlendMode: "overlay", pointerEvents: "none" }}>
      <svg width={W} height={H}>
        <filter id="g"><feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves={2} seed={f % 24} stitchTiles="stitch" /></filter>
        <rect width={W} height={H} filter="url(#g)" />
      </svg>
    </AbsoluteFill>
  );
};

const strobeAt = T.rescue.from + T.rescue.strobe;
const endAt = T.help.from + T.help.end;

const BeatLabel = () => {
  const f = useCurrentFrame();
  const i = BEATS.findIndex(([b]) => f >= b.from && f < b.from + b.dur);
  if (i < 0 || (f >= strobeAt && f < T.help.from) || f >= endAt) return null; // keep the hero shot and end card clean
  const b = BEATS[i][0];
  return (
    <div style={{ position: "absolute", left: 72, top: 56, fontFamily: MONO, fontWeight: 600, fontSize: 20, letterSpacing: "0.22em", textTransform: "uppercase", color: C.ice, textShadow: "0 2px 12px rgba(0,0,0,.7)", ...rise(f, b.from + 4, b.from + b.dur - 10, { dy: 14 }) }}>
      <span style={{ color: C.teal }}>Saklolo</span> · {BEATS[i][2]}
    </div>
  );
};

// Proof of "no network", on screen from 0:07 to the end.
const AirplaneChip = () => {
  const f = useCurrentFrame();
  if (f < CHIP_FROM) return null;
  return (
    <div style={{
      position: "absolute", right: 64, top: 44, display: "flex", alignItems: "center", gap: 12, padding: "12px 22px", borderRadius: 999,
      background: "rgba(4,9,19,.8)", border: `1.5px solid ${C.line}`, fontFamily: MONO, fontWeight: 600, fontSize: 22, color: C.ice,
      ...rise(f, CHIP_FROM, undefined, { dy: -20 }),
    }}>
      <svg viewBox="0 0 24 24" width={24} height={24} fill={C.amber}><path d="M21 16v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z" /></svg>
      Airplane mode · 0 bars
    </div>
  );
};

// Where the caption bar sits: away from the phone picture-in-picture.
const side = (f: number) => {
  const inB = (b: { from: number; dur: number }) => f >= b.from && f < b.from + b.dur;
  if (inB(T.aha)) return "right";
  if (inB(T.intro) && f < T.intro.from + T.intro.logo) return "intro";
  if (inB(T.report) || inB(T.myth) || (inB(T.rescue) && f < strobeAt)) return "left";
  return "center";
};

const WHO = { VO: ["Voice-over", C.ice], CALLER: ["Caller · Taglish", C.amber], PHONE: ["The phone, speaking", C.teal] } as const;

const Captions = () => {
  const f = useCurrentFrame();
  const c = captions.find((x) => f >= x.from && f < x.to);
  if (!c) return null;
  const s = side(f);
  const pos = s === "intro" ? { left: 72, maxWidth: 1120 } : s === "left" ? { left: 72, maxWidth: 1300 } : s === "right" ? { left: 650, maxWidth: 1200 } : { left: 160, right: 160, marginInline: "auto", maxWidth: 1600 };
  const [label, color] = WHO[(c.who ?? "VO") as keyof typeof WHO];
  const out = ramp(f, c.to - 6, 6);
  return (
    <div style={{ position: "absolute", bottom: 52, ...pos, display: "flex", justifyContent: s === "center" ? "center" : "flex-start" }}>
      <div style={{
        padding: "18px 30px 22px", borderRadius: 18, background: "rgba(3,7,15,.86)", borderLeft: `6px solid ${color}`,
        boxShadow: "0 20px 60px rgba(0,0,0,.5)", ...rise(f, c.from, undefined, { dy: 14, dur: 8 }), opacity: ramp(f, c.from, 6) * (1 - out),
      }}>
        <div style={{ fontFamily: MONO, fontWeight: 600, fontSize: 18, letterSpacing: "0.18em", textTransform: "uppercase", color, marginBottom: 6 }}>{label}</div>
        {c.en ? (
          <>
            <div style={{ fontFamily: ARCHIVO_I, fontStyle: "italic", fontWeight: 500, fontSize: 44, lineHeight: 1.22, color: C.ice }}>“{c.text}”</div>
            <div style={{ fontWeight: 700, fontSize: 48, lineHeight: 1.2, color: "#fff", marginTop: 8 }}>{c.en}</div>
          </>
        ) : (
          <div style={{ fontWeight: 600, fontSize: 48, lineHeight: 1.22, color: "#fff" }}>{c.who === "PHONE" ? `“${c.text}”` : c.text}</div>
        )}
      </div>
    </div>
  );
};

// Music: drone ducked under any speech, 1 s of silence before the strobe, swell at the strobe,
// resolve at "Help is on the way", fade out at the end.
const music = (f: number) => {
  const S = T.rescue.from + T.rescue.silence;
  const level = interpolate(
    f,
    [0, 30, S - 14, S, strobeAt - 1, strobeAt, strobeAt + 24, T.help.from, T.help.from + 60, DURATION - 45, DURATION],
    [0, 0.5, 0.5, 0, 0, 0.55, 0.9, 0.9, 0.5, 0.5, 0],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" },
  );
  const speech = Math.max(0, ...captions.map((c) => interpolate(f, [c.from - 8, c.from, c.to, c.to + 10], [0, 1, 1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" })));
  return level * (1 - 0.7 * speech);
};
