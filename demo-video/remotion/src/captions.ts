// Burned-in captions, in absolute frames (30 fps). `text` is what is heard; `en` is the English subtitle
// shown under Filipino/Taglish speech. Until audio/vo.wav exists, these lines carry the voice-over.
export type Caption = { from: number; to: number; text: string; en?: string; who?: string };

export const captions: Caption[] = [
  // 1 · hook
  { from: 12, to: 92, text: "When the Big One hits, the towers go first.", who: "VO" },
  { from: 96, to: 196, text: "911 can't answer.", who: "VO" },
  // 2 · intro
  { from: 222, to: 290, text: "Saklolo is a 911 dispatcher that lives on your phone.", who: "VO" },
  { from: 292, to: 352, text: "No signal needed.", who: "VO" },
  // 3 · the real report (Demo.REPORT in MainActivity.kt; English from PRD §11)
  { from: 400, to: 488, text: "Tulong! Gumuho ang bahay, naipit kami sa ikalawang palapag, sa may banyo.", en: "Help! The house collapsed, we're trapped on the second floor, by the bathroom.", who: "CALLER" },
  { from: 490, to: 560, text: "Tatlo kami, ay hindi, apat pala, kasama si lola.", en: "Three of us, no wait, four, including grandma.", who: "CALLER" },
  { from: 562, to: 652, text: "Hindi ko maigalaw ang binti ko, at ang daming dugo sa ulo ng anak ko!", en: "I can't move my leg, and there's so much blood on my child's head!", who: "CALLER" },
  // 4 · the aha
  { from: 672, to: 760, text: "A keyword list hears three people, no injuries.", who: "VO" },
  { from: 762, to: 852, text: "The on-device AI hears four, the bleeding, the floor and the room.", who: "VO" },
  { from: 854, to: 924, text: "And it can't be cloud AI. The cloud went down with the towers.", who: "VO" },
  // 5 · protocol + myth (protocols.json: bl_press, faq "lighter")
  { from: 940, to: 1008, text: "Press hard on the wound with a cloth.", who: "PHONE" },
  { from: 1014, to: 1066, text: "Pwede bang sindihan ang lighter para makakita?", en: "Can I light a lighter so I can see?", who: "CALLER" },
  { from: 1070, to: 1136, text: "No. Never light a lighter, match or candle. Gas may be leaking and one spark can explode.", who: "PHONE" },
  // 6 · 22 bytes
  { from: 1150, to: 1284, text: "The AI squeezes the whole emergency into 22 bytes, small enough for a Bluetooth beacon.", who: "VO" },
  // 7 · rescuer (Rescuer.kt briefing())
  { from: 1300, to: 1404, text: "Red. Collapse. 4 people, trapped, including a child and an elderly person. 2nd floor, bathroom.", who: "PHONE" },
  { from: 1408, to: 1482, text: "No GPS under rubble. Follow the signal, then make it ring.", who: "VO" },
  // 8 · help
  { from: 1612, to: 1694, text: "Every safe phone becomes a rescuer. Bayanihan, with no towers.", who: "VO" },
];
