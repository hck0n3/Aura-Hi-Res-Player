from pathlib import Path

p = Path(r"app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
t = p.read_text(encoding="utf-8")
old = """        if (_videoMode.value && mediaItem != null) {
            val newId = mediaItem.mediaId
            val prebuilt = videoModeItems[newId]
            if (prebuilt != null) {
                _videoUrl.value = prebuilt.videoUrl
                restoreVideoTracksExcept(newId)   // restore previous video track(s) to audio; refreshes single-field state
            } else if (newId != videoModeMediaId) {
                applyVideoToCurrent()
            }
        }
        // PRE-BUILD the NEXT track"""
new = """        if (_videoMode.value && mediaItem != null) {
            val newId = mediaItem.mediaId
            val prebuilt = videoModeItems[newId]
            if (prebuilt != null) {
                _videoUrl.value = prebuilt.videoUrl
                restoreVideoTracksExcept(newId)   // restore previous video track(s) to audio; refreshes single-field state
            } else if (newId != videoModeMediaId) {
                applyVideoToCurrent()
            }
        } else if (
            // Owner: tapping a VIDEO starts playback IN video mode (manual SEEK / new queue only —
            // AUTO advances stay audio unless sticky video was already on above).
            mediaItem != null &&
            (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) &&
            player.currentMetadata?.isVideoSong == true &&
            !(iad1tya.echo.music.utils.PerformanceMode.isOn(this) &&
                !iad1tya.echo.music.utils.DeviceForm.isTvOrCar(this))
        ) {
            userHasUsedVideo = true
            videoSwapMeasureStart()
            if (!tryInstantVideoSwap()) {
                teardownInstantVideoSwap("auto video on manual video tap")
                _videoMode.value = true
                applyVideoToCurrent()
            }
            val nextIdx = player.nextMediaItemIndex
            if (nextIdx != C.INDEX_UNSET) {
                runCatching { player.getMediaItemAt(nextIdx).mediaId }.getOrNull()
                    ?.let { prebuildNextVideoItem(nextIdx, it) }
            }
        }
        // PRE-BUILD the NEXT track"""
if old not in t:
    raise SystemExit("FAIL: needle not found")
p.write_text(t.replace(old, new, 1), encoding="utf-8")
print("OK")
