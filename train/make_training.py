"""Generates train/reports.jsonl: labeled reports assembled from phrase banks, then roughened like real messages.

Labels come with the parts, so every generated report is labeled exactly. The frozen test sets (eval/real.json,
eval/hard.json, by:"others" in eval/extract.json) are never used here; don't copy sentences from them.
"""
import json
import random
from pathlib import Path

random.seed(7)
HERE = Path(__file__).parent

# People: (phrase, vulnerable labels). {p} in condition phrases is filled with one of these.
PERSONS = [
    ("si lola", ["ELDERLY"]), ("si lolo", ["ELDERLY"]), ("my grandmother", ["ELDERLY"]), ("my grandpa", ["ELDERLY"]),
    ("yung matanda naming kapitbahay", ["ELDERLY"]), ("my 80 year old dad", ["ELDERLY"]), ("si nanay na senior", ["ELDERLY"]),
    ("yung baby ko", ["INFANT"]), ("ang sanggol", ["INFANT"]), ("my newborn", ["INFANT"]), ("our 6 month old", ["INFANT"]),
    ("anak ko", ["CHILD"]), ("yung bata", ["CHILD"]), ("my son", ["CHILD"]), ("my little girl", ["CHILD"]), ("ang pamangkin ko", ["CHILD"]),
    ("my 5 year old", ["CHILD"]), ("yung bunso namin", ["CHILD"]),
    ("buntis kong asawa", ["PREGNANT"]), ("my pregnant wife", ["PREGNANT"]), ("si ate na buntis", ["PREGNANT"]),
    ("kapatid kong PWD", ["PWD"]), ("tatay kong naka-wheelchair", ["PWD"]), ("my deaf brother", ["PWD"]), ("yung bulag naming kasama", ["PWD"]),
    ("si kuya", []), ("my brother", []), ("si papa", []), ("my husband", []), ("yung kasama ko", []), ("si ate", []),
    ("my friend", []), ("my mom", []),
    # Plain family words with no vulnerable label: without them "my wife" leaned PREGNANT and "nanay" ELDERLY.
    ("my wife", []), ("asawa ko", []), ("si nanay", []), ("nanay ko", []), ("my mother", []), ("si mama", []), ("si tito", []), ("my coworker", []), ("yung kapitbahay", []), ("someone", []),
]
SELF = [("ako", []), ("I", [])]

