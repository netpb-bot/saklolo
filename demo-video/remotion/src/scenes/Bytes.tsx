import { AbsoluteFill, interpolate, interpolateColors, useCurrentFrame } from "remotion";
import { C, MONO, ramp, rise } from "../theme";
import { T } from "../timeline";

const { collapse, split, dur } = T.bytes;

// About 200 spoken words: the report, the follow-up questions (protocols.json wording) and the answers.
// Words prefixed with * become facts in the record.
const TRANSCRIPT = `SAKLOLO What happened?
CALLER Tulong! *Gumuho ang bahay, *naipit kami sa *ikalawang *palapag, sa may *banyo. Tatlo kami, ay hindi, *apat pala, kasama si *lola. Hindi ko *maigalaw ang *binti ko, at ang daming *dugo sa ulo ng *anak ko!
SAKLOLO Press hard on the wound with a cloth. Use both hands and your body weight. Don't lift the cloth to look.
CALLER Pwede bang sindihan ang lighter para makakita?
SAKLOLO No. Never light a lighter, match or candle. Gas may be leaking and one spark can explode. Use your phone's light.
SAKLOLO How many of you are there?
CALLER *Apat kami.
SAKLOLO Can you get out?
CALLER Hindi, may pader na bumagsak sa pinto, hindi namin mabuksan. *Nakadagan pa rin ang kabinet sa binti ko.
SAKLOLO Is blood soaking through the cloth?
CALLER Oo, *tumatagos pa rin.
SAKLOLO Put more cloth on top and press harder. Leave the first cloth where it is.
SAKLOLO Which floor, and which part of the building?
CALLER *Ikalawang palapag, sa *banyo, sa likod ng bahay.
SAKLOLO Is anyone hurt or pinned?
CALLER Si lola, nanghihina na pero gising pa. Ang anak ko, pitong taon, umiiyak pero gising. Mahina na ang *battery ko, bilisan po ninyo, please!`
  .split("\n").map((line) => { const [who, ...w] = line.split(" "); return { who, words: w }; });

// The real record, Record.encode() (saklolo-film.html "the real record"), grouped as hexFields() in MainActivity.kt.
const BY = ["10", "5A", "4B", "1C", "07", "80", "00", "00", "00", "80", "00", "00", "00", "0A", "04", "06", "0C", "21", "4E", "02", "00", "B4"];
const FIELDS: [n: number, name: string, value: string, color: string][] = [
  [1, "ver · hop", "v1 · 0", C.mute],
  [4, "id", "5A4B1C07", C.mute],
  [4, "lat", "NO_FIX", C.teal],
  [4, "lon", "NO_FIX", C.teal],
  [1, "kind", "collapse\nRED (rule)\ntrapped", C.red],
  [1, "people", "4", C.ice],
  [1, "vuln", "child\nelderly", C.amber],
  [1, "cond", "pinned\nbleeding", C.red],
  [1, "place", "floor 2\nbathroom", C.ai],
  [1, "battery", "78%", C.green],
  [1, "rev", "2", C.mute],
  [2, "age", "180 min", C.mute],
];
const CELL = 60, GAP = 6, GROUP = 28, ROW_Y = 500, CH = 92;

