from pathlib import Path
p = Path(r"d:\7-8-26\AURA HI-RES\app\src\main\kotlin\com\music\echo\ui\newui\AuraOnlinePlaylistScreen.kt")
t = p.read_text(encoding="utf-8")
if "import androidx.compose.ui.draw.clip" not in t:
    t = t.replace(
        "import androidx.compose.ui.Alignment\n",
        "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.draw.clip\n",
        1,
    )
needle = (
    "                if (hasExplicitContent) {\n"
    "                    append(\" • \")\n"
    "                    append(stringResource(R.string.explicit))\n"
    "                }\n"
    "            },\n"
    "            style = AuraType.RowSubtitle,\n"
    "            color = AuraPalette.OnGroundMuted,\n"
    "            maxLines = 1,\n"
    "            overflow = AuraDefaultOverflow,\n"
    "        )\n"
    "\n"
    "        Spacer(Modifier.height(18.dp))\n"
)
insert = (
    "                if (hasExplicitContent) {\n"
    "                    append(\" • \")\n"
    "                    append(stringResource(R.string.explicit))\n"
    "                }\n"
    "                if (videoPlaylist) {\n"
    "                    append(\" • \")\n"
    "                    append(auraTypeLabel(AuraContentKind.Video))\n"
    "                }\n"
    "            },\n"
    "            style = AuraType.RowSubtitle,\n"
    "            color = AuraPalette.OnGroundMuted,\n"
    "            maxLines = 1,\n"
    "            overflow = AuraDefaultOverflow,\n"
    "        )\n"
    "\n"
    "        if (videoPlaylist) {\n"
    "            Spacer(Modifier.height(8.dp))\n"
    "            Row(\n"
    "                verticalAlignment = Alignment.CenterVertically,\n"
    "                horizontalArrangement = Arrangement.spacedBy(6.dp),\n"
    "                modifier = Modifier\n"
    "                    .clip(AuraShapes.Pill)\n"
    "                    .background(AuraPalette.Teal.copy(alpha = 0.14f))\n"
    "                    .padding(horizontal = 10.dp, vertical = 5.dp),\n"
    "            ) {\n"
    "                AuraIconGlyph(\n"
    "                    icon = AuraIcons.Video,\n"
    "                    contentDescription = null,\n"
    "                    size = 12.dp,\n"
    "                    tint = AuraPalette.Teal,\n"
    "                )\n"
    "                Text(\n"
    "                    text = \"Lista de vídeos\",\n"
    "                    style = AuraType.QualityBadge,\n"
    "                    color = AuraPalette.Teal,\n"
    "                    maxLines = 1,\n"
    "                )\n"
    "            }\n"
    "        }\n"
    "\n"
    "        Spacer(Modifier.height(18.dp))\n"
)
if needle not in t:
    raise SystemExit("needle missing")
p.write_text(t.replace(needle, insert, 1), encoding="utf-8")
print("ok")
