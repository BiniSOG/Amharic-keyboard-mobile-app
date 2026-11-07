#!/usr/bin/env python3
"""Expands tools/phrasal_verbs.tsv into all English forms for the translator.

Input lines:  phrasal verb<TAB>Amharic meaning|Amharic meaning
Output:       vocab_en/50_phrasal_verbs.tsv with
              "give up|gives up|gave up|giving up|given up<TAB>መተው|ማቆም"
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))

# base: (past, past participle)
IRREGULAR = {
    "be": ("was", "been"), "bear": ("bore", "borne"), "beat": ("beat", "beaten"), "become": ("became", "become"),
    "begin": ("began", "begun"), "bend": ("bent", "bent"), "bite": ("bit", "bitten"), "blow": ("blew", "blown"),
    "break": ("broke", "broken"), "bring": ("brought", "brought"), "build": ("built", "built"), "burn": ("burnt", "burnt"),
    "buy": ("bought", "bought"), "catch": ("caught", "caught"), "choose": ("chose", "chosen"), "come": ("came", "come"),
    "cut": ("cut", "cut"), "deal": ("dealt", "dealt"), "dig": ("dug", "dug"), "do": ("did", "done"), "draw": ("drew", "drawn"),
    "drink": ("drank", "drunk"), "drive": ("drove", "driven"), "eat": ("ate", "eaten"), "fall": ("fell", "fallen"),
    "feel": ("felt", "felt"), "fight": ("fought", "fought"), "find": ("found", "found"), "fly": ("flew", "flown"),
    "forget": ("forgot", "forgotten"), "freeze": ("froze", "frozen"), "get": ("got", "gotten"), "give": ("gave", "given"),
    "go": ("went", "gone"), "grow": ("grew", "grown"), "hang": ("hung", "hung"), "have": ("had", "had"),
    "hear": ("heard", "heard"), "hide": ("hid", "hidden"), "hit": ("hit", "hit"), "hold": ("held", "held"),
    "keep": ("kept", "kept"), "know": ("knew", "known"), "lay": ("laid", "laid"), "lead": ("led", "led"),
    "leave": ("left", "left"), "lend": ("lent", "lent"), "let": ("let", "let"), "lie": ("lay", "lain"),
    "light": ("lit", "lit"), "lose": ("lost", "lost"), "make": ("made", "made"), "mean": ("meant", "meant"),
    "meet": ("met", "met"), "pay": ("paid", "paid"), "put": ("put", "put"), "read": ("read", "read"),
    "ride": ("rode", "ridden"), "ring": ("rang", "rung"), "rise": ("rose", "risen"), "run": ("ran", "run"),
    "say": ("said", "said"), "see": ("saw", "seen"), "sell": ("sold", "sold"), "send": ("sent", "sent"),
    "set": ("set", "set"), "shake": ("shook", "shaken"), "shoot": ("shot", "shot"), "shut": ("shut", "shut"),
    "sing": ("sang", "sung"), "sink": ("sank", "sunk"), "sit": ("sat", "sat"), "sleep": ("slept", "slept"),
    "slide": ("slid", "slid"), "speak": ("spoke", "spoken"), "spend": ("spent", "spent"), "split": ("split", "split"),
    "spread": ("spread", "spread"), "stand": ("stood", "stood"), "steal": ("stole", "stolen"), "stick": ("stuck", "stuck"),
    "strike": ("struck", "struck"), "swear": ("swore", "sworn"), "sweep": ("swept", "swept"), "swim": ("swam", "swum"),
    "swing": ("swung", "swung"), "take": ("took", "taken"), "teach": ("taught", "taught"), "tear": ("tore", "torn"),
    "tell": ("told", "told"), "think": ("thought", "thought"), "throw": ("threw", "thrown"), "wake": ("woke", "woken"),
    "wear": ("wore", "worn"), "win": ("won", "won"), "write": ("wrote", "written"), "wind": ("wound", "wound"),
    "shine": ("shone", "shone"), "feed": ("fed", "fed"), "hurt": ("hurt", "hurt"), "quit": ("quit", "quit"),
    "spit": ("spat", "spat"), "dream": ("dreamt", "dreamt"), "bet": ("bet", "bet"), "seek": ("sought", "sought"),
}
# short verbs that double their final consonant: stop → stopped, stopping
DOUBLE = set("""stop drop plan step ship slip trip grab rub nod pop top tip zip map mop nap pat pin chat drag jog
hug beg bat bag dip hop log pad pet rip rob rot sip sob tap tug wrap chop clap ban fit hem jot knit mug net
pen plug prop scrub shop shrug skip slam snap stab strip swap trim wed wet bob bud dot dub gun hog jam""".split())

def third(v):
    if v == "be": return "is"
    if v == "have": return "has"
    if v in ("do", "go"): return v + "es"
    if v.endswith(("s", "x", "z", "ch", "sh", "o")): return v + "es"
    if v.endswith("y") and v[-2] not in "aeiou": return v[:-1] + "ies"
    return v + "s"

def regular_past(v):
    if v in DOUBLE: return v + v[-1] + "ed"
    if v.endswith("e"): return v + "d"
    if v.endswith("y") and v[-2] not in "aeiou": return v[:-1] + "ied"
    return v + "ed"

def ing(v):
    if v == "be": return "being"
    if v.endswith("ie"): return v[:-2] + "ying"
    if v in DOUBLE or v in ("get", "put", "set", "let", "run", "sit", "cut", "hit", "win", "swim", "dig", "begin", "shut", "spit", "quit", "forget", "split"):
        return v + v[-1] + "ing"
    if v.endswith("e") and not v.endswith(("ee", "ye", "oe")): return v[:-1] + "ing"
    return v + "ing"

def forms(phrase):
    verb, *rest = phrase.split(" ")
    tail = (" " + " ".join(rest)) if rest else ""
    past, part = IRREGULAR.get(verb, (regular_past(verb), regular_past(verb)))
    out = [verb, third(verb), past, ing(verb), part]
    if verb == "be": out = ["be", "is", "was", "being", "been", "am", "are", "were"]
    seen, result = set(), []
    for f in out:
        if f not in seen:
            seen.add(f)
            result.append(f + tail)
    return result

rows, n_verbs = [], 0
with open(os.path.join(HERE, "phrasal_verbs.tsv"), encoding="utf-8") as f:
    for line in f:
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        en, am = line.split("\t")
        n_verbs += 1
        rows.append("|".join(forms(en.strip())) + "\t" + am.strip())

with open(os.path.join(HERE, "vocab_en", "50_phrasal_verbs.tsv"), "w", encoding="utf-8") as o:
    o.write("# generated by tools/gen_phrasal_verbs.py from tools/phrasal_verbs.tsv\n")
    o.write("\n".join(rows) + "\n")
n_forms = sum(len(r.split("\t")[0].split("|")) for r in rows)
print(f"{n_verbs} phrasal verbs -> {n_forms} English forms")
