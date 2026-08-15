from pathlib import Path

p = Path("app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayerMenu.kt")
raw = p.read_bytes()
# normalize for search
text = raw.decode("utf-8")
marker = "val (audioQuality, onAudioQualityChange)"
i = text.find(marker)
if i < 0:
    raise SystemExit("marker not found")
# walk back to start of "item {"
start = text.rfind("item {", 0, i)
if start < 0:
    raise SystemExit("item start not found")
# find matching close for this item block — next "\n        item {" after the AuraMenuRow closes
# Simpler: from start, find the line with only "        }" that closes the item after LOSSLESS
j = text.find("LOSSLESS -> \"QOBUZ\"", i)
if j < 0:
    raise SystemExit("QOBUZ not found")
# after that, find "\n        }\n        item { Spacer"
k = text.find("\n        item { Spacer", j)
if k < 0:
    k = text.find("\n        item { Spacer", j)
if k < 0:
    # show context
    print(repr(text[j:j+200]))
    raise SystemExit("spacer item not found")
# start may need to include leading whitespace of the line
line_start = text.rfind("\n", 0, start) + 1
replacement = "        // Streaming quality cycling (Opus / Saavn / Qobuz) removed — Opus is the only stream path.\n"
new_text = text[:line_start] + replacement + text[k+1:]  # k points at \n before item Spacer — keep that item
p.write_text(new_text, encoding="utf-8", newline="\n")
print("OK removed", start, "to", k)
