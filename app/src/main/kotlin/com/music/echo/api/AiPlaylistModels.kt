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

/**
 * Parsed AI response for an "edit this playlist" request.
 *
 * [removeIndices] are 0-BASED POSITIONS into the track snapshot that was sent to the model (see
 * [AiPlaylistPrompt.buildModifyMessages], which numbers them 1-based for the model and whose parser
 * normalizes them back). They are deliberately NOT database ids: no Room autoincrement id is ever
 * exposed to the AI, so a bogus number can only be an out-of-range position, never a pointer at an
 * unrelated row. Already validated/deduped by [AiPlaylistParser.parseEdit].
 */
data class AiPlaylistEdit(val removeIndices: List<Int>, val additions: List<TrackQuery>)

/** One chat message (role + content) for an OpenAI-compatible chat completion. */
data class ChatMessage(val role: String, val content: String)
