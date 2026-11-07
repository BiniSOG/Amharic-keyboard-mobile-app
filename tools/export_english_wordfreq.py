#!/usr/bin/env python3
"""Exports the English suggestion dictionary from the `wordfreq` package.

Writes tools/english_words_freq.tsv (word<TAB>frequency). build_dictionaries.py merges
it with the curated list in english_words_ranked.txt.

Data: wordfreq by Robyn Speer, word lists licensed CC BY-SA 4.0
(https://github.com/rspeer/wordfreq). The generated file carries the same licence.

Usage: pip install wordfreq==3.1.1 && python3 tools/export_english_wordfreq.py
"""
import os
import re

import wordfreq

N = 20000
HERE = os.path.dirname(os.path.abspath(__file__))
WORD = re.compile(r"^[a-z]+(?:'[a-z]+)?$")
SINGLE_LETTERS = {"a", "i"}
# Do not proactively suggest slurs/profanity. Users can still type and teach them.
BLOCK = set("""
fuck fucking fucked fucker fucks motherfucker shit shitty bullshit bitch bitches cunt dick dicks
cock pussy whore slut bastard asshole nigga nigger faggot fag retard retarded twat wanker
""".split())

out = []
for w in wordfreq.top_n_list("en", N * 2):
    if len(out) >= N:
        break
    if not WORD.match(w) or w in BLOCK or (len(w) == 1 and w not in SINGLE_LETTERS):
        continue
    zipf = wordfreq.zipf_frequency(w, "en")
    # Zipf 3 (≈1 per million words) → 3, Zipf 7.7 ('the') → ~160k
    out.append((w, max(1, int(round(10 ** (zipf - 2.5))))))

with open(os.path.join(HERE, "english_words_freq.tsv"), "w", encoding="utf-8") as f:
    f.write("# exported from wordfreq 3.1.1 (CC BY-SA 4.0) by tools/export_english_wordfreq.py\n")
    for w, c in out:
        f.write(f"{w}\t{c}\n")
print(len(out), "words")
