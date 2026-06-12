package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareLinksTest {

    @Test
    fun songLinkIsDirectYouTubeMusicWatchUrl() {
        assertEquals(
            "https://music.youtube.com/watch?v=_KZEkEb_dvA",
            ShareLinks.song("_KZEkEb_dvA")
        )
    }

    @Test
    fun playlistLinkIsDirectYouTubeMusicPlaylistUrl() {
        assertEquals(
            "https://music.youtube.com/playlist?list=PLabc123",
            ShareLinks.playlist("PLabc123")
        )
    }

    @Test
    fun channelLinkIsDirectYouTubeMusicChannelUrl() {
        assertEquals(
            "https://music.youtube.com/channel/UCxyz",
            ShareLinks.channel("UCxyz")
        )
    }
}
