package com.music.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Test

class YTItemShareLinkTest {

    @Test
    fun songShareLinkPointsToYouTubeMusic() {
        val song = SongItem(
            id = "_KZEkEb_dvA",
            title = "song",
            artists = emptyList(),
            thumbnail = "thumb"
        )
        assertEquals("https://music.youtube.com/watch?v=_KZEkEb_dvA", song.shareLink)
    }

    @Test
    fun albumShareLinkUsesAudioPlaylistId() {
        val album = AlbumItem(
            browseId = "MPREb_abc",
            playlistId = "OLAK5uy_def",
            title = "album",
            artists = null,
            thumbnail = "thumb"
        )
        assertEquals("https://music.youtube.com/playlist?list=OLAK5uy_def", album.shareLink)
    }

    @Test
    fun playlistShareLinkUsesPlaylistId() {
        val playlist = PlaylistItem(
            id = "PLxyz",
            title = "playlist",
            author = null,
            songCountText = null,
            thumbnail = null,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null
        )
        assertEquals("https://music.youtube.com/playlist?list=PLxyz", playlist.shareLink)
    }

    @Test
    fun artistShareLinkUsesChannelId() {
        val artist = ArtistItem(
            id = "UCabc",
            title = "artist",
            thumbnail = null,
            shuffleEndpoint = null,
            radioEndpoint = null
        )
        assertEquals("https://music.youtube.com/channel/UCabc", artist.shareLink)
    }
}
