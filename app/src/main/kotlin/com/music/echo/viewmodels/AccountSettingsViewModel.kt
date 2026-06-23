

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.App
import iad1tya.echo.music.utils.SyncUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val syncUtils: SyncUtils,
) : ViewModel() {

    
    fun logoutAndClearSyncedContent(context: Context, onCookieChange: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            
            syncUtils.clearAllSyncedContent()

            
            App.forgetAccount(context)

            
            onCookieChange("")
        }
    }

    /**
     * User-initiated "mirror favorites from my account": makes local liked songs match the YouTube
     * account exactly (adds missing, removes local likes no longer on the account). Only called from
     * the explicit, confirmed button in Account settings.
     */
    fun mirrorFromAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.mirrorLikedSongs()
        }
    }


    fun logoutKeepData(context: Context, onCookieChange: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            App.forgetAccount(context)
            withContext(Dispatchers.Main) {
                onCookieChange("")
            }
        }
    }

    // ── Manual YouTube Music sync hub ── (user triggers each on demand; nothing runs automatically)

    /** Pull everything from the account at once: liked songs/albums, subscriptions, saved playlists, library. */
    fun syncAll() = viewModelScope.launch(Dispatchers.IO) { syncUtils.performFullSyncSuspend() }

    fun syncLikedSongs() = viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongsSuspend() }

    fun syncLikedAlbums() = viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbumsSuspend() }

    fun syncArtists() = viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptionsSuspend() }

    fun syncPlaylists() = viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylistsSuspend() }

    fun syncLibrarySongs() = viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongsSuspend() }

    fun syncUploads() = viewModelScope.launch(Dispatchers.IO) {
        syncUtils.syncUploadedSongsSuspend()
        syncUtils.syncUploadedAlbumsSuspend()
    }

}
