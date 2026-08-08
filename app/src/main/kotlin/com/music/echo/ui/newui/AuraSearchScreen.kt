package iad1tya.echo.music.ui.newui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.utils.YouTubeUrlParser
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalIsPlayerExpanded
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.PauseSearchHistoryKey
import iad1tya.echo.music.constants.SearchSource
import iad1tya.echo.music.constants.SearchSourceKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.SearchHistory
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * # Buscar — "Interfaz nueva"
 *
 * The fourth full screen of the redesign and, with Inicio, the most-visited one: it is a bottom-bar
 * tab that had never been rebuilt at all ("cuando toco el botón de inicio y paso al de buscar no me
 * sale personalizado").
 *
 * ## The classic Buscar is FOUR files, not one
 *  · `SearchScreen.kt` — the tab itself: the search bar, the mic, the biblioteca/en-línea source
 *    toggle, and three content tabs (Explorar / Sugerencias / Álbum).
 *  · `OnlineSearchScreen.kt` — the panel that replaces those tabs while the bar is active: search
 *    history (with a per-row delete), YouTube's own query suggestions, and the "mejor resultado"
 *    items. Rebuilt as [AuraOnlineSearchSuggestions].
 *  · `LocalSearchScreen.kt` — the same panel when the source is the LIBRARY: filter chips and live
 *    database results. Rebuilt as [AuraLocalSearchResults].
 *  · `OnlineSearchResult.kt` — the RESULTS route (`search/{query}`), with its own bar and its eight
 *    filter chips. Rebuilt as [AuraSearchResultScreen].
 *
 * All four are presentation only. The queries, the debounce, the link parsing, the history writes and
 * the podcast search all stay in `OnlineSearchSuggestionViewModel`, `LocalSearchViewModel`,
 * `OnlineSearchViewModel`, `MoodAndGenresViewModel`, `ExploreViewModel` and `SuggestionsViewModel` —
 * the same instances the classic screens use, so a fix lands once.
 *
 * ## What is deliberately different
 *  · **The source toggle is not drawn while "Modo sin conexión" is on.** The classic still draws it
 *    there and tapping it flips `SearchSourceKey` while `effectiveSource` stays LOCAL — a control that
 *    visibly changes nothing. Offline, the new bar shows a non-interactive [AuraIcons.CloudOff]
 *    marker instead; the stored preference is untouched and the toggle returns the moment offline mode
 *    is switched off.
 *  · **The field is focused when the panel opens.** The classic builds a `FocusRequester`, never
 *    attaches it to anything and then calls `requestFocus()` inside a `try/catch` that always throws —
 *    i.e. its "focus on first launch" has never worked. Rather than resurrect a behaviour nobody has
 *    ever seen, the new bar focuses the field when the user opens the panel, which is when a keyboard
 *    is actually wanted.
 */
@Composable
fun AuraSearchScreen(
    navController: NavController,
    pureBlack: Boolean,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    // "Modo sin conexión" forces the entry to the local database and never hits the network — the same
    // term the classic screen computes, read from the same key.
    val offlineMode by rememberPreference(OfflineModeKey, false)
    val effectiveSource = if (offlineMode) SearchSource.LOCAL else searchSource
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    val closePanel = {
        searchActive = false
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val onSearch: (String) -> Unit = { searchQuery ->
        focusManager.clearFocus()
        keyboardController?.hide()
        auraRunSearch(
            rawQuery = searchQuery,
            playerConnection = playerConnection,
            navController = navController,
            database = database,
            scope = coroutineScope,
            offlineMode = offlineMode,
            pauseSearchHistory = pauseSearchHistory,
            navigateToResults = { encoded -> navController.navigate("search/$encoded") },
        )
    }

    val voice = rememberAuraVoiceSearch(
        onPartial = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
        },
        onResult = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
            searchActive = true
            onSearch(spoken)
        },
    )

    // Closing the panel with Atrás instead of leaving the tab — the classic bar's back arrow does the
    // same thing, this only adds the system gesture to it.
    BackHandler(enabled = searchActive) { closePanel() }

    LaunchedEffect(searchActive) {
        if (searchActive) runCatching { focusRequester.requestFocus() }
    }

    // Buscar has no now-playing context of its own; the render dims the bloom on the denser screens.
    val bloom = rememberAuraBloom(null)

    val currentInsets = LocalPlayerAwareWindowInsets.current
    val topInsetOnly = remember(currentInsets) { currentInsets.only(WindowInsetsSides.Top) }
    // The header + bar + chips consume the top inset themselves; the body must not reserve it again.
    val bodyInsets = remember(currentInsets) {
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getLeft(density, layoutDirection)

            override fun getTop(density: Density) = 0

            override fun getRight(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getRight(density, layoutDirection)

            override fun getBottom(density: Density) = currentInsets.getBottom(density)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                // Pure black is a real user setting: drop the ground AND the bloom rather than paint
                // black over a bloom computed for nothing (same rule as the new Cola).
                if (pureBlack) Modifier.background(Color.Black)
                else Modifier.auraScreenBackground(bloom, intensity = 0.40f)
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(topInsetOnly),
        ) {
            AuraScreenHeader(
                title = stringResource(R.string.search),
                trailing = { AuraTopActions() },
            )

            AuraSearchInputBar(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(
                    when (effectiveSource) {
                        SearchSource.LOCAL -> R.string.search_library
                        SearchSource.ONLINE -> R.string.search_yt_music
                    }
                ),
                active = searchActive,
                onLeadingClick = {
                    if (searchActive) {
                        query = TextFieldValue("")
                        closePanel()
                    } else {
                        searchActive = true
                    }
                },
                onSubmit = {
                    onSearch(query.text)
                    // Offline: keep the panel open so the live library results stay visible.
                    if (!offlineMode) closePanel()
                },
                onClear = { query = TextFieldValue("") },
                onVoice = voice,
                offlineMode = offlineMode,
                sourceIsLocal = effectiveSource == SearchSource.LOCAL,
                onToggleSource = {
                    searchSource = if (searchSource == SearchSource.ONLINE) {
                        SearchSource.LOCAL
                    } else {
                        SearchSource.ONLINE
                    }
                },
                focusRequester = focusRequester,
                onFieldTap = { searchActive = true },
            )

            AnimatedVisibility(
                visible = !searchActive,
                enter = expandVertically(animationSpec = AuraMotion.intSize) +
                    fadeIn(animationSpec = AuraMotion.float),
                exit = shrinkVertically(animationSpec = AuraMotion.intSize) +
                    fadeOut(animationSpec = AuraMotion.float),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Gutter, vertical = 2.dp),
                ) {
                    auraSearchTabs().forEachIndexed { index, labelRes ->
                        AuraChip(
                            text = stringResource(labelRes),
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides bodyInsets) {
                Box(Modifier.fillMaxSize()) {
                    if (searchActive) {
                        when (effectiveSource) {
                            SearchSource.LOCAL -> AuraLocalSearchResults(
                                query = query.text,
                                navController = navController,
                                onDismiss = { closePanel() },
                            )

                            SearchSource.ONLINE -> AuraOnlineSearchSuggestions(
                                query = query.text,
                                onQueryChange = { query = it },
                                navController = navController,
                                onSearch = { picked ->
                                    onSearch(picked)
                                    if (!offlineMode) closePanel()
                                },
                                onDismiss = { closePanel() },
                            )
                        }
                    } else {
                        when (selectedTabIndex) {
                            0 -> AuraExploreTab(navController)
                            1 -> AuraTrendingTab(navController)
                            else -> AuraNewAlbumsTab(navController)
                        }
                    }
                }
            }
        }
    }

    // Same keyboard hygiene as the classic screen: never leave a keyboard up behind the expanded
    // player, and drop focus when the app is backgrounded.
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (isPlayerExpanded) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** The three content tabs of Buscar, in the classic order and with the classic labels. */
private fun auraSearchTabs(): List<Int> = listOf(
    R.string.tab_explore,
    R.string.tab_Suggestions,
    R.string.tab_album,
)

// ── The bar ───────────────────────────────────────────────────────────────────────────────────────

/**
 * The render's search field: a `SurfaceFill` card with a hairline, holding every control the classic
 * `SearchBar` carried — leading search/back glyph, the field itself, clear, mic, and the
 * biblioteca/en-línea source toggle.
 */
@Composable
internal fun AuraSearchInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    active: Boolean,
    onLeadingClick: () -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    focusRequester: FocusRequester,
    onFieldTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** The results bar has no source picker — its route is online by definition. */
    showSource: Boolean = true,
    offlineMode: Boolean = false,
    sourceIsLocal: Boolean = false,
    onToggleSource: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 12.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(start = 4.dp, end = 4.dp),
    ) {
        AuraIconButton(
            icon = if (active) AuraIcons.ChevronRight else AuraIcons.Search,
            contentDescription = if (active) {
                stringResource(R.string.dismiss)
            } else {
                stringResource(R.string.search)
            },
            onClick = onLeadingClick,
            size = 18.dp,
            tint = AuraPalette.OnGroundFaint,
            // The chevron points BACK when the panel is open — one glyph, rotated, exactly as the
            // library's close control reuses "+" at 45°.
            modifier = Modifier.graphicsLayer { rotationZ = if (active) 180f else 0f },
        )

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AuraType.RowTitle.copy(color = AuraPalette.OnGround),
                cursorBrush = SolidColor(AuraPalette.Teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    // Focusing the field IS opening the panel — no second clickable on top of the
                    // text field, which would swallow the tap that gives it focus in the first place.
                    .onFocusChanged { if (it.isFocused) onFieldTap() },
            )
        }

        if (value.text.isNotEmpty()) {
            AuraIconButton(
                // The render's own close glyph is a "+" turned 45°.
                icon = AuraIcons.Plus,
                contentDescription = stringResource(R.string.search_clear_query),
                onClick = onClear,
                size = 16.dp,
                tint = AuraPalette.OnGroundFaint,
                modifier = Modifier.graphicsLayer { rotationZ = 45f },
            )
        }

        AuraIconButton(
            icon = AuraIcons.Mic,
            contentDescription = stringResource(R.string.voice_search),
            onClick = onVoice,
            size = 18.dp,
            tint = AuraPalette.OnGroundFaint,
        )

        if (showSource) {
            if (offlineMode) {
                // Not a control: offline mode pins the source to the library, so a toggle here would
                // be a button that changes nothing on screen. This states WHY instead.
                AuraIconGlyph(
                    icon = AuraIcons.CloudOff,
                    contentDescription = stringResource(R.string.offline_mode),
                    size = 18.dp,
                    tint = AuraPalette.Teal,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            } else {
                AuraIconButton(
                    icon = if (sourceIsLocal) AuraIcons.Library else AuraIcons.Cloud,
                    contentDescription = stringResource(
                        if (sourceIsLocal) R.string.search_online else R.string.search_library
                    ),
                    onClick = onToggleSource,
                    size = 18.dp,
                    tint = if (sourceIsLocal) AuraPalette.Teal else AuraPalette.OnGroundFaint,
                )
            }
        }
    }
}

