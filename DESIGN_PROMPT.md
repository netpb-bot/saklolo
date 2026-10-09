I'm building **Saklolo**, an Android app for a 10-hour solo hackathon, and I need a design language and app UI/UX that I can build quickly in Jetpack Compose with Material 3. I've attached PRD.md, SPEC.md and demo.html (a rough interactive prototype). The PRD wins wherever these disagree with this brief. Use demo.html to understand the flows only, not as a visual reference: its look is a throwaway.

## The product in one paragraph
When a typhoon takes down the cell towers in the Philippines, no one answers 911. Saklolo is an offline 911 dispatcher that runs entirely on the phone. A local AI model listens to a panicked report in Taglish (mixed Tagalog and English). It asks for what's missing, guides first aid until help arrives, and compresses the emergency into a 20-byte Bluetooth beacon. Rescuers nearby see every beacon ranked on a dispatch board, hear a spoken briefing, follow an arrow or a hot/cold meter to the victim, and tap "Papunta na" (on my way). The victim's phone then shows "May paparating na tulong" (help is on the way). There's no signal, no cloud and no account at any point.

## Who uses it, and in what conditions
Design for these conditions, not for a calm user at a desk.

**The survivor (primary user).** They may be on a roof in a flood, at night, in rain, panicking, with wet hands, possibly holding a child, often on a low-end Android phone with a cracked screen and little battery left. They may be doing CPR while talking to the phone. They speak Filipino or Taglish first and may have low literacy. They have never opened the app before this moment.

**The rescuer (receiving end).** A barangay tanod, a Coast Guard crew member or an MDRRMO volunteer in a boat, in daylight glare or at night, possibly wearing gloves, with hands busy steering or carrying people. They have a power bank. They need to decide in seconds which of many roofs to go to first.

**Judges on demo day (secondary audience).** Two phones are projected on a screen. The design has to make the hidden parts visible: that the AI runs on the phone, what it understood, and the 20 bytes going out.

## What I need from you
1. **A design language for Saklolo.** Give it a short name and 3–4 principles that follow from the conditions above. For example: calm under panic, one thing at a time, voice first and buttons always there, never let colour carry the meaning alone.
2. **Design tokens, mapped to Material 3** so I can drop them into a Compose theme:
   - Colour roles for a victim theme and a rescuer theme, each in light and dark.
   - A type scale.
   - Spacing, shape and elevation.
   - Motion: durations, and which few things animate.
   - Haptic patterns, such as SOS confirmed, a question asked, ACK received.
3. **A small component set.** The fewer custom components the better: I have 10 hours. Tell me which ones are plain Material 3 components with theming and which are genuinely custom.
4. **Screens and their states** (listed below) at 360 × 800 dp, with real Filipino copy and English underneath or as a toggle.
5. **A short handoff note:** the top 5 decisions a developer must not get wrong, and what to cut first if I run out of time.

## Hard constraints
- **Triage colours are reserved.** RED, YELLOW and GREEN mean triage and nothing else. Brand and UI colours can never be confused with them, especially red. Every triage colour comes with its word and a distinct shape or icon, because some rescuers will be colour-blind and screens get washed out in sunlight.
- **Contrast and touch targets.** Text meets at least WCAG AA, and higher for the victim's key actions. Victim touch targets are at least 64 dp and rescuer targets at least 56 dp. Everything has to be usable with a wet thumb and with system font scaling at 130%.
- **Filipino first.** Filipino strings run about 30% longer than English, so layouts must survive that. Keep sentences short, imperative and in plain words, as a calm dispatcher would say them, not clinical language.
- **Voice first, buttons always.** Every question can be answered by voice, but YES / NO / UNSURE buttons are always visible, and the incident tiles are always available as a fallback. The mic needs clear states: idle, listening, thinking on the phone, and understood.
- **Show what the AI understood.** After every utterance, show what the phone understood as facts, never as a chat transcript. Example: "Naintindihan ko: HINDI" (I understood: NO), with a one-tap correction. Approved myth-buster answers are visibly marked as approved text, not AI writing.
- **Battery and darkness.** The victim theme should work in a dark room and drain little on OLED screens: a dark-first victim UI is welcome. The rescuer theme must stay readable in direct sunlight.
- **Buildable in Compose in hours.** Use stock Material 3 wherever it works. No custom illustrations, no maps, no onboarding, no settings screens, no animations that need a library.
- **Safety copy.** Say "Demo, not medically reviewed" somewhere unobtrusive. Put a "Tumawag sa 911" (call 911) button on every screen that doesn't compete with SOS.

