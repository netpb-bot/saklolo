import { AbsoluteFill, Sequence, useCurrentFrame } from "remotion";
import { Clip, Phone, footage } from "../footage";
import { C, MONO, PIP_H, pill, ramp, rise } from "../theme";
import { T } from "../timeline";
import { PIP } from "./Report";

const { silence, strobe, dur } = T.rescue;

// Beat 7 · Hope. The board reads RED aloud; the rescuer walks the signal, taps Ring phone.
// One second of silence, then a hard cut to the phone strobing in the dark.
export const Rescue = () => {
  const f = useCurrentFrame();
  const hush = ramp(f, silence, strobe - silence, (x) => x); // tension: slow push-in while everything goes quiet
  return (
    <AbsoluteFill>
      <Sequence durationInFrames={strobe}>
        <AbsoluteFill style={{ transform: `scale(${1 + 0.08 * hush})` }}>
          <Sequence durationInFrames={silence}><Clip shot={footage.rescuerWalkCam} style={{ filter: "saturate(.8) contrast(1.06)" }} /></Sequence>
          <Sequence from={silence}><Clip shot={footage.rescuerWalkCam} startFrom={silence} muted style={{ filter: "saturate(.8) contrast(1.06)" }} /></Sequence>
        </AbsoluteFill>
        <AbsoluteFill style={{ background: `linear-gradient(90deg, rgba(4,9,19,.8) 0%, rgba(4,9,19,.4) 45%, rgba(4,9,19,.08) 70%), rgba(0,0,0,${0.35 * hush})` }} />

        <Sequence durationInFrames={silence}>
          <Phone h={PIP_H} style={{ right: PIP.right, top: PIP.top, ...rise(f, 4, silence - 12, { dy: 60, dur: 18 }) }}>
            <Clip shot={footage.rescuerBoardScreen} contain />
          </Phone>
        </Sequence>

        <div style={{ position: "absolute", left: 72, top: 150, display: "flex", flexDirection: "column", gap: 20, alignItems: "flex-start" }}>
          <div style={{ ...pill, fontSize: 30, ...rise(f, 10, silence - 8) }}>
            <span style={{ padding: "2px 12px", borderRadius: 8, background: C.red, color: "#fff", fontWeight: 800 }}>RED</span> on top · ranked by rule, not AI
          </div>
          <div style={{ ...pill, fontSize: 30, ...rise(f, 112, silence - 8) }}>
            <span style={{ color: C.teal }}>◉</span> No GPS under rubble → follow the Bluetooth signal
          </div>
          <div style={{ ...pill, fontSize: 30, borderColor: "rgba(255,197,61,.6)", ...rise(f, 150, silence - 8) }}>
            <span style={{ color: C.amber }}>●</span> Ring phone: chirp + flashlight strobe
          </div>
        </div>
      </Sequence>

      <Sequence from={strobe}>
        <Clip shot={footage.strobeDarkCam} />
        <div style={{ position: "absolute", left: 72, bottom: 64, fontFamily: MONO, fontWeight: 600, fontSize: 22, letterSpacing: "0.18em", color: C.ice, textShadow: "0 2px 14px #000", ...rise(f, strobe + 36, dur - 6, { dy: 14 }) }}>
          THE VICTIM'S PHONE · RUNG BY THE RESCUER
        </div>
      </Sequence>
    </AbsoluteFill>
  );
};