HAZARD = {
    "COLLAPSE": ["gumuho ang bahay", "the house collapsed", "nagiba yung bahay namin", "bumagsak ang bubong sa amin", "the roof caved in",
                 "lumindol at bumigay ang pader", "nag-collapse yung building", "earthquake, everything fell", "bumagsak ang mga hollow blocks",
                 "the ceiling came down after the quake", "malakas na lindol, sira ang bahay", "our school building collapsed",
                 "bumagsak ang pader pagkatapos ng lindol", "the stairs and walls fell in", "lumindol nang malakas", "an earthquake hit",
                 "nag-crack at bumagsak ang dingding", "the mall ceiling fell", "gumuho ang simbahan", "bumagsak ang second floor"],
    "FLOOD": ["baha na dito", "the flood is waist deep", "lubog na ang bahay", "pumasok ang baha", "the water is up to our necks",
              "hanggang bewang na ang tubig", "flash flood sa amin", "storm surge hit our village", "umapaw ang ilog", "the river overflowed",
              "abot dibdib na ang baha", "our street is under water", "binaha kami", "tubig na lahat sa baba", "the typhoon flooded everything",
              "daluyong sa tabing dagat", "malalim na ang baha sa labas", "rumaragasa ang tubig"],
    "LANDSLIDE": ["nagkaroon ng landslide", "a landslide buried our house", "natabunan ng lupa ang bahay", "gumuho ang bundok sa likod namin",
                  "mud came down the hill", "pagguho ng lupa sa amin", "putik ang pumasok sa kusina", "the slope collapsed onto the houses",
                  "tinabunan ng lupa yung kalsada at bahay", "mudslide sa barangay namin"],
}
TRAPPED = {
    "YES": ["hindi kami makalabas", "we can't get out", "stuck kami dito", "naipit kami", "natrap kami sa loob", "the door is blocked",
            "di mabuksan ang pinto", "walang daan palabas", "we're trapped", "nakulong kami sa loob", "can't get down", "di na kami makababa",
            "stranded kami", "we are stranded", "trapped inside", "hindi makalabas si {p}", "{p} is stuck inside", "napapalibutan kami ng tubig",
            "blocked ang exit", "hindi namin maalis yung nakaharang"],
    "NO": ["nakalabas na kami", "we got out", "nasa labas na kami", "we're outside now", "ligtas na kaming nakalabas", "nakalikas na kami",
           "we evacuated already", "nakaalis na kami sa bahay", "hindi naman kami naiipit", "we're not trapped"],
}
COND = {
    "NOT_BREATHING": ["hindi na humihinga {p}", "{p} is not breathing", "{p} stopped breathing", "wala nang hininga {p}",
                      "hindi gumagalaw ang dibdib ni {p}", "{p} has no pulse and isn't breathing", "parang hindi na humihinga {p}",
                      "{p} isn't breathing anymore", "di na humihinga {p}"],
    "UNCONSCIOUS": ["walang malay {p}", "{p} is unconscious", "{p} won't wake up", "hindi magising {p}", "nawalan ng malay {p}",
                    "{p} passed out", "{p} is not responding", "hindi sumasagot {p} pag tinatawag", "{p} fainted"],
    "HEAVY_BLEEDING": ["ang daming dugo {p}", "{p} is bleeding badly", "hindi tumitigil ang dugo ni {p}", "{p} is losing a lot of blood",
                       "dumudugo nang malakas {p}", "malalim ang sugat ni {p} at dumudugo", "blood everywhere, {p} is bleeding",
                       "basang-basa ng dugo ang damit ni {p}", "{p} has a deep cut that won't stop bleeding"],
    "PINNED": ["naipit ang binti ni {p}", "{p}'s leg is pinned under the wall", "hindi maigalaw ni {p} ang paa", "nadaganan ng biga {p}",
               "{p} is stuck under the debris", "may nakadagan sa katawan ni {p}", "{p} can't move, something heavy is on top",
               "naipit ang kamay ko", "my leg is crushed under the beam", "hindi ko maigalaw ang binti ko", "nadaganan ng aparador {p}"],
    "INJURED": ["sugatan {p}", "{p} is hurt", "{p} broke an arm", "may bali {p}", "{p} has a head wound", "may sugat {p} sa ulo",
                "{p} twisted an ankle", "masakit ang likod ni {p}", "{p} got burned", "nahihirapan huminga {p}", "{p} has trouble breathing",
                "hirap akong huminga dahil sa alikabok", "I can barely breathe from the dust", "masikip ang dibdib ni {p}", "{p} is hurt badly"],
    "WATER_RISING": ["tumataas pa ang tubig", "the water keeps rising", "lumalalim pa ang baha", "the water is getting higher",
                     "umaakyat na ang tubig", "pataas nang pataas ang baha", "the flood is still rising", "bumibilis tumaas ang tubig",
                     "the room is filling up", "pumapasok pa ang tubig"],
}
# Things that sound like a condition but aren't one: they keep the heads from firing on look-alikes.
CONFUSERS = ["walang sugatan sa amin", "no one is hurt", "humihinga pa naman {p}", "{p} is breathing fine", "gising naman {p}",
             "{p} is awake and talking", "hindi na tumataas ang tubig", "the water stopped rising", "humupa na ang baha",
             "walang dumudugo", "nobody is bleeding", "okay lang kami", "we're fine just scared", "nakakagalaw pa ako",
             "I can still move", "buhay pa kami", "maayos naman si {p}",
             # Damage that isn't a collapse.
             "may crack sa pader", "there are cracks in the wall", "nabasag ang bintana", "may lamat ang kisame"]
MENTION = ["kasama namin {p}", "with {p}", "nandito rin {p}", "kasama ko {p}", "{p} is with us", "andito {p}"]
PEOPLE = ["dalawa kami", "tatlo kami dito", "apat kami", "lima kaming magkakasama", "we are six", "there are 3 of us", "family of 5",
          "10 kami sa bahay", "7 people", "two of us", "ako lang mag-isa", "I'm alone"]
PLACE = ["sa second floor", "nasa bubong kami", "on the roof", "sa banyo", "in the bathroom", "sa kusina", "under the table",
         "sa ilalim ng mesa", "near the stairs", "sa hagdan", "sa kwarto", "in the bedroom", "sa ikalawang palapag", "3rd floor",
         "sa attic", "sa hallway", "sa labas ng bahay"]
FILLER = ["Tulong po!", "Please help", "#RescuePH", "pls hurry", "saklolo", "[address]", "pls rt", "HELP", "paki-rescue po",
          "send help asap", "[name] [number]", "please po", "God help us", "rescue needed", "nasa [address] kami", "please send a boat"]

