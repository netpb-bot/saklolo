import { AbsoluteFill, interpolate, useCurrentFrame } from "remotion";
import { Clip, footage } from "../footage";
import { C, MONO, ramp, rise } from "../theme";
import { T } from "../timeline";

const { stat, network, dur } = T.hook;

// Beat 1 · Dread. Real footage, then the statistic lands like a tremor and the signal bars fall away.
export const Hook = () => {
  const f = useCurrentFrame();
  const away = ramp(f, stat - 14, 16); // footage dissolves into the graphic
  const shake = f >= stat && f < stat + 16 ? Math.sin(f * 2.7) * 10 * (1 - (f - stat) / 16) : 0;
  const lives = Math.round(interpolate(ramp(f, stat + 8, 30), [0, 1], [0, 34000]));

  return (
    <AbsoluteFill>
      <AbsoluteFill style={{ opacity: 1 - away, filter: `blur(${away * 14}px)`, transform: `scale(${1 + f * 0.0006})` }}>
        <Clip shot={footage.hookNoService} style={{ filter: "saturate(.55) contrast(1.12) brightness(.8)" }} />
        <AbsoluteFill style={{ background: "linear-gradient(180deg, rgba(0,18,51,.35), rgba(4,9,19,.55))", mixBlendMode: "multiply" }} />
      </AbsoluteFill>

      {f >= stat - 4 && (
        <AbsoluteFill style={{ transform: `translate(${shake}px, ${shake * 0.4}px)` }}>
          <Seismo f={f} />
          <div style={{ position: "absolute", top: 268, width: "100%", textAlign: "center" }}>
            <div style={{ fontFamily: MONO, fontWeight: 700, fontSize: 24, letterSpacing: "0.3em", color: C.red, ...rise(f, stat, dur - 10) }}>
              THE BIG ONE · WEST VALLEY FAULT
            </div>
            <div style={{ fontWeight: 900, fontSize: 168, letterSpacing: "-0.04em", lineHeight: 1, marginTop: 30, fontVariantNumeric: "tabular-nums", ...rise(f, stat + 2, dur - 10, { dy: 60 }) }}>
              <span style={{ color: C.red }}>M7.2</span>
              <span style={{ color: C.dim, margin: "0 0.22em" }}>·</span>
              ~{lives.toLocaleString("en-US")}
              <span style={{ fontWeight: 600, color: C.mute, fontSize: 110, marginLeft: "0.25em", letterSpacing: "-0.02em" }}>lives</span>
            </div>
            <div style={{ fontSize: 34, color: C.mute, marginTop: 18, ...rise(f, stat + 14, dur - 10) }}>estimated deaths in Metro Manila</div>
          </div>

          <div style={{ position: "absolute", top: 628, width: "100%", display: "flex", justifyContent: "center", alignItems: "flex-end", gap: 32, ...rise(f, network, dur - 8) }}>
            <Bars f={f - network} />
            <div style={{ fontWeight: 700, fontSize: 60, letterSpacing: "-0.015em", lineHeight: 1 }}>
              and the network is the <span style={{ color: C.red }}>first thing to go</span>
            </div>
          </div>
          <div style={{ position: "absolute", bottom: 210, width: "100%", textAlign: "center", fontFamily: MONO, fontWeight: 600, fontSize: 20, letterSpacing: "0.06em", color: C.dim, ...rise(f, stat + 20, dur - 8) }}>
            Source: MMEIRS 2004
          </div>
        </AbsoluteFill>
      )}
    </AbsoluteFill>
  );
};

// A seismograph trace drawing across the frame; the spike lands with the number.
const Seismo = ({ f }: { f: number }) => {
  const p = ramp(f, stat - 4, 40);
  const pts = Array.from({ length: 241 }, (_, i) => {
    const x = (i / 240) * 1920;
    const d = Math.abs(i - 120);
    const amp = 5 + 55 * Math.exp(-d / 14) + 14 * Math.exp(-d / 50);
    return `${x.toFixed(1)},${(770 + Math.sin(i * 1.9) * Math.cos(i * 0.73) * amp).toFixed(1)}`;
  }).join(" ");
  return (
    <svg width={1920} height={1080} style={{ position: "absolute", opacity: 0.22 * (1 - ramp(f, dur - 12, 10)) }}>
      <polyline points={pts} fill="none" stroke={C.red} strokeWidth={2.5} pathLength={1} strokeDasharray={1} strokeDashoffset={1 - p} />
    </svg>
  );
};

// Four signal bars that drop out one by one.
const Bars = ({ f }: { f: number }) => (
  <svg width={84} height={60} viewBox="0 0 84 60" style={{ overflow: "visible" }}>
    {[0, 1, 2, 3].map((i) => {
      const drop = ramp(f, 14 + (3 - i) * 5, 12);
      const h = 15 + i * 15;
      return (
        <rect key={i} x={i * 22} y={60 - h} width={16} height={h} rx={4}
          fill={drop > 0.02 ? C.red : C.ice} opacity={1 - drop * 0.82}
          style={{ transform: `translateY(${drop * 16}px)`, transformOrigin: "50% 100%" }} />
      );
    })}
  </svg>
);
