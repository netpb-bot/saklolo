import { loadFont as loadArchivo } from "@remotion/google-fonts/Archivo";
import { loadFont as loadMono } from "@remotion/google-fonts/JetBrainsMono";
import { Easing, interpolate } from "remotion";
import type { CSSProperties } from "react";

export const ARCHIVO = loadArchivo("normal", { weights: ["400", "500", "600", "700", "800", "900"], subsets: ["latin"] }).fontFamily;
export const ARCHIVO_I = loadArchivo("italic", { weights: ["500"], subsets: ["latin"] }).fontFamily;
export const MONO = loadMono("normal", { weights: ["400", "600", "700"], subsets: ["latin"] }).fontFamily;

// Design tokens from saklolo-film.html (:root).
export const C = {
  night: "#040913",
  deep: "#001233",
  navy: "#002D72",
  ice: "#D9E6FF",
  ink: "#EEF3FB",
  mute: "#8C9BB5",
  dim: "#4A5872",
  red: "#E8333D",
  amber: "#FFC53D",
  green: "#2FB36A",
  teal: "#7FD6D0",
  ai: "#B9A6FF",
  line: "rgba(217,230,255,.14)",
  scr: "#0A1222",
};

export const W = 1920;
export const H = 1080;

// Phone screen recordings (Galaxy S24: 1080×2340). Footage is letterboxed with "contain", never cropped,
// so the status bar (airplane icon) always stays in frame even if the real aspect differs.
export const PHONE_ASPECT = 1080 / 2340;
export const PIP_H = 720; // picture-in-picture height, two thirds of the frame

const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const easeOut = Easing.bezier(0.16, 1, 0.3, 1);
const easeIO = Easing.bezier(0.65, 0, 0.35, 1);

// 0→1 over [a, a+dur] with the film's ease-out.
export const ramp = (f: number, a: number, dur: number, ease = easeOut) => interpolate(f, [a, a + dur], [0, 1], { ...clamp, easing: ease });

// The film's signature move: lift + de-blur in at frame `a`, soft blur out at frame `b`.
export const rise = (f: number, a: number, b?: number, { dy = 36, dur = 16, out = 11 } = {}): CSSProperties => {
  const i = ramp(f, a, dur);
  const o = b == null ? 0 : ramp(f, b, out, easeIO);
  const blur = (1 - i) * 12 + o * 10;
  return {
    opacity: i * (1 - o),
    transform: `translateY(${(1 - i) * dy - o * 20}px)`,
    filter: blur > 0.05 ? `blur(${blur}px)` : undefined,
  };
};

export const kicker: CSSProperties = {
  fontFamily: MONO,
  fontWeight: 600,
  fontSize: 22,
  letterSpacing: "0.22em",
  textTransform: "uppercase",
  color: C.teal,
};

export const pill: CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  gap: 14,
  padding: "14px 26px",
  borderRadius: 999,
  border: `1.5px solid ${C.line}`,
  background: "rgba(10,20,40,.82)",
  fontFamily: MONO,
  fontWeight: 600,
  fontSize: 26,
  color: C.ice,
  whiteSpace: "nowrap",
};
