from pathlib import Path

p = Path("app/src/main/kotlin/com/music/echo/ui/menu/SongMenu.kt")
text = p.read_text(encoding="utf-8")
needle = "    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost\n\n    LazyColumn("
if needle not in text:
    raise SystemExit("needle not found")

insert = r'''    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    if (exportedVideoActionsOnly) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()),
        ) {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.action_share)) },
                        description = { Text(text = stringResource(R.string.share_local_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                val uri = lookupExportedFileUri(context, song.id)
                                if (uri != null &&
                                    shareContentUri(context, uri, "video/mp4")
                                ) {
                                    onDismiss()
                                    return@launch
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.export_directory_not_set),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                onDismiss()
                            }
                        },
                    ),
                    when {
                        isExporting -> Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.exporting)) },
                            icon = {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            },
                            onClick = {},
                        )
                        else -> Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.export_as_mp3)) },
                            description = { Text(text = stringResource(R.string.export_as_mp3_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.file_export),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                ensureMp3Folder { directoryUri ->
                                    onDismiss()
                                    AudioExportService.start(
                                        context = context,
                                        songId = song.id,
                                        songTitle = song.song.title,
                                        songArtist = song.artists.joinToString(", ") { it.name },
                                        songAlbum = song.song.albumName ?: "",
                                        artworkUrl = song.song.thumbnailUrl ?: "",
                                        targetDirectoryUri = directoryUri,
                                        exportAsVideo = false,
                                    )
                                }
                            },
                        )
                    },
                ),
            )
        }
        return
    }

    LazyColumn('''

text = text.replace(needle, insert, 1)
p.write_text(text, encoding="utf-8", newline="\n")
print("OK")
