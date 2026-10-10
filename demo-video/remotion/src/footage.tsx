import { AbsoluteFill, Freeze, OffthreadVideo, getStaticFiles, staticFile, useCurrentFrame } from "remotion";
import type { CSSProperties } from "react";
import { C, MONO, ARCHIVO, PHONE_ASPECT } from "./theme";

// The one place that decides real footage vs slate. Drop a file into public/footage/ and it replaces its slate.
// `volume`: screen recordings keep the app's own audio (TTS, the report); camera audio is ambience.
export const footage = {
  hookNoService: { file: "footage/hook-no-service.mp4", beat: "1 · Hook", must: "Camera: dark room, a phone showing \"No service\", a 911 call failing", volume: 1 },
  airplaneOpen: { file: "footage/airplane-open.mp4", beat: "2 · Offline", must: "Screen rec: airplane mode turned on, then Saklolo opened to the SOS home", volume: 1 },
  sosReportCam: { file: "footage/sos-report-cam.mp4", beat: "3 · The report", must: "Camera: a hand holding SOS while the person speaks the report", volume: 0.35 },
  sosReportScreen: { file: "footage/sos-report-screen.mp4", beat: "3–4 · The report", must: "Screen rec of the same take, ending on the demo overlay (Keywords vs Saklolo)", volume: 1 },
  protocolMythScreen: { file: "footage/protocol-myth-screen.mp4", beat: "5 · Protocol + myth", must: "Screen rec: bleeding protocol, then the lighter question and its answer card", volume: 1 },
  rescuerBoardScreen: { file: "footage/rescuer-board-screen.mp4", beat: "7 · Rescuer", must: "Screen rec: RED at the top of the board, briefing read aloud, hot/cold meter", volume: 1 },
  rescuerWalkCam: { file: "footage/rescuer-walk-cam.mp4", beat: "7 · Rescuer", must: "Camera: the rescuer walking, then tapping Ring phone", volume: 0.25 },
  strobeDarkCam: { file: "footage/strobe-dark-cam.mp4", beat: "7 · Hero shot", must: "Camera: the victim phone strobing and chirping in the dark", volume: 1 },
  helpOnWayScreen: { file: "footage/help-on-way-screen.mp4", beat: "8 · Help", must: "Screen rec: \"On my way\" tapped, victim screen shows \"Help is on the way\"", volume: 1 },
};
export type Shot = (typeof footage)[keyof typeof footage];

export const has = (file: string) => getStaticFiles().some((f) => f.name.replace(/\\/g, "/") === file);

// A shot that fills its box: the real file if present, otherwise a slate naming the file and what it must show.
// `contain` letterboxes (screen recordings: never crop the status bar). `freeze` holds one frame of the clip.
export const Clip = ({ shot, contain, freeze, muted, startFrom, style }: {
  shot: Shot; contain?: boolean; freeze?: number; muted?: boolean; startFrom?: number; style?: CSSProperties;
}) => {
  if (!has(shot.file)) return <Slate shot={shot} portrait={contain} freeze={freeze} />;
  const video = (
    <OffthreadVideo
      src={staticFile(shot.file)}
      startFrom={startFrom}
      muted={muted || freeze != null}
      volume={shot.volume}
      style={{ width: "100%", height: "100%", objectFit: contain ? "contain" : "cover", ...style }}
    />
  );
  return freeze == null ? video : <Freeze frame={freeze}>{video}</Freeze>;
};

const Slate = ({ shot, portrait, freeze }: { shot: Shot; portrait?: boolean; freeze?: number }) => {
  const f = useCurrentFrame();
  const z = (p: number, l: number) => (portrait ? p : l); // phone-shaped box vs full frame
  return (
    <AbsoluteFill style={{
      background: `repeating-linear-gradient(135deg, #0B1426 0 22px, #0E182D 22px 44px)`,
      border: `2px dashed rgba(255,197,61,.45)`, justifyContent: "center", alignItems: "center",
      padding: "0 28px", textAlign: "center", color: C.ice, fontFamily: ARCHIVO,
    }}>
      <div style={{ fontFamily: MONO, fontWeight: 700, fontSize: z(17, 22), letterSpacing: "0.2em", color: C.amber, maxWidth: 1100 }}>
        FOOTAGE NEEDED · {shot.beat.toUpperCase()}
      </div>
      <div style={{ fontFamily: MONO, fontWeight: 700, fontSize: z(24, 40), marginTop: "1.2em", wordBreak: "break-all", color: C.ink, maxWidth: 1100 }}>
        public/{shot.file}
      </div>
      <div style={{ fontWeight: 500, fontSize: z(24, 32), lineHeight: 1.35, marginTop: "1em", color: C.mute, maxWidth: 1100 }}>
        {shot.must}
      </div>
      <div style={{ fontFamily: MONO, fontSize: z(16, 20), marginTop: "2em", color: C.dim }}>
        {freeze != null ? `HOLD · clip frame ${freeze}` : `clip frame ${f}`}
      </div>
    </AbsoluteFill>
  );
};

// A phone screen recording as a floating rounded panel. Sized by height; width follows the phone aspect.
export const Phone = ({ h, style, children }: { h: number; style?: CSSProperties; children: React.ReactNode }) => (
  <div style={{
    position: "absolute", height: h, width: h * PHONE_ASPECT, borderRadius: h * 0.045, overflow: "hidden", background: "#000",
    boxShadow: "0 40px 110px rgba(0,0,0,.65), 0 0 0 1.5px rgba(217,230,255,.18)", ...style,
  }}>
    {children}
  </div>
);
