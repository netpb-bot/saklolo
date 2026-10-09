"""Builds the two frozen test sets. Nothing is trained or tuned on these.

real.json: anonymized #RescuePH tweets from CrisisLexT26 (Olteanu, Vieweg & Castillo, CSCW '15), Philippine events.
           Names → [name], street addresses → [address], phone numbers → [number], handles removed.
           Labeled from the text alone: FLOOD only if water/flood is said; trapped only if trapped/stranded/stuck/
           can't leave is said ("on the roof" alone is floor 15, trapped unknown); people only if countable.
hard.json: written separately from the training set: wording without keywords, negation, corrections, text-speak,
           collapse/landslide cases, and breathing cases that must never become NOT_BREATHING.
"""
import json
from pathlib import Path

OUT = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "eval"


def r(text, hazard="NONE", people=0, trapped="UNKNOWN", floor=0, spot="UNKNOWN", vuln=(), cond=(), lang="en", by=None):
    return {"lang": lang, **({"by": by} if by else {}), "text": text, "expect": {
        "hazard": hazard, "people": people, "trapped": trapped, "floor": floor, "spot": spot,
        "vulnerable": list(vuln), "condition": list(cond)}}


T = "tweet"
real = [
    r("74 yrs old lady trapped at [address]. All grilled windows locked water already at 2nd floor. #rescuePH help!", "FLOOD", 1, "YES", vuln=["ELDERLY"], by=T),
    r("Please send rescue team to [address]. Family trapped in 2nd flr. Thanks! #RescuePH", trapped="YES", floor=2, by=T),
    r("#RescuePH Rescue pls family trapped at [address]. Family of 4 w/2 children.", people=4, trapped="YES", vuln=["CHILD"], by=T),
    r("Pls rescue folks at [address]- they're on the roof! #rescuePH", floor=15, by=T),
    r("[place] needs rescue. Almost 2nd flr na yung tubig. Naka salbabida na yung mga tao! #rescuePH", "FLOOD", lang="taglish", by=T),
    r("Please retweet! Teenager stranded in his house [address] #rescuePH", people=1, trapped="YES", by=T),
    r("Pls rescue PWDs in [address]. #RescuePH please pass!", vuln=["PWD"], by=T),
    r("Families stuck in the roof, 11 people, [address], they need immediate help #RescuePH", people=11, trapped="YES", floor=15, by=T),
    r("People at [place] needs immediate help. Yung ibang tao nasa bubong na daw.", floor=15, lang="taglish", by=T),
    r("Please send help [address].. May 2 bata, isang baby at mga lola at lolo ko po andun #RescuePH", vuln=["CHILD", "INFANT", "ELDERLY"], lang="taglish", by=T),
    r("#RescuePH [name] & family, [address]. Stranded in neck-deep flood.", "FLOOD", trapped="YES", by=T),
    r("[name] with 3 kids [address]; their house is near the national high school #rescuePH", people=4, vuln=["CHILD"], by=T),
    r("[name], 9 adults +1 child, [address] #rescuePH Stranded on 3rd flr, Flood", "FLOOD", 10, "YES", floor=3, vuln=["CHILD"], by=T),
    r("please help! stranded ang mga residente ng [address]! staying on top of their roofs!", trapped="YES", floor=15, lang="taglish", by=T),
    r("#rescuePH Pregnant mom and small kids trapped in 5 feet flooded house [address]", "FLOOD", trapped="YES", vuln=["PREGNANT", "CHILD"], by=T),
    r("PREGNANT [name] NEEDS IMMEDIATE #rescuePH @ [address]. SHES IN PAIN.", people=1, vuln=["PREGNANT"], cond=["INJURED"], by=T),
    r("Please po may mga bata pa sa address na to. [name]. [address] #rescueph", vuln=["CHILD"], lang="taglish", by=T),
    r("#RescuePH my grandpa at [address], he's 75yrs old & nsa bubong sya since 10am. :(", people=1, floor=15, vuln=["ELDERLY"], lang="taglish", by=T),
    r("#rescuePH Rescue needed [name] & family (incl.1 yr old baby) & 70 y/o dad. [address]", vuln=["INFANT", "ELDERLY"], by=T),
    r("Rescue needed in [address]. 1st house beside Caltex. Lolo and Lola residing. #RescuePH", people=2, vuln=["ELDERLY"], by=T),
    r("2yo toddler stuck w/ 91 y.o. lola @ 2nd flr of hse.", people=2, trapped="YES", floor=2, vuln=["CHILD", "ELDERLY"], by=T),
    r("#RescuePH [name] family, [address]. The mother has cancer. Theyre trapped on their roof", trapped="YES", floor=15, by=T),
    r("WE NEED HELP!!! 6 PEOPLE INSIDE HOUSE, [address]. PLEASE SPREAD!!! #RescuePH", people=6, by=T),
    r("NASA BUBONG WITH 1Y/O BABY. [name] [address] [number] #rescuePH", floor=15, vuln=["INFANT"], lang="taglish", by=T),
    r("pls RT: NEED RESCUE [address]! MOTHER+CHILD+PETS #rescuePH", people=2, vuln=["CHILD"], by=T),
    r("[address]. Family stranded on the roof, no food and drink since morning. #rescuePH", trapped="YES", floor=15, by=T),
    r("House is a bungalow & water is knee deep already & rising fast. #rescuePH", "FLOOD", cond=["WATER_RISING"], by=T),
    r("#rescuePH i need help for 81yr old grandma stuck alone in her house [address]", people=1, trapped="YES", vuln=["ELDERLY"], by=T),
    r("Pls Help! [name] and 3 others trapped @ [address] [number].#RescuePH", people=4, trapped="YES", by=T),
    r("need help asap w/ 3 days old baby [address]. [number] #RescuePH", vuln=["INFANT"], by=T),
    r("pls RT Family of 8 with 2 seniors is trapped in their home! Please help #rescuePH", people=8, trapped="YES", vuln=["ELDERLY"], by=T),
    r("Sana po magkaron ng pagrescue sa [place]. Marami na pong natrap sa 2nd floor ng mga bahay #rescuePH", trapped="YES", floor=2, lang="taglish", by=T),
    r("#rescuePH TRAPPED NO FOOD WATER SINCE 9AM [name], [address], [number]", trapped="YES", by=T),
    r("4 persons needs #rescuePH at the only house now that can be seen at [address].", people=4, by=T),
    r("[address]! 10 persons including 2 kids. last working number [number]. #rescuePH", people=10, vuln=["CHILD"], by=T),
    r("Please help [name] FAMILY w/ 2y/o child!! No communications yet @ [address] #rescuePH", vuln=["CHILD"], by=T),
    r("paki-tag yung involve sa [place], kanina pa sila humihingi ng tulong wala pa raw nag-rescue. #rescuePH", lang="fil", by=T),
    r("#rescueph A pregnant woman's staying on top of a roof near [address]. PLEASE HELP!!", people=1, floor=15, vuln=["PREGNANT"], by=T),
    r("Please help! [address]. Lola is bedridden :( [name]-[number] #rescuePH", people=1, vuln=["ELDERLY"], by=T),
    r("Pls help [name] pregnant due na po niya on aug 11 [address] #rescuePH", people=1, vuln=["PREGNANT"], lang="taglish", by=T),
    r("Can somebody rescue my friend @ [address] [number]. Still on roof 8/8 7:58am #rescuePH", people=1, floor=15, by=T),
    r("#RescuePH [name] and fam of [address]. 7 pax stranded on rooftop", people=7, trapped="YES", floor=15, by=T),
    r("pls help [name]. address is [address]. Water rising. [number]. #rescuePH", "FLOOD", cond=["WATER_RISING"], by=T),
    r("[address]. 20 people in the house. Pls send help. #rescuePH", people=20, by=T),
    r("hav a friend who badly needs help at [address] di na sila makalabas house pls RT #rescuePH", trapped="YES", lang="taglish", by=T),
    r("[place] esp. my family, needs FOOD and clean WATER. Flood chest deep inside our house. Pls send them #rescuePH", "FLOOD", by=T),
    r("[name], 6 people including baby [address] [number] #RescuePH", people=6, vuln=["INFANT"], by=T),
    r("May mangnganak. [address]. [name]-[number] #RescuePH", vuln=["PREGNANT"], lang="fil", by=T),
    r("URGENT RESCUE ASSISTANCE NEEDED, [place], Flood waters rising fast po!! #RescuePH", "FLOOD", cond=["WATER_RISING"], lang="taglish", by=T),
    r("[name] family. [address] - Family of 12 including 1 elderly #rescuePH", people=12, vuln=["ELDERLY"], by=T),
    r("My friend from [place] needs help! malapit na daw sa 2nd floor ung baha doon. please RT this #rescuePH", "FLOOD", 1, lang="taglish", by=T),
    r("[name]; [address]. Baby needs milk. Flood is chest deep. #RescuePH", "FLOOD", vuln=["INFANT"], by=T),
    r("#RescuePH Pls help! [name] [address]. WOUNDED. No food 3 days", people=1, cond=["INJURED"], by=T),
    r("Help rescue & evacuate residents of [place]. People now on their rooftops. #RescuePH", floor=15, by=T),
    r("#rescuePH my ofcmate [name] & [name] is stuck at 2nd flr of their house [address]", people=2, trapped="YES", floor=2, by=T),
    r("Stuck tao sa second floor at umaasa napo ang mga tao sa rescuer asa second floor na tubig", "FLOOD", trapped="YES", floor=2, lang="taglish", by=T),
    r("Mag-asawang matanda sa foot bridge sa [place]. Wala silang matuluyan #RescuePH", people=2, vuln=["ELDERLY"], lang="fil", by=T),
    r("[name] [address] w/ children #RescuePH", vuln=["CHILD"], by=T),
]

