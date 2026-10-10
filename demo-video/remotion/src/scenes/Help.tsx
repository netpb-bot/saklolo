import { AbsoluteFill, useCurrentFrame } from "remotion";
import { Clip, Phone, footage } from "../footage";
import { C, MONO, kicker, pill, ramp, rise } from "../theme";
import { T } from "../timeline";
import { Logo } from "./Intro";

const { end, dur } = T.help;

// Beat 8 · Relief. "On my way" → the victim's screen turns green. Then the end card.
export const Help = () => {
  const f = useCurrentFrame();
  const warm = ramp(f, 40, end - 40, (x) => x);
  const out = ramp(f, end - 10, 12);
  return (
    <AbsoluteFill>
      <AbsoluteFill style={{ background: `radial-gradient(ellipse 55% 70% at 50% 50%, rgba(47,179,106,${0.32 * warm}), transparent 70%)`, opacity: 1 - out }} />
      <AbsoluteFill style={{ opacity: 1 - out, filter: out > 0.01 ? `blur(${out * 12}px)` : undefined }}>
        <Phone h={760} style={{ left: 960 - 175, top: 120, ...rise(f, 0, undefined, { dy: 40, dur: 16 }) }}>
          <Clip shot={footage.helpOnWayScreen} contain />
        </Phone>
        <div style={{ position: "absolute", right: 1920 - 960 + 175 + 70, top: 320, textAlign: "right", ...rise(f, 8) }}>
          <div style={{ ...kicker, color: C.mute }}>Rescuer</div>
          <div style={{ fontWeight: 800, fontSize: 48, marginTop: 10 }}>Taps “On my way”</div>
        </div>
        <div style={{ position: "absolute", left: 960 + 175 + 70, top: 320, ...rise(f, 40) }}>
          <div style={{ ...kicker, color: C.green }}>Victim</div>
          <div style={{ fontWeight: 800, fontSize: 48, marginTop: 10 }}>Sees “Help is on the way”</div>
          <div style={{ fontFamily: MONO, fontWeight: 600, fontSize: 20, color: C.mute, marginTop: 14 }}>back over the same Bluetooth channel</div>
        </div>
      </AbsoluteFill>
      {f >= end - 4 && <EndCard f={f - end} />}
    </AbsoluteFill>
  );
};

const EndCard = ({ f }: { f: number }) => (
  <AbsoluteFill style={{ alignItems: "center", textAlign: "center", background: `radial-gradient(ellipse 60% 55% at 50% 40%, rgba(0,45,114,${0.5 * ramp(f, 0, 14)}), transparent 72%)` }}>
    <div style={{ marginTop: 200, display: "flex", alignItems: "center", gap: 28, ...rise(f, 0) }}>
      <Logo size={112} draw={ramp(f, 0, 14)} arcs={ramp(f, 8, 12)} />
      <span style={{ fontWeight: 800, fontSize: 92, letterSpacing: "-0.03em" }}>Saklolo</span>
    </div>
    <div style={{ marginTop: 56, display: "flex", alignItems: "flex-end", gap: 36, ...rise(f, 8, undefined, { dy: 50 }) }}>
      <ZeroBars />
      <span style={{ fontWeight: 900, fontSize: 124, lineHeight: 0.9, letterSpacing: "-0.04em" }}>Works with zero bars</span>
    </div>
    <div style={{ marginTop: 40, fontWeight: 500, fontSize: 44, color: C.ice, ...rise(f, 18) }}>
      Every safe phone becomes a rescuer. <span style={{ color: C.teal, fontWeight: 700 }}>Bayanihan, with no towers.</span>
    </div>
    <div style={{ marginTop: 56, display: "flex", gap: 16, ...rise(f, 28) }}>
      {["Offline Whisper", "EmbeddingGemma 300M", "22-byte BLE beacon", "No cloud, no backend"].map((t) => <span key={t} style={{ ...pill, fontSize: 22, padding: "10px 22px" }}>{t}</span>)}
    </div>
    <div style={{ position: "absolute", bottom: 64, fontFamily: MONO, fontWeight: 600, fontSize: 20, color: C.mute, letterSpacing: "0.04em", lineHeight: 1.7, ...rise(f, 36) }}>
      Hackathon prototype · demo, not medically reviewed<br />
      <span style={{ color: C.dim }}>Source: MMEIRS 2004 (Metro Manila Earthquake Impact Reduction Study)</span>
    </div>
  </AbsoluteFill>
);

// Four empty signal bars, still with a check: works anyway.
const ZeroBars = () => (
  <svg width={110} height={100} viewBox="0 0 110 100" style={{ marginBottom: 6 }}>
    {[0, 1, 2, 3].map((i) => <rect key={i} x={i * 26 + 2} y={98 - (24 + i * 22)} width={18} height={24 + i * 22} rx={5} fill="none" stroke={C.dim} strokeWidth={3} />)}
  </svg>
);
