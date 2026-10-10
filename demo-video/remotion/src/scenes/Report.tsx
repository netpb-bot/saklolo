import { AbsoluteFill, useCurrentFrame } from "remotion";
import { Clip, Phone, footage } from "../footage";
import { C, MONO, PHONE_ASPECT, PIP_H, pill, ramp, rise } from "../theme";
import { T } from "../timeline";

const { beacon, dur } = T.report;
export const PIP = { right: 96, top: 136 }; // shared with Rescue so the phone sits in the same place
const pipW = PIP_H * PHONE_ASPECT;
const cx = 1920 - PIP.right - pipW / 2;
const cy = PIP.top + PIP_H / 2;

// Beat 3 · Panic, empathy. The real take, with the screen recording large enough to read.
export const Report = () => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill>
      <AbsoluteFill style={{ transform: `scale(${1.05 - 0.05 * ramp(f, 0, dur)})` }}>
        <Clip shot={footage.sosReportCam} style={{ filter: "saturate(.85) contrast(1.05)" }} />
      </AbsoluteFill>
      <AbsoluteFill style={{ background: "linear-gradient(90deg, rgba(4,9,19,.82) 0%, rgba(4,9,19,.45) 45%, rgba(4,9,19,.1) 70%)" }} />

      <Beacon f={f - beacon} />
      <Phone h={PIP_H} style={{ right: PIP.right, top: PIP.top, ...rise(f, 6, undefined, { dy: 60, dur: 18 }) }}>
        <Clip shot={footage.sosReportScreen} contain />
      </Phone>

      <div style={{ position: "absolute", left: 72, top: 150, display: "flex", flexDirection: "column", gap: 18, alignItems: "flex-start" }}>
        <div style={{ ...pill, fontSize: 30, padding: "16px 30px", borderColor: "rgba(127,214,208,.55)", ...rise(f, beacon) }}>
          <Dot f={f} /> Beacon on, <span style={{ color: C.teal }}>before the AI even finishes</span>
        </div>
        <div style={{ fontFamily: MONO, fontWeight: 600, fontSize: 21, color: C.mute, paddingLeft: 30, ...rise(f, beacon + 10) }}>
          SOS → 22-byte Bluetooth beacon · the model is never waited on
        </div>
        <div style={{ ...pill, marginTop: 18, ...rise(f, beacon + 70) }}>
          <span style={{ color: C.ai }}>●</span> Offline Whisper · understands Taglish, no signal
        </div>
      </div>
    </AbsoluteFill>
  );
};

const Dot = ({ f }: { f: number }) => (
  <span style={{ width: 16, height: 16, borderRadius: 8, background: C.teal, boxShadow: `0 0 ${10 + 8 * Math.sin(f / 4)}px ${C.teal}` }} />
);

// Bluetooth rings radiating from the phone once the beacon is on.
const Beacon = ({ f }: { f: number }) => {
  if (f < 0) return null;
  return (
    <>
      {[0, 1, 2].map((k) => {
        const p = ((f + k * 20) % 60) / 60;
        const r = 240 + p * 520;
        return (
          <div key={k} style={{
            position: "absolute", left: cx - r, top: cy - r, width: r * 2, height: r * 2, borderRadius: "50%",
            border: `3px solid ${C.teal}`, opacity: (1 - p) * 0.5 * ramp(f, 0, 10),
          }} />
        );
      })}
    </>
  );
};
