from pathlib import Path

p = Path(r"d:/7-8-26/AURA HI-RES/app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
t = p.read_text(encoding="utf-8")

if "import iad1tya.echo.music.constants.OfflineModeKey" not in t:
    old = "import iad1tya.echo.music.constants.AutoLoadMoreKey"
    new = old + "\nimport iad1tya.echo.music.constants.OfflineModeKey"
    assert old in t
    t = t.replace(old, new, 1)
    print("import OK")

old_http = (
    '            if (mediaId.startsWith("http://", ignoreCase = true) || '
    'mediaId.startsWith("https://", ignoreCase = true)) {\n'
    "                return@Factory dataSpec.withUri(mediaId.toUri())\n"
    "            }"
)
new_http = (
    '            if (mediaId.startsWith("http://", ignoreCase = true) || '
    'mediaId.startsWith("https://", ignoreCase = true)) {\n'
    "                // Offline mode: direct URLs still need the network — refuse them.\n"
    "                if (dataStore.get(OfflineModeKey, false)) {\n"
    "                    throw PlaybackException(\n"
    "                        getString(R.string.error_offline_not_downloaded),\n"
    "                        null,\n"
    "                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,\n"
    "                    )\n"
    "                }\n"
    "                return@Factory dataSpec.withUri(mediaId.toUri())\n"
    "            }"
)
if old_http in t:
    t = t.replace(old_http, new_http, 1)
    print("http gate OK")
else:
    print("http gate MISS")

marker = (
    "            // Read Room NOW — BEFORE serving any playerCache/songUrlCache hit — "
    "for the container-mismatch guard"
)
insert = (
    "            // Strict offline: ONLY a full downloadCache hit may play. playerCache / "
    "songUrlCache / YT\n"
    "            // resolve all need the network (or a URL refresh) — refuse them while "
    "OfflineModeKey is ON.\n"
    "            if (dataStore.get(OfflineModeKey, false)) {\n"
    "                throw PlaybackException(\n"
    "                    getString(R.string.error_offline_not_downloaded),\n"
    "                    null,\n"
    "                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,\n"
    "                )\n"
    "            }\n"
    "\n"
    + marker
)
if "Strict offline: ONLY a full downloadCache" not in t:
    if marker in t:
        t = t.replace(marker, insert, 1)
        print("resolve gate OK")
    else:
        print("resolve gate MISS")
else:
    print("resolve gate already")

old_radio = "    fun startRadioSeamlessly() {\n\n        if (!playerInitialized.value) {"
new_radio = (
    "    fun startRadioSeamlessly() {\n"
    "        // Offline mode: never seed radio / related / YouTube next — that is network by definition.\n"
    "        if (dataStore.get(OfflineModeKey, false)) {\n"
    "            resumeAfterSeed = false\n"
    "            advanceIntoRadioRequested = false\n"
    "            return\n"
    "        }\n"
    "\n"
    "        if (!playerInitialized.value) {"
)
if "Offline mode: never seed radio" not in t:
    if old_radio in t:
        t = t.replace(old_radio, new_radio, 1)
        print("radio gate OK")
    else:
        print("radio gate MISS")
else:
    print("radio gate already")

p.write_text(t, encoding="utf-8")
print("done")