H = "hard"
hard = [
    r("Nadaganan ng aparador ang binti ni nanay, hindi siya makagalaw. Kami lang dalawa dito sa kwarto.", people=2, trapped="YES", spot="ROOM", cond=["PINNED"], lang="fil", by=H),
    r("Bumigay yung kisame sa sala, may naipit na bata sa ilalim.", "COLLAPSE", trapped="YES", spot="ROOM", vuln=["CHILD"], cond=["PINNED"], lang="taglish", by=H),
    r("Yung bahay ng kapitbahay nagiba, wala nang sumasagot sa loob.", "COLLAPSE", lang="fil", by=H),
    r("Lola is pale and not responding when we call her name.", vuln=["ELDERLY"], cond=["UNCONSCIOUS"], by=H),
    r("Ang dami pong lumalabas na dugo sa hita ni kuya, basang-basa na yung tuwalya.", cond=["HEAVY_BLEEDING"], lang="fil", by=H),
    r("My son's lips are turning blue and his chest isn't moving.", vuln=["CHILD"], cond=["NOT_BREATHING"], by=H),
    r("Gumuho ang lupa sa likod ng bahay, natabunan ang kusina.", "LANDSLIDE", trapped="YES", spot="KITCHEN", lang="fil", by=H),
    r("The hillside slid down onto our street after the rain, three houses are buried.", "LANDSLIDE", by=H),
    r("hanggang dibdib na yung tubig sa loob at lumalalim pa", "FLOOD", cond=["WATER_RISING"], lang="taglish", by=H),
    r("We made it out of the building, everyone's okay, just scared.", trapped="NO", by=H),
    r("hindi naman kami naipit pero sira na ang hagdan kaya di kami makababa", trapped="YES", lang="fil", by=H),
    r("Tatlo, este apat pala kaming nandito sa CR ng 3rd floor", people=4, floor=3, spot="BATHROOM", lang="taglish", by=H),
    r("lindol!! bagsak ung pader s kwarto ni lolo, d sya makatayo", "COLLAPSE", trapped="YES", spot="ROOM", vuln=["ELDERLY"], cond=["PINNED"], lang="taglish", by=H),
    r("There's a crushing weight on my legs and I can't pull them out.", people=1, trapped="YES", cond=["PINNED"], by=H),
    r("My grandmother has been unresponsive since the wall fell.", "COLLAPSE", vuln=["ELDERLY"], cond=["UNCONSCIOUS"], by=H),
    r("Wala pong sugatan sa amin, pero sarado ang daan palabas dahil sa guho.", "COLLAPSE", trapped="YES", lang="fil", by=H),
    r("Buntis ang asawa ko at mukhang manganganak na siya, nasa bubong kami, baha.", "FLOOD", 2, floor=15, vuln=["PREGNANT"], lang="fil", by=H),
    r("may PWD kaming kasama na di makalakad, rumaragasa na ang baha sa labas", "FLOOD", vuln=["PWD"], lang="taglish", by=H),
    r("Two kids and I are hiding under the dining table, the house is shaking and pieces are falling.", "COLLAPSE", 3, spot="UNDER_TABLE", vuln=["CHILD"], by=H),
    r("huminto na yata ang paghinga ni papa, hindi gumagalaw", cond=["NOT_BREATHING"], lang="fil", by=H),
    r("may sugat sa noo ko pero kaya ko pa, kaso nakulong ako sa elevator", people=1, trapped="YES", cond=["INJURED"], lang="fil", by=H),
    r("im ok but my brother's head got hit by a falling beam and he's bleeding nonstop", "COLLAPSE", 2, cond=["HEAVY_BLEEDING"], by=H),
    r("nilamon na ng tubig ang unang palapag, umakyat kami sa attic", "FLOOD", spot="ATTIC", lang="fil", by=H),
    r("Hirap huminga si lola dahil sa usok at alikabok.", vuln=["ELDERLY"], cond=["INJURED"], lang="fil", by=H),
    r("Hindi siya humihinga ng maayos pero gising pa.", cond=["INJURED"], lang="fil", by=H),
    r("Our apartment block pancaked, I'm in what's left of the hallway on the 2nd floor.", "COLLAPSE", 1, floor=2, spot="HALLWAY", by=H),
    r("anim kami dito, dalawa senior, isang naka-wheelchair", people=6, vuln=["ELDERLY", "PWD"], lang="fil", by=H),
    r("baby ko po hindi umiiyak at hindi humihinga", vuln=["INFANT"], cond=["NOT_BREATHING"], lang="fil", by=H),
    r("The water stopped rising but we're still stuck on the roof.", "FLOOD", trapped="YES", floor=15, by=H),
]


def dump(name, items):
    (OUT / name).write_text("[\n" + ",\n".join("  " + json.dumps(c, ensure_ascii=False) for c in items) + "\n]\n", encoding="utf-8")
    print(name, len(items))


if __name__ == "__main__":
    dump("real.json", real)
    dump("hard.json", hard)