## Screens to design

**Victim**
1. **Home / SOS:** a single huge hold-to-send SOS button, a short line explaining that the beacon starts right away even without signal, a GPS state, and a model state ("AI loading…" vs. ready; SOS works either way).
2. **3-second cancel** after the hold.
3. **"Ano ang nangyari?"** (What happened?) mic-first report screen, with the tiles as fallback: Nalunod (drowning), Kinagat ng hayop (animal bite), Lumusong sa baha (waded through floodwater), Naiipit (trapped).
4. **The status card that stays at the top of every screen:** triage colour, what happened, headcount, trapped or not, vulnerable people (infant, elderly, pregnant, PWD, child), danger signs, revision number and beacon status. Unknown facts must look unknown, not empty.
5. **A follow-up question** from the dispatcher, e.g. "Kaya n'yo bang lumikas?" (Can you get out?), at most 2 per report. **If someone isn't breathing, the CPR step stays on top and the question waits below it, marked "answer when you can".**
6. **A first-aid step**, read aloud, with a big Next button. Design the CPR step specifically: it may run for minutes, so it must be readable at arm's length and must not time out.
7. **A first-aid question** with YES / NO / UNSURE, free-answer voice, and the "Naintindihan ko" confirmation. Include the case where UNSURE took the safer branch.
8. **Myth-buster:** ask a question, then show either an approved answer or the "I can't answer that safely" state.
9. **"May paparating na tulong"** (help is on the way): the emotional payoff. It must be unmissable without breaking the CPR flow.
10. **Rescued / beacon stopped.**
11. **Edge states:** no GPS fix, model failed so tiles only, low battery, and the validator rejecting the AI output ("Hindi sigurado ang AI. Pumili sa ibaba.", The AI wasn't sure. Pick below).

**Rescuer**
1. **The dispatch board:** a ranked list (RED › YELLOW › GREEN, then vulnerable people, then oldest report). Each row shows headcount, flags, distance and bearing, report age, and "last heard N min ago". Include special treatments for **GETTING WORSE**, **ON THE WAY**, and **SILENT** (a phone not heard for 10 minutes or more, whose last location is still kept).
2. **Record detail:**
   - the spoken briefing text with a replay button
   - a big arrow and distance, or a hot/cold meter shown as 4 bands (Malamig cold, Maligamgam warm, Mainit hot, Nandito na right here) when there is no GPS
   - the actions "Papunta na" (on my way) and "Nakuha na" (rescued)
3. **Empty / scanning state.**

**Demo overlay (judges only, behind a hidden toggle).** This is a projection-friendly layer showing:
- the on-device model badge
- the last AI call: input, closed-list output, validator pass or fail
- a side-by-side "Dictionary vs. Gemma" result for the last report
- the 20 bytes as coloured cells

It must never appear in the normal victim flow, because a panicking user shouldn't see a comparison table.

**Eval screen (developer):** a simple table of dictionary score vs. model score vs. target for three test sets, plus median latency.

## Tone references
A calm, competent dispatcher's voice. Philippine context without stereotypes: no flags, no jeepney clip art, no stock disaster photos. Visual honesty: nothing should look more connected or more certain than it is.

## Please avoid
Chat bubbles, which make the product feel like a chatbot when it's a dispatcher. Also avoid:
- gradients or decoration on the victim screens
- red used for anything other than RED triage and the 911 button
- tiny secondary text for anything safety-relevant
- generic "AI sparkle" iconography
- placeholder Lorem ipsum (use the real copy from the PRD)

Start by proposing the design language and the tokens, then the victim flow, then the rescuer flow, then the demo overlay. Tell me where you think my product flow itself is wrong or confusing for a panicking user: I'd rather fix it now than after building.
