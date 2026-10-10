import { AbsoluteFill, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { Clip, Phone, footage } from "../footage";
import { C, MONO, PHONE_ASPECT, kicker, pill, ramp, rise } from "../theme";
import { OVERLAY_FRAME, T } from "../timeline";

const { wrong, right, red, cloud, dur } = T.aha;
const PH = 900;
const PW = PH * PHONE_ASPECT;
// Where the Keywords and Saklolo columns sit in the frozen overlay frame, as fractions of the phone picture.
// ponytail: guessed from the Compare() layout; nudge these once the real screen recording exists.
const MARKS = { keywords: { x: 0.46, y: 0.7 }, saklolo: { x: 0.8, y: 0.7 } };
const AI = ["4 people", "pinned", "heavy bleeding", "child", "elderly", "Floor 2, bathroom"];

// Beat 4 · The aha. Same report, two readers: the keyword list misses it, the on-device AI doesn't.
export const Aha = () => {
  const f = useCurrentFrame();
  const { fps } = useVideoConfig();
  const dimPanel = ramp(f, cloud, 14);
  const stamp = spring({ frame: f - red, fps, config: { damping: 11, stiffness: 180 } });

  return (
    <AbsoluteFill>
      <AbsoluteFill style={{ backgroundImage: `linear-gradient(${C.line} 1px, transparent 1px), linear-gradient(90deg, ${C.line} 1px, transparent 1px)`, backgroundSize: "60px 60px", opacity: 0.35, maskImage: "radial-gradient(ellipse 70% 60% at 60% 50%, #000 20%, transparent 75%)" }} />

      <Phone h={PH} style={{ left: 110, top: 120, ...rise(f, 0, undefined, { dy: 30, dur: 14 }) }}>
        <Clip shot={footage.sosReportScreen} contain freeze={OVERLAY_FRAME} />
        <Mark at={MARKS.keywords} p={ramp(f, wrong + 4, 12)} kind="x" />
        <Mark at={MARKS.saklolo} p={ramp(f, right + 40, 12)} kind="v" />
      </Phone>

      <div style={{ position: "absolute", left: 650, top: 140, width: 1180, opacity: 1 - dimPanel * 0.85, filter: dimPanel > 0.01 ? `blur(${dimPanel * 6}px)` : undefined }}>
        <div style={{ ...kicker, ...rise(f, 2) }}>Same report · two readers</div>

        <div style={{ marginTop: 28, padding: "26px 32px", borderRadius: 22, border: "1.5px solid rgba(232,51,61,.45)", background: "rgba(232,51,61,.07)", position: "relative", filter: `saturate(${1 - 0.6 * ramp(f, wrong + 10, 10)})`, ...rise(f, 6) }}>
          <div style={{ fontFamily: MONO, fontWeight: 600, fontSize: 20, letterSpacing: "0.14em", color: C.mute }}>KEYWORD LIST</div>
          <div style={{ display: "flex", alignItems: "center", gap: 28, marginTop: 10 }}>
            <span style={{ position: "relative", fontWeight: 700, fontSize: 58, color: "#FF8A90", letterSpacing: "-0.01em" }}>
              3 people · no injuries
              <span style={{ position: "absolute", left: -6, right: -6, top: "54%", height: 6, borderRadius: 3, background: C.red, transform: `scaleX(${ramp(f, wrong, 10)})`, transformOrigin: "0 50%" }} />
            </span>
            <Tick p={ramp(f, wrong + 4, 12)} kind="x" size={64} />
          </div>
        </div>

        <div style={{ marginTop: 26, padding: "26px 32px 30px", borderRadius: 22, border: "1.5px solid rgba(127,214,208,.5)", background: "linear-gradient(180deg, rgba(127,214,208,.10), rgba(127,214,208,.03))", ...rise(f, right - 8) }}>
          <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
            <div style={{ fontFamily: MONO, fontWeight: 600, fontSize: 20, letterSpacing: "0.14em", color: C.teal }}>SAKLOLO · ON-DEVICE AI</div>
            <div style={{ marginLeft: "auto" }}><Tick p={ramp(f, right + 40, 12)} kind="v" size={56} /></div>
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 14, marginTop: 18 }}>
            {AI.map((t, i) => (
              <span key={t} style={{ padding: "10px 20px", borderRadius: 12, background: "rgba(217,230,255,.09)", fontWeight: 700, fontSize: 38, color: C.ink, ...rise(f, right + i * 6, undefined, { dy: 20, dur: 12 }) }}>{t}</span>
            ))}
            <span style={{ display: "inline-flex", alignItems: "center", gap: 16, opacity: ramp(f, red - 4, 6) }}>
              <span style={{ fontFamily: MONO, fontSize: 40, color: C.mute }}>→</span>
              <span style={{ display: "inline-block", padding: "6px 24px", borderRadius: 14, background: C.red, color: "#fff", fontWeight: 900, fontSize: 56, letterSpacing: "0.04em", transform: `scale(${1.6 - 0.6 * stamp})`, boxShadow: `0 0 ${40 * stamp}px rgba(232,51,61,.55)` }}>RED</span>
              <span style={{ fontFamily: MONO, fontWeight: 600, fontSize: 19, color: C.mute, lineHeight: 1.3 }}>triage<br />rule</span>
            </span>
          </div>
        </div>

        <div style={{ ...pill, marginTop: 30, borderColor: "rgba(185,166,255,.5)", ...rise(f, right + 50) }}>
          <span style={{ color: C.ai }}>◆</span> On-device AI · <b style={{ color: C.ai }}>EmbeddingGemma 300M</b> · offline
        </div>
      </div>

      {f >= cloud - 2 && <Cloud f={f - cloud} />}
    </AbsoluteFill>
  );
};

