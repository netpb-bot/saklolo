# Saklolo demo reel: script and shoot sheet (60 s)

Matches `remotion/src/timeline.ts` and `remotion/src/captions.ts`. If you change a line here, change it in `captions.ts` too.

**Cast:** VO (calm, low, unhurried) · CALLER (the person trapped, panicked, speaks Taglish) · RESCUER (no lines, just walks and taps) · PHONE (the app's own TTS, recorded live from the screen recording, never re-voiced).

**Before every take:** airplane mode ON, Bluetooth ON, both phones charged, English on both phones, model loaded, volume up, Do Not Disturb off. Never crop the status bar: the airplane icon is the proof.

---

## 1 · Hook (0:00–0:07) · Dread
**Picture:** a dark room. A phone shows "No service". A thumb dials 911 and the call fails. Then the graphic: **M7.2 · ~34,000 lives** · *MMEIRS 2004*.
**Sound:** heartbeat drone. The real "call failed" tone from the phone.

> **VO:** When the Big One hits, the towers go first.
> *(beat)*
> **VO:** 911 can't answer.

*Direction: let "can't answer" land on the failed-call tone. Don't rush the silence after it.*

## 2 · Intro (0:07–0:12) · Curiosity
**Picture:** screen recording. Swipe down, tap airplane mode, open Saklolo to the SOS home. Then the logo lands (~1 s).

> **VO:** Saklolo is a 911 dispatcher that lives on your phone. No signal needed.

*Direction: a small lift on "No signal needed". That's the promise.*

## 3 · The report (0:12–0:22) · Panic, empathy
**Picture:** camera on a hand holding SOS in a dim, dusty space. Picture-in-picture: the same take's screen recording. Callout when the beacon pill appears: **"Beacon on, before the AI even finishes."**
**Sound:** the CALLER's real voice, recorded on the phone. Subtitles carry the English.

> **CALLER** *(breathless, fast, cracking)*: Tulong! Gumuho ang bahay, naipit kami sa ikalawang palapag, sa may banyo.
> *(EN: Help! The house collapsed, we're trapped on the second floor, by the bathroom.)*
>
> **CALLER** *(correcting themself mid-panic)*: Tatlo kami, ay hindi, apat pala, kasama si lola.
> *(EN: Three of us, no wait, four, including grandma.)*
>
> **CALLER** *(breaking)*: Hindi ko maigalaw ang binti ko, at ang daming dugo sa ulo ng anak ko!
> *(EN: I can't move my leg, and there's so much blood on my child's head!)*

*Direction: this must sound real, not read. The "ay hindi, apat pala" correction is the point of the next beat, so make it audible. Say it word for word: it's `Demo.REPORT`. If the hall is noisy, use the demo menu's "Report" injection; it runs the same model.*

## 4 · The aha (0:22–0:31) · "That's why it's AI"
**Picture:** freeze on the demo overlay. A red ✗ over **Keywords: 3 people · no injuries**. A teal ✓ over **Saklolo: 4 · pinned · heavy bleeding · child · elderly · Floor 2, bathroom → RED** (label: *triage rule*). Chip: **On-device AI · EmbeddingGemma 300M · offline**.

> **VO:** A keyword list hears three people, no injuries.
> **VO:** The on-device AI hears four, the bleeding, the floor and the room.
> **VO:** And it can't be cloud AI. The cloud went down with the towers.

*Direction: the most important 9 seconds. Slow down on "four, the bleeding, the floor and the room", one item per beat. Hit "can't" in the last line. This is the densest VO (~36 words in 9 s); if it feels rushed, cut "And" and "the room".*

## 5 · Protected (0:31–0:38) · Not alone
**Picture:** screen recording. The bleeding protocol appears; the caller asks the myth question; the answer card appears.
**Sound:** the phone's own voice. No VO in this beat.

> **PHONE:** Press hard on the wound with a cloth.
> **CALLER** *(quieter, scared)*: Pwede bang sindihan ang lighter para makakita?
> *(EN: Can I light a lighter so I can see?)*
> **PHONE:** No. Never light a lighter, match or candle. Gas may be leaking and one spark can explode.

*Direction: let the phone speak with nothing over it. A real device talking someone through it is the proof.*

## 6 · 22 bytes (0:38–0:43) · Clever
**Picture:** graphic. The report text collapses into the 22 real bytes, which split into labeled fields (id · lat/lon *no GPS fix* · kind · people · vuln · cond · place · battery · rev · age).

> **VO:** The AI squeezes the whole emergency into 22 bytes, small enough for a Bluetooth beacon.

## 7 · The rescue (0:43–0:53) · Hope
**Picture:** RESCUER walking (camera) with the rescuer board in PiP: RED at the top, briefing playing, hot/cold meter warming. RESCUER taps **Ring phone**. **~1 s of black silence**, then a hard cut to the victim phone strobing and chirping in the dark (hero shot).
**Sound:** the phone briefing, then VO, then silence, then the chirp.

> **PHONE:** Red. Collapse. 4 people, trapped, including a child and an elderly person. 2nd floor, bathroom.
> **VO:** No GPS under rubble. Follow the signal, then make it ring.
> *(1 s silence)*
> *(strobe + chirp, no words)*

*Direction: protect the silence. The chirp after it is the emotional peak of the reel.*

## 8 · Help (0:53–1:00) · Relief
**Picture:** RESCUER taps **On my way**. The victim screen turns green: **Help is on the way** (hold ~1 s, still in airplane mode). End card: logo · **Works with zero bars** · tagline · *Hackathon prototype · demo, not medically reviewed · MMEIRS 2004*.
**Sound:** music resolves.

> **VO:** Every safe phone becomes a rescuer. Bayanihan, with no towers.

*Direction: warm, almost a smile on "Bayanihan". Then let the music finish.*

---

## Recording notes
- **VO total:** ~85 words. Record each line as its own take, with 2 s of room tone. Export `public/audio/vo.wav`.
- **Caller:** record on the phone itself (it is the real input), in a quiet room. Do 3 takes: panicked, more panicked, quieter. Pick the most believable, not the clearest.
- **Shot order** (fastest to shoot): 2 → 3 → 5 → 7 (board + walk) → 8 → 7 (strobe, needs a dark room) → 1 (dark room again).
- **Filenames:** see the footage table in `REMOTION_PROMPT.md`. Drop the clips into `remotion/public/footage/` and the slates disappear.
