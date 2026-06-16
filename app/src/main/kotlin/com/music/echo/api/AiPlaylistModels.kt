package iad1tya.echo.music.api

/**
 * Data model for the text-to-playlist feature.
 *
 * These are plain (Android-free) holders shared by [AiPlaylistPrompt], [AiPlaylistParser] and
 * [AiPlaylistService] so the pure logic can be unit-tested on the JVM.
 */

/** A track proposed by the AI, to be resolved against the local/YouTube catalog. */
data class TrackQuery(val title: String, val artist: String)

/** Parsed AI response: a short playlist name plus the proposed tracks. */
data class AiPlaylistSpec(val name: String, val tracks: List<TrackQuery>)

/** One chat message (role + content) for an OpenAI-compatible chat completion. */
data class ChatMessage(val role: String, val content: String)
