from pathlib import Path
p = Path(r"d:\7-8-26\AURA HI-RES\app\src\main\kotlin\com\music\echo\ui\newui\AuraOnlinePlaylistScreen.kt")
text = p.read_text(encoding="utf-8")
# Remove FIRST More block (top row) only — between Play weight and Spacer(12)
marker = "modifier = Modifier.weight(1f).tvFocusable(isTvOrCar, scaleFocused = 1f),\n            )\n            AuraHeaderCircleButton(\n                icon = AuraIcons.More,"
idx = text.find(marker)
print("first more after play", idx)
if idx < 0:
    raise SystemExit("pattern not found")
# Find the end of this More button (closing of circle button + before Spacer)
start = text.find("            AuraHeaderCircleButton(\n                icon = AuraIcons.More,", idx)
# Only remove if this is before secondary comment
sec = text.find("// Guardar", start)
end = text.find("\n        )\n\n        Spacer(modifier.height(12.dp))", start)
print("start", start, "end", end, "sec", sec)
if start < 0 or end < 0 or start > sec:
    raise SystemExit("bad bounds")
# end points at newline before `        )` that closes the Row — we need to remove More button but keep Row close
# Actually end finds `        )` closing the Row. We should remove from start through the More's `            )` inclusive
# Find More's closing: after start, find matching
chunk = text[start:sec]
# Remove entire AuraHeaderCircleButton for More in chunk
import re
chunk2, n = re.subn(
    r"\n            AuraHeaderCircleButton\(\n                icon = AuraIcons\.More,\n(?:.*\n)*?                modifier = Modifier\.tvFocusable\(isTvOrCar, scaleFocused = 1f\),\n            \)",
    "",
    chunk,
    count=1,
)
print("removed", n, "len delta", len(chunk)-len(chunk2))
text = text[:start] + chunk2 + text[sec:]
# Ensure secondary has More after Search
if text.count("icon = AuraIcons.More") < 1:
    raise SystemExit("no More left!")
print("More count", text.count("icon = AuraIcons.More"))
# Update comment
text = text.replace(
    "// Guardar · Descargar · Compartir · Buscar — search on the secondary row.",
    "// Guardar · Descargar · Compartir · Buscar · Más — secondary row.",
    1,
)
p.write_text(text, encoding="utf-8")
print("ok")