SPEAK = {"kami": "kmi", "nasa": "nsa", "hindi": "hnd", "yung": "ung", "lang": "lng", "naman": "nmn", "siya": "sya", "please": "pls",
         "dito": "d2", "ako": "aq", "wala": "wla", "talaga": "tlga", "po": "p0"}


def person():
    return random.choice(PERSONS)


def fill(t):
    p, v = person()
    return t.replace("{p}", p), v if "{p}" in t else []


def report():
    parts, lab = [], {"hazard": "NONE", "trapped": "UNKNOWN", "cond": set(), "vuln": set()}
    single = random.random() < 0.25  # short single-fact messages, like clauses of longer ones

    pieces = []  # (text, its own labels): the same facts as single clauses, since the app also reads clause by clause

    def add(t, v=(), **own):
        parts.append(t)
        lab["vuln"].update(v)
        pieces.append((t, {"hazard": own.get("hazard", "NONE"), "trapped": own.get("trapped", "UNKNOWN"),
                           "cond": set(own.get("cond", ())), "vuln": set(v)}))

    if random.random() < (0.35 if single else 0.75):
        h = random.choice(list(HAZARD))
        lab["hazard"] = h
        add(random.choice(HAZARD[h]), hazard=h)
    if not single or not parts:
        if random.random() < 0.55:
            k = "YES" if random.random() < 0.8 else "NO"
            lab["trapped"] = k
            add(*fill(random.choice(TRAPPED[k])), trapped=k)
        for c in random.sample(list(COND), k=random.choice([0, 0, 1, 1, 1, 2])):
            if c == "WATER_RISING" and lab["hazard"] not in ("FLOOD", "NONE"):
                continue
            lab["cond"].add(c)
            add(*fill(random.choice(COND[c])), cond=[c])
        if random.random() < 0.3:
            add(*fill(random.choice(CONFUSERS)))
        if random.random() < 0.35:
            add(*fill(random.choice(MENTION)))
        if random.random() < 0.4:
            add(random.choice(PEOPLE))
        if random.random() < 0.4:
            add(random.choice(PLACE))
    if not parts:
        add(*fill(random.choice(CONFUSERS + MENTION)))
    # Consistency: rising water means a flood; a pinned person can't leave.
    def consistent(text, l):
        if l["hazard"] == "NONE" and ("WATER_RISING" in l["cond"] or any(w in text.lower() for w in ("tubig", "water", "baha", "flood"))):
            l["hazard"] = "FLOOD"
        if "PINNED" in l["cond"] and l["trapped"] == "UNKNOWN":
            l["trapped"] = "YES"
    consistent(" ".join(parts), lab)
    for t, l in pieces:
        consistent(t, l)
    random.shuffle(parts)
    if random.random() < 0.6:
        parts.insert(0 if random.random() < 0.5 else len(parts), random.choice(FILLER))
    joiner = random.choice([". ", ", ", " ", "! ", ", tapos ", " at ", " and ", " pero "])
    return joiner.join(parts), lab, pieces


def roughen(t):
    r = random.random()
    if r < 0.25:
        t = " ".join(SPEAK.get(w, w) if random.random() < 0.7 else w for w in t.split())
    if random.random() < 0.3:  # one-letter typo in a longer word
        ws = t.split()
        i = random.randrange(len(ws))
        if len(ws[i]) > 5:
            j = random.randrange(1, len(ws[i]) - 1)
            ws[i] = ws[i][:j] + ws[i][j + 1:]
        t = " ".join(ws)
    if random.random() < 0.15:
        t = t.upper()
    elif random.random() < 0.4:
        t = t.lower()
    if random.random() < 0.3:
        t = "".join(ch for ch in t if ch not in ".,!")
    return t


if __name__ == "__main__":
    out = HERE / "reports.jsonl"
    seen, n = set(), 0
    with out.open("w", encoding="utf-8") as f:
        def write(t, lab):
            f.write(json.dumps({"text": t, "hazard": lab["hazard"], "trapped": lab["trapped"],
                                "cond": sorted(lab["cond"]), "vuln": sorted(lab["vuln"])}, ensure_ascii=False) + "\n")

        while n < 3000:
            t, lab, pieces = report()
            t = roughen(t)
            if t in seen:
                continue
            seen.add(t)
            n += 1
            write(t, lab)
            for pt, pl in pieces:  # about one clause sample per report
                pt = roughen(pt)
                if len(pieces) > 1 and random.random() < 0.35 and pt not in seen:
                    seen.add(pt)
                    write(pt, pl)
    print(n, "reports →", out)