const Tick = ({ p, kind, size }: { p: number; kind: "x" | "v"; size: number }) => (
  <svg width={size} height={size} viewBox="0 0 64 64" style={{ flex: "none", overflow: "visible" }}>
    <circle cx={32} cy={32} r={30} fill={kind === "x" ? "rgba(232,51,61,.16)" : "rgba(127,214,208,.16)"} opacity={p} />
    <path d={kind === "x" ? "M20 20 L44 44 M44 20 L20 44" : "M17 33 L28 44 L48 21"} fill="none" stroke={kind === "x" ? C.red : C.teal}
      strokeWidth={7} strokeLinecap="round" strokeLinejoin="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - p} />
  </svg>
);

// The same ✗ / ✓ drawn on top of the real footage, over each column.
const Mark = ({ at, p, kind }: { at: { x: number; y: number }; p: number; kind: "x" | "v" }) => (
  <div style={{ position: "absolute", left: at.x * PW - 48, top: at.y * PH - 48, filter: "drop-shadow(0 6px 16px rgba(0,0,0,.6))" }}>
    <Tick p={p} kind={kind} size={96} />
  </div>
);

// "It can't be cloud AI": the cloud loses its connection and falls.
const Cloud = ({ f }: { f: number }) => {
  const cut = ramp(f, 14, 12);
  return (
    <div style={{ position: "absolute", left: 650, top: 200, width: 1180, display: "flex", alignItems: "center", gap: 60 }}>
      <svg width={300} height={280} viewBox="0 0 300 280" style={{ flex: "none", overflow: "visible", ...rise(f, 0) }}>
        <g style={{ transform: `translateY(${cut * 34}px)`, opacity: 1 - cut * 0.55 }}>
          <path d="M80 170 a50 50 0 0 1 4-99 a68 68 0 0 1 128 10 a45 45 0 0 1 8 89 Z" fill="rgba(217,230,255,.05)" stroke={C.ice} strokeWidth={6} strokeLinejoin="round" strokeDasharray={cut > 0.5 ? "14 12" : undefined} />
        </g>
        <path d="M150 196 L150 262" stroke={C.ice} strokeWidth={6} strokeLinecap="round" strokeDasharray="10 12" opacity={1 - cut} />
        <path d="M60 60 L240 240" stroke={C.red} strokeWidth={10} strokeLinecap="round" pathLength={1} strokeDasharray={1} strokeDashoffset={1 - cut} />
      </svg>
      <div>
        <div style={{ ...kicker, color: C.red, ...rise(f, 4) }}>Why it has to be local</div>
        <div style={{ fontWeight: 800, fontSize: 76, lineHeight: 1.04, letterSpacing: "-0.025em", marginTop: 18, ...rise(f, 8) }}>
          No towers,<br />no cloud AI.
        </div>
        <div style={{ fontWeight: 700, fontSize: 48, color: C.teal, marginTop: 22, ...rise(f, 22) }}>So the AI runs on the phone.</div>
      </div>
    </div>
  );
};
