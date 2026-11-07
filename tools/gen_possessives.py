#!/usr/bin/env python3
"""Generates possessive forms (my/your/his/her/our/their X) for common nouns.

Amharic marks possession with suffixes that fuse with the noun's last Fidel letter:
  consonant-final (6th order): ቤት → ቤቴ my, ቤትህ your(m), ቤትሽ your(f), ቤቱ his/the,
                                ቤቷ her, ቤታችን our, ቤታችሁ your(pl), ቤታቸው their
  a-final (4th order):         መኪና → መኪናዬ, መኪናህ, መኪናሽ, መኪናው, መኪናዋ, መኪናችን, መኪናችሁ, መኪናቸው
Nouns ending in other vowels, or in ህ/ው/ይ/እ, are skipped (their forms are irregular).
Writes vocab/20_possessives.tsv.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))

# (base code point of each consonant row, has a labialised "wa" form)
ROWS = [0x1200, 0x1208, 0x1210, 0x1218, 0x1220, 0x1228, 0x1230, 0x1238, 0x1240, 0x1250, 0x1260,
        0x1268, 0x1270, 0x1278, 0x1280, 0x1290, 0x1298, 0x12A0, 0x12A8, 0x12B8, 0x12C8, 0x12D0,
        0x12D8, 0x12E0, 0x12E8, 0x12F0, 0x1300, 0x1308, 0x1320, 0x1328, 0x1330, 0x1338, 0x1340,
        0x1348, 0x1350]
WA = {0x1208: 0x120F, 0x1210: 0x1217, 0x1218: 0x121F, 0x1220: 0x1227, 0x1228: 0x122F, 0x1230: 0x1237,
      0x1238: 0x123F, 0x1240: 0x124B, 0x1260: 0x1267, 0x1268: 0x126F, 0x1270: 0x1277, 0x1278: 0x127F,
      0x1290: 0x1297, 0x1298: 0x129F, 0x12A8: 0x12B3, 0x12D8: 0x12DF, 0x12E0: 0x12E7, 0x12F0: 0x12F7,
      0x1300: 0x1307, 0x1308: 0x1313, 0x1320: 0x1327, 0x1328: 0x132F, 0x1330: 0x1337, 0x1338: 0x133F,
      0x1348: 0x134F, 0x1350: 0x1357}
SKIP_ROWS = {0x1200, 0x12C8, 0x12E8, 0x12A0, 0x12D0}  # ህ ው ይ እ ዕ consonant endings are irregular

NOUNS = """
ቤት house|ስልክ phone|ስም name|ገንዘብ money|ሥራ work|ስራ job|ልጅ child|እናት mother|አባት father
ወንድም brother|እህት sister|ጓደኛ friend|ባል husband|ሚስት wife|ቤተሰብ family|አገር country|ሀገር country
ከተማ city|መኪና car|መጽሐፍ book|ልብስ clothes|ጫማ shoes|ቦርሳ bag|ሰዓት watch|ቁልፍ key|ኮምፒውተር computer
ፀጉር hair|እጅ hand|እግር leg|ልብ heart|ዓይን eye|አይን eye|አፍ mouth|ፊት face|ሆድ stomach|ጥርስ tooth
ሀሳብ idea|ሃሳብ idea|ፍቅር love|ሕይወት life|ህይወት life|ጤና health|ትምህርት education|ምግብ food|ቡና coffee
ውሃ water|ቁርስ breakfast|ምሳ lunch|እራት dinner|ክፍል room|አልጋ bed|ወንበር chair|በር door|ሰፈር neighbourhood
መንደር village|ጎረቤት neighbour|አለቃ boss|መምህር teacher|ዶክተር doctor|ሀኪም doctor|ደንበኛ customer
ባለቤት spouse|እጮኛ fiancé|ፍቅረኛ lover|ውሻ dog|ድመት cat|ላም cow|ፈረስ horse|ኩባንያ company|ድርጅት organization
ሱቅ shop|ንግድ business|ደመወዝ salary|ብር money|ሂሳብ account|ካርድ card|ፓስፖርት passport|መታወቂያ ID
አድራሻ address|ቁጥር number|ኢሜይል email|መልዕክት message|መልስ answer|ቃል word|ድምፅ voice|ቀን day
ልደት birthday|ሰርግ wedding|ስጦታ gift|ምስል picture|ዘፈን song|ፊልም film|ጨዋታ game|ቡድን team|ክለብ club
ዕቅድ plan|እቅድ plan|ፕሮጀክት project|ሀብት wealth|ችግር problem|ስህተት mistake|ጥፋት fault|መብት right
ኃላፊነት responsibility|ምርጫ choice|ህልም dream|ተስፋ hope|እምነት faith|ሃይማኖት religion|ታሪክ story
ባህል culture|ዜግነት citizenship|ቦታ place|መቀመጫ seat|ትኬት ticket|ሻንጣ suitcase|ዕቃ belongings
ሸሚዝ shirt|ቀሚስ dress|ኮት coat|ኮፍያ hat|መነፅር glasses|ቀለበት ring|ትራስ pillow|ቴሌቪዥን television
ፍሪጅ fridge|ቻርጀር charger|ላፕቶፕ laptop|ሰውነት body|ደም blood|መድሃኒት medicine|ዘመድ relative
ወላጅ parent|አጎት uncle|አክስት aunt|አያት grandparent|ወዳጅ friend|ጠላት enemy|እንግዳ guest|ፎቶግራፍ photograph
መንገድ way|ጉዞ trip|ፈተና exam|ውጤት result|ማስታወሻ note|ደብተር notebook|ብዕር pen|እርሳስ pencil
ገበሬ farmer|ሾፌር driver|ተራ turn|ሚስጥር secret|ጉዳይ matter|ፍላጎት wish|ስሜት feeling|ድካም tiredness
ልማድ habit|ዕድል luck|እድል luck|ፀሎት prayer|ጸሎት prayer|ምክር advice|ፍርድ judgement|ፈቃድ permission
መሬት land|እርሻ farm|ከብት cattle|በግ sheep|ፍየል goat|ዛፍ tree|አበባ flower|ግቢ_ compound
ትከሻ shoulder|አንገት neck|ጣት finger|ቆዳ skin|ፍራሽ mattress|ሳህን plate|ድስት pot|ማንኪያ spoon|ቢላዋ knife
ሳሙና soap|ፎጣ towel|መስኮት window|ግድግዳ wall|ጣሪያ roof|አትክልት garden|እንቁላል egg|ወተት milk|ዘይት oil
ስኳር sugar|ማር honey|ገበያ market|ሆስፒታል hospital|ስብሰባ meeting|ጥናት study|ምርምር research
ሰርተፊኬት certificate|ደረጃ level|ውድድር competition|ዋንጫ cup|ሽልማት prize|ሰነድ document|ፊርማ signature
ውል contract|ጋብቻ marriage|ትዳር marriage|ጉብኝት visit|ዕረፍት vacation|በዓል holiday|ቤተ_ x
"""

def forms(noun):
    last = ord(noun[-1])
    base = next((b for b in ROWS if b <= last < b + 8), None)
    if base is None:
        return None
    order = last - base
    stem = noun[:-1]
    if order == 5 and base not in SKIP_ROWS:  # consonant-final
        out = {
            "my": stem + chr(base + 4),
            "your_m": noun + "ህ",
            "your_f": noun + "ሽ",
            "his": stem + chr(base + 1),
            "our": stem + chr(base + 3) + "ችን",
            "your_pl": stem + chr(base + 3) + "ችሁ",
            "their": stem + chr(base + 3) + "ቸው",
        }
        if base in WA:
            out["her"] = stem + chr(WA[base])
        return out
    if order == 3:  # a-final
        return {"my": noun + "ዬ", "your_m": noun + "ህ", "your_f": noun + "ሽ", "his": noun + "ው",
                "her": noun + "ዋ", "our": noun + "ችን", "your_pl": noun + "ችሁ", "their": noun + "ቸው"}
    return None

EN = {"my": "my {}", "your_m": "your {}", "your_f": "your {}", "his": "the {}|his {}", "her": "her {}",
      "our": "our {}", "your_pl": "your {}", "their": "their {}"}

rows, skipped = [], []
for item in NOUNS.replace("\n", "|").split("|"):
    item = item.strip()
    if not item or "_" in item:
        continue
    am, en = item.split(" ", 1)
    f = forms(am)
    if f is None:
        skipped.append(am)
        continue
    for k, w in f.items():
        rows.append(f"{w}\t{EN[k].format(en, en)}")

with open(os.path.join(HERE, "vocab", "20_possessives.tsv"), "w", encoding="utf-8") as o:
    o.write("# generated by tools/gen_possessives.py - possessive forms of common nouns\n")
    o.write("\n".join(rows) + "\n")
print(len(rows), "forms; skipped:", " ".join(skipped))
