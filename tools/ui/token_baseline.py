#!/usr/bin/env python3
"""Write the per-file raw design value counts that TokenRatchetTest holds the app to (app/src/test/resources/token-ratchet.txt).

Run from the repository root after moving a screen onto the tokens, so the lower counts become the new ceiling.
Counted outside ui/theme: dp literals above 2 (1-2dp hairlines and nudges are allowed), sp literals, Color(0x...).
"""
import os, re

ROOT = "app/src/main/java/com/wangzan101/mika"
OUT = "app/src/test/resources/token-ratchet.txt"
DP = re.compile(r"(?<![\w.])(\d+(?:\.\d+)?)\.dp\b")
SP = re.compile(r"(?<![\w.])\d+(?:\.\d+)?\.sp\b")
HEX = re.compile(r"Color\(0x")


def counts(path):
    dp = sp = hx = 0
    for line in open(path, encoding="utf-8"):
        code = line.split("//")[0]
        if code.lstrip().startswith("*"):
            continue
        dp += sum(1 for m in DP.finditer(code) if float(m.group(1)) > 2)
        sp += len(SP.findall(code))
        hx += len(HEX.findall(code))
    return dp, sp, hx


rows = []
for d, _, files in os.walk(ROOT):
    for f in sorted(files):
        p = os.path.join(d, f)
        rel = os.path.relpath(p, ROOT).replace(os.sep, "/")
        if not f.endswith(".kt") or rel.startswith("ui/theme/"):
            continue
        c = counts(p)
        if any(c):
            rows.append((rel, *c))
rows.sort()
with open(OUT, "w", encoding="utf-8") as out:
    out.write("# path raw-dp raw-sp hex-colors; regenerate with tools/ui/token_baseline.py. Counts may only go down.\n")
    for r in rows:
        out.write("%s %d %d %d\n" % r)
print("files", len(rows), "dp", sum(r[1] for r in rows), "sp", sum(r[2] for r in rows), "hex", sum(r[3] for r in rows))
