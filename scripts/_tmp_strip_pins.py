from pathlib import Path
import re

menu_dir = Path(r"d:\7-8-26\AURA HI-RES\app\src\main\kotlin\com\music\echo\ui\menu")

patterns = [
    # add( Material3MenuItemData( ... pin ... ) )
    re.compile(
        r"\n\s*add\(\s*\n\s*Material3MenuItemData\(\s*\n(?:.*\n)*?.*?pin_to_speed_dial(?:.*\n)*?\s*\)\s*\n\s*\)\s*",
        re.M,
    ),
    # bare Material3MenuItemData( ... pin ... ),
    re.compile(
        r"\n\s*Material3MenuItemData\(\s*\n(?:.*\n)*?.*?pin_to_speed_dial(?:.*\n)*?\s*\),?",
        re.M,
    ),
    # add( NewAction( ... pin ... ) )
    re.compile(
        r"\n\s*add\(\s*\n\s*NewAction\(\s*\n(?:.*\n)*?.*?pin_to_speed_dial(?:.*\n)*?\s*\)\s*\n\s*\)\s*",
        re.M,
    ),
]

for name in [
    "SongMenu.kt",
    "YouTubeSongMenu.kt",
    "YouTubeAlbumMenu.kt",
    "ArtistMenu.kt",
    "YouTubeArtistMenu.kt",
]:
    path = menu_dir / name
    t = path.read_text(encoding="utf-8")
    if "pin_to_speed_dial" not in t:
        print(name, "already clean")
        continue
    orig = t
    for pat in patterns:
        t, n = pat.subn("\n", t)
        if n:
            print(name, "removed", n)
    if "pin_to_speed_dial" in t:
        print(name, "STILL HAS PIN — leftover context:")
        for i, line in enumerate(t.splitlines()):
            if "pin_to_speed_dial" in line or "speedDialDao" in line:
                print(f"  {i+1}: {line.strip()[:120]}")
    else:
        # cleanup unused isPinned + SpeedDialItem import if unused
        if "speedDialDao" not in t and "SpeedDialItem" not in t:
            pass
        else:
            t2 = re.sub(
                r"\n\s*val isPinned by database\.speedDialDao\.isPinned\([^\n]+",
                "",
                t,
            )
            if "speedDialDao" not in t2 and "SpeedDialItem" not in t2.replace("import", ""):
                # only remove import if no remaining SpeedDialItem usage
                if "SpeedDialItem" not in t2.split("import")[-1] if False else "SpeedDialItem(" not in t2 and "SpeedDialItem." not in t2:
                    t2 = re.sub(r"\nimport iad1tya\.echo\.music\.db\.entities\.SpeedDialItem\r?", "", t2)
            t = t2
            # remove isPinned if still there after pin gone
            if "isPinned" not in t:
                pass
            elif "speedDialDao" not in t:
                t = re.sub(r"\n\s*val isPinned by database\.speedDialDao\.isPinned\([^\n]+", "", t)
        # More careful cleanup
        if "pin_to_speed_dial" not in t:
            t = re.sub(r"\n\s*val isPinned by database\.speedDialDao\.isPinned\([^\n]+", "", t)
            if "speedDialDao" not in t and "SpeedDialItem" not in t:
                t = re.sub(r"\nimport iad1tya\.echo\.music\.db\.entities\.SpeedDialItem\r?", "", t)
            elif "SpeedDialItem" not in t:
                t = re.sub(r"\nimport iad1tya\.echo\.music\.db\.entities\.SpeedDialItem\r?", "", t)
            if "SpeedDialItem" not in t and "speedDialDao" not in t:
                t = re.sub(r"\nimport iad1tya\.echo\.music\.db\.entities\.SpeedDialItem\r?", "", t)
        path.write_text(t, encoding="utf-8")
        print(name, "cleaned OK" if "pin_to_speed_dial" not in t else "FAILED")

print("done")
