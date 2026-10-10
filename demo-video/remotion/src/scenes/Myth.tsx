import { AbsoluteFill, useCurrentFrame } from "remotion";
import { Clip, Phone, footage } from "../footage";
import { C, MONO, kicker, ramp, rise } from "../theme";
import { T } from "../timeline";

const { ask, match, answer, dur } = T.myth;

// What happens, in order: the phone speaks the protocol, a myth is asked, the AI only matches it to approved text.
const STEPS = [
  { at: 4, title: "Reads the protocol aloud", sub: "Heavy bleeding · step 1 · fixed steps from protocols.json" },
  { at: ask, title: "A myth question, in Taglish", sub: "“Pwede bang sindihan ang lighter?”" },
  { at: match, title: "EmbeddingGemma picks the closest approved question", sub: "faq: lighter · or nothing, never a guess" },
  { at: answer, title: "The answer was written by people", sub: "The AI picks it from a closed list. It never writes it." },
];

// Beat 5 · Protected.
export const Myth = () => {
  const f = useCurrentFrame();
  const active = STEPS.reduce((a, s, i) => (f >= s.at ? i : a), 0);
  const fill = ramp(f, 4, answer + 10 - 4, (x) => x);
  return (
    <AbsoluteFill>
      <Phone h={880} style={{ right: 96, top: 136, ...rise(f, 0, undefined, { dy: 40, dur: 16 }) }}>
        <Clip shot={footage.protocolMythScreen} contain />
      </Phone>

      <div style={{ position: "absolute", left: 120, top: 150, width: 1060 }}>
        <div style={{ ...kicker, ...rise(f, 2) }}>Survival steps, then the myths</div>
        <div style={{ position: "relative", marginTop: 40 }}>
          <div style={{ position: "absolute", left: 17, top: 24, bottom: 40, width: 3, background: C.line }} />
          <div style={{ position: "absolute", left: 17, top: 24, width: 3, height: `calc((100% - 64px) * ${fill})`, background: C.teal }} />
          {STEPS.map((s, i) => {
            const on = i === active;
            return (
              <div key={i} style={{ position: "relative", display: "flex", gap: 34, marginBottom: 34, ...rise(f, s.at) }}>
                <div style={{ flex: "none", width: 38, height: 38, borderRadius: 19, marginTop: 8, background: on ? C.teal : C.night, border: `3px solid ${i <= active ? C.teal : C.dim}`, boxShadow: on ? `0 0 26px ${C.teal}` : undefined }} />
                <div style={{ opacity: on ? 1 : 0.55 }}>
                  <div style={{ fontWeight: 800, fontSize: 44, lineHeight: 1.1, letterSpacing: "-0.015em" }}>{s.title}</div>
                  <div style={{ fontFamily: i === 1 ? undefined : MONO, fontStyle: i === 1 ? "italic" : undefined, fontWeight: 500, fontSize: i === 1 ? 32 : 22, color: i === 1 ? C.amber : C.mute, marginTop: 8 }}>{s.sub}</div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </AbsoluteFill>
  );
};