// Beat 6 · Clever. The conversation keeps only its facts, then folds into 22 real bytes.
export const Bytes = () => {
  const f = useCurrentFrame();
  const squeeze = ramp(f, collapse, 26);
  const g = ramp(f, split, 18);

  // cell x positions: groups drift apart as g goes 0 → 1
  const xs: number[] = [];
  let x = 0;
  FIELDS.forEach(([n], gi) => { for (let k = 0; k < n; k++) { xs.push(x); x += CELL + GAP; } x += GROUP * g * (gi < FIELDS.length - 1 ? 1 : 0); });
  const width = x - GAP;
  const x0 = (1920 - width) / 2;
  let wi = 0;

  return (
    <AbsoluteFill>
      <div style={{ position: "absolute", top: 120, width: "100%", display: "flex", justifyContent: "center", alignItems: "baseline", gap: 36, fontWeight: 900, fontSize: 84, letterSpacing: "-0.03em" }}>
        <span style={{ color: C.mute, ...rise(f, 2) }}>~200 words</span>
        <span style={{ fontFamily: MONO, fontWeight: 700, fontSize: 60, color: C.dim, ...rise(f, collapse + 12) }}>→</span>
        <span style={{ color: C.teal, ...rise(f, collapse + 18) }}>22 bytes</span>
      </div>

      {squeeze < 1 && (
        <div style={{
          position: "absolute", left: 180, top: 250, width: 1560, fontSize: 25, lineHeight: 1.55, color: C.ice,
          transformOrigin: `50% ${ROW_Y + CH / 2 - 250}px`,
          ...(squeeze > 0
            ? { transform: `scale(${interpolate(squeeze, [0, 1], [1, 0.06])}, ${interpolate(squeeze, [0, 1], [1, 0.02])})`, opacity: 1 - ramp(f, collapse + 8, 18), filter: `blur(${squeeze * 8}px)` }
            : rise(f, 0, undefined, { dy: 20, dur: 12 })),
        }}>
          {TRANSCRIPT.map(({ who, words }, li) => (
            <span key={li}>
              <span style={{ fontFamily: MONO, fontWeight: 700, fontSize: 17, letterSpacing: "0.12em", color: who === "CALLER" ? C.amber : C.teal, marginRight: 10 }}>{who}</span>
              {words.map((w, i) => {
                const key = w.startsWith("*");
                const at = 10 + (wi++ % 24) * 0.8;
                const hl = ramp(f, at, 8);
                return (
                  <span key={i} style={{
                    marginRight: "0.28em", color: key ? interpolateColors(hl, [0, 1], [C.ice, C.teal]) : C.ice, opacity: key ? 1 : 1 - 0.72 * hl,
                    fontWeight: key ? 700 : 400, textShadow: key ? `0 0 ${18 * hl}px rgba(127,214,208,.8)` : undefined,
                  }}>{key ? w.slice(1) : w}</span>
                );
              })}
              {li < TRANSCRIPT.length - 1 && <span style={{ marginRight: "0.9em" }} />}
            </span>
          ))}
        </div>
      )}

      {BY.map((b, i) => {
        const fromCenter = Math.abs(i - 10.5);
        const a = collapse + 12 + fromCenter * 1.2;
        const p = ramp(f, a, 12);
        let gi = 0, acc = 0;
        while (acc + FIELDS[gi][0] <= i) { acc += FIELDS[gi][0]; gi++; }
        const color = FIELDS[gi][3];
        return (
          <div key={i} style={{
            position: "absolute", left: x0 + xs[i], top: ROW_Y, width: CELL, height: CH, borderRadius: 12, display: "grid", placeItems: "center",
            fontFamily: MONO, fontWeight: 700, fontSize: 28, color: C.ink, background: `rgba(217,230,255,${0.06 + 0.05 * g})`,
            border: `2px solid ${g > 0 ? mix(color, g) : C.line}`, opacity: p, transform: `scale(${1.5 - 0.5 * p})`, filter: p < 1 ? `blur(${(1 - p) * 10}px)` : undefined,
            boxShadow: p > 0 && p < 1 ? `0 0 30px rgba(127,214,208,${0.6 * (1 - p)})` : undefined,
          }}>{b}</div>
        );
      })}

      {FIELDS.map(([n, name, value, color], gi) => {
        const start = FIELDS.slice(0, gi).reduce((s, fl) => s + fl[0], 0);
        const left = x0 + xs[start];
        const w = xs[start + n - 1] + CELL - xs[start];
        const up = gi % 2 === 1;
        const st = rise(f, split + 8 + gi * 2.5, undefined, { dy: up ? -18 : 18, dur: 12 });
        const lines = value.split("\n");
        return (
          <div key={gi} style={{
            position: "absolute", left: left + w / 2 - 110, width: 220, textAlign: "center", ...st,
            ...(up ? { bottom: 1080 - ROW_Y + 16 } : { top: ROW_Y + CH + 16 }),
            display: "flex", flexDirection: up ? "column-reverse" : "column", alignItems: "center", gap: 8,
          }}>
            <div style={{ width: w, height: 3, borderRadius: 2, background: color }} />
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
              <div style={{ fontFamily: MONO, fontWeight: 600, fontSize: 16, letterSpacing: "0.1em", textTransform: "uppercase", color: C.mute }}>{name}</div>
              {lines.map((l) => <div key={l} style={{ fontFamily: MONO, fontWeight: 700, fontSize: 21, lineHeight: 1.2, color: color === C.mute ? C.ice : color }}>{l}</div>)}
            </div>
          </div>
        );
      })}

      <div style={{ position: "absolute", top: 262, width: "100%", textAlign: "center", fontFamily: MONO, fontWeight: 600, fontSize: 20, letterSpacing: "0.06em", color: C.dim, ...rise(f, split + 40, dur - 6) }}>
        Record.encode() · the real bytes of this report · no names
      </div>
    </AbsoluteFill>
  );
};

const mix = (hex: string, a: number) => {
  if (!hex.startsWith("#")) return hex;
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${(0.3 + 0.55 * a).toFixed(2)})`;
};