// ── Voice search ──────────────────────────────────────────────────────────────────────────────────

/**
 * Voice search: prefer in-process [SpeechRecognizer] + Aura listening UI (partial results fill the
 * search field live). [RecognizerIntent] is last resort when SpeechRecognizer is unavailable.
 * RECORD_AUDIO is requested on demand for the direct path.
 *
 * The dialog is emitted from here so both the Buscar bar and the results bar get it from one place.
 */
@Composable
internal fun rememberAuraVoiceSearch(
    onPartial: ((String) -> Unit)? = null,
    onResult: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current

    val intentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (!spoken.isNullOrEmpty()) onResult(spoken)
    }

    var listening by remember { mutableStateOf(false) }
    var liveText by remember { mutableStateOf("") }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    val startDirect = {
        val speech = recognizer
        if (speech == null) {
            Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT).show()
        } else {
            speech.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    listening = false
                    liveText = ""
                    if (error == SpeechRecognizer.ERROR_CLIENT) return
                    Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onResults(results: android.os.Bundle) {
                    listening = false
                    liveText = ""
                    val spoken = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!spoken.isNullOrEmpty()) onResult(spoken)
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val spoken = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isEmpty()) return
                    liveText = spoken
                    onPartial?.invoke(spoken)
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
            liveText = ""
            listening = true
            speech.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                },
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startDirect()
        } else {
            Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    val requestDirect = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startDirect()
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    if (listening) {
        DefaultDialog(
            onDismiss = {
                listening = false
                liveText = ""
                recognizer?.cancel()
            },
            title = {
                Text(
                    text = stringResource(R.string.voice_search),
                    style = AuraType.RowTitle,
                    color = AuraPalette.OnGround,
                )
            },
            buttons = {
                TextButton(onClick = {
                    listening = false
                    liveText = ""
                    recognizer?.cancel()
                }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            horizontalAlignment = Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = AuraPalette.Teal,
                    trackColor = AuraPalette.TrackEmpty,
                    strokeWidth = 2.dp,
                    modifier = Modifier.width(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.listening),
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                    )
                    if (liveText.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = liveText,
                            style = AuraType.RowTitle,
                            color = AuraPalette.OnGround,
                        )
                    }
                }
            }
        }
    }

    return {
        if (recognizer != null) {
            requestDirect()
        } else {
            try {
                intentLauncher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(
                            RecognizerIntent.EXTRA_PROMPT,
                            context.getString(R.string.voice_search),
                        )
                    },
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}

// ── The one search action, shared by the bar and the results bar ───────────────────────────────────

/**
 * Submitting a query. Term for term the classic `onSearch`:
 *  · a YouTube VIDEO link plays straight away, an ARTIST link navigates to the artist,
 *  · anything else opens the results route — unless "Modo sin conexión" is on, in which case the panel
 *    already shows live library results and no network page is opened,
 *  · and the query is written to the history unless "Pausar el historial" is on.
 *
 * [navigateToResults] receives the URL-ENCODED query, because the two call sites navigate differently
 * (the tab pushes, the results screen replaces itself with `popUpTo`).
 */
internal fun auraRunSearch(
    rawQuery: String,
    playerConnection: PlayerConnection?,
    navController: NavController,
    database: MusicDatabase,
    scope: CoroutineScope,
    offlineMode: Boolean,
    pauseSearchHistory: Boolean,
    navigateToResults: (String) -> Unit,
) {
    if (rawQuery.isEmpty()) return

    when (val parsed = YouTubeUrlParser.parse(rawQuery)) {
        is YouTubeUrlParser.ParsedUrl.Video ->
            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = parsed.id)))

        is YouTubeUrlParser.ParsedUrl.Artist ->
            navController.navigate("artist/${parsed.id}")

        null -> if (!offlineMode) {
            navigateToResults(URLEncoder.encode(rawQuery, "UTF-8"))
        }
    }

    if (!pauseSearchHistory) {
        scope.launch(Dispatchers.IO) {
            database.query { insert(SearchHistory(query = rawQuery)) }
        }
    }
}
