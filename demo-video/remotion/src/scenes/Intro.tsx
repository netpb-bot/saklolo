import { AbsoluteFill, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { Clip, Phone, footage } from "../footage";
import { C, MONO, kicker, ramp, rise } from "../theme";
import { T } from "../timeline";

const { logo, dur } = T.intro;

// Beat 2 · Curiosity. Airplane mode on, Saklolo opens; then the mark lands.
export const Intro = () => {
  const f = useCurrentFrame();
  const out = ramp(f, logo - 10, 12);
  return (
    <AbsoluteFill>
      <AbsoluteFill style={{ opacity: 1 - out, filter: out > 0.01 ? `blur(${out * 12}px)` : undefined }}>
        <div style={{ position: "absolute", left: 200, top: 300, width: 760 }}>
          <div style={{ ...kicker, ...rise(f, 4) }}>No network on purpose</div>
          <div style={{ fontWeight: 800, fontSize: 84, letterSpacing: "-0.025em", lineHeight: 1.02, marginTop: 22, ...rise(f, 8) }}>
            Airplane mode.<br />Then open Saklolo.
          </div>
          <div style={{ marginTop: 44, display: "flex", flexDirection: "column", gap: 18 }}>
            {["Mobile network", "Wi-Fi", "Cloud AI"].map((t, i) => <Gone key={t} f={f} at={30 + i * 12} text={t} />)}
          </div>
        </div>
        <Phone h={860} style={{ left: 1260, top: 136, ...rise(f, 0, undefined, { dy: 50, dur: 20 }) }}>
          <Clip shot={footage.airplaneOpen} contain />
        </Phone>
      </AbsoluteFill>
      {f >= logo - 2 && <Mark f={f - logo} />}
    </AbsoluteFill>
  );
};

const Gone = ({ f, at, text }: { f: number; at: number; text: string }) => {
  const s = ramp(f, at + 8, 10);
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 20, fontFamily: MONO, fontWeight: 600, fontSize: 32, color: C.mute, ...rise(f, at) }}>
      <span style={{ width: 34, textAlign: "center", color: C.red, fontWeight: 700, opacity: s }}>✕</span>
      <span style={{ position: "relative" }}>
        {text}
        <span style={{ position: "absolute", left: -4, right: -4, top: "54%", height: 3, background: C.red, transform: `scaleX(${s})`, transformOrigin: "0 50%" }} />
      </span>
    </div>
  );
};

// The logo draws itself (house, then the signal arcs and dot), then the wordmark rises letter by letter.
export const Logo = ({ size, draw = 1, arcs = 1 }: { size: number; draw?: number; arcs?: number }) => (
  <svg viewBox="0 0 108 108" width={size} height={size}>
    <rect width="108" height="108" rx="24" fill={C.navy} />
    <g fill="none" stroke={C.ice} strokeLinecap="round" strokeLinejoin="round">
      <path d="M28,56 L54,32 L80,56 M36,50 L36,76 L72,76 L72,50" strokeWidth={5} pathLength={1} strokeDasharray={1} strokeDashoffset={1 - draw} />
      <path d="M48.34,56.34 A8,8 0 0,1 59.66,56.34" strokeWidth={4} opacity={ramp(arcs, 0, 0.5, (x) => x)} />
      <path d="M44.1,52.1 A14,14 0 0,1 63.9,52.1" strokeWidth={4} opacity={ramp(arcs, 0.4, 0.6, (x) => x)} />
    </g>
    <circle cx="54" cy="62" r="4.5" fill={C.ice} opacity={ramp(arcs, 0, 0.2, (x) => x)} />
  </svg>
);

const Mark = ({ f }: { f: number }) => {
  const { fps } = useVideoConfig();
  const pop = spring({ frame: f, fps, config: { damping: 13, stiffness: 140 } });
  const pulse = (f % 30) / 30;
  return (
    <AbsoluteFill style={{ background: `radial-gradient(ellipse 60% 55% at 50% 42%, rgba(0,45,114,${0.55 * ramp(f, 0, 12)}), transparent 70%)` }}>
      <div style={{ position: "absolute", left: 960 - 90, top: 230, width: 180, height: 180, transform: `scale(${0.55 + 0.45 * pop})`, opacity: ramp(f, 0, 6) }}>
        {f > 14 && [0, 1].map((k) => {
          const p = (pulse + k * 0.5) % 1;
          return <div key={k} style={{ position: "absolute", inset: -60 * p, borderRadius: 40 + 60 * p, border: `2px solid ${C.ice}`, opacity: (1 - p) * 0.35 }} />;
        })}
        <Logo size={180} draw={ramp(f, 2, 16)} arcs={ramp(f, 12, 14)} />
      </div>
      <div style={{ position: "absolute", top: 450, width: "100%", textAlign: "center", fontWeight: 800, fontSize: 132, letterSpacing: "-0.03em" }}>
        {[..."Saklolo"].map((ch, i) => <span key={i} style={{ display: "inline-block", ...rise(f, 8 + i * 2, dur - logo - 6, { dy: 40, dur: 14 }) }}>{ch}</span>)}
      </div>
      <div style={{ position: "absolute", top: 625, width: "100%", textAlign: "center", fontFamily: MONO, fontWeight: 600, fontSize: 24, letterSpacing: "0.3em", color: C.teal, ...rise(f, 22, dur - logo - 6) }}>
        OFFLINE 911 DISPATCHER
      </div>
    </AbsoluteFill>
  );
};
