package iad1tya.echo.music.legal

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.TermsAcceptedAppVersionKey
import iad1tya.echo.music.constants.TermsAcceptedAtKey
import iad1tya.echo.music.constants.TermsAcceptedVersionKey
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.theme.BrandAccent
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Aura premium dark scheme for both the blocking gate and the Ajustes terms reader. */
private val TermsAuraScheme = darkColorScheme(
    primary = AuraPalette.Teal,
    onPrimary = AuraPalette.OnAccent,
    secondary = BrandAccent,
    onSecondary = AuraPalette.OnAccent,
    background = AuraPalette.Ground,
    onBackground = AuraPalette.OnGround,
    surface = AuraPalette.Ground,
    onSurface = AuraPalette.OnGround,
    surfaceVariant = AuraPalette.GroundRaised,
    onSurfaceVariant = AuraPalette.OnGroundMuted,
    surfaceContainer = AuraPalette.GroundRaised,
    outline = AuraPalette.OnGroundDisabled,
    outlineVariant = AuraPalette.SurfaceLine,
)

/**
 * BLOCKING Terms & Conditions gate. Wraps the WHOLE app (OUTSIDE LicenseGate): until the user has
 * accepted the current [TermsInfo.TERMS_VERSION], it renders the full-screen acceptance screen
 * INSTEAD of the app content — so no NavHost, no deep-link handling, no auto-play and no license
 * UI can run behind it. Once accepted (version >= constant) it is a pure pass-through, and bumping
 * TERMS_VERSION re-shows it automatically for everyone.
 */
@Composable
fun TermsGate(appContent: @Composable () -> Unit) {
    // rememberPreference reads the stored value synchronously for its initial state, so users who
    // already accepted never see the terms screen flash on startup.
    val acceptedVersion by rememberPreference(TermsAcceptedVersionKey, defaultValue = 0)
    if (acceptedVersion >= TermsInfo.TERMS_VERSION) {
        appContent()
    } else {
        TermsAcceptanceScreen()
    }
}

/**
 * Full-screen, non-dismissable acceptance screen: complete terms text (scrollable) + a single
 * prominent "Aceptar y continuar" button, with a subtle "Salir" that closes the app.
 * TV/D-pad: the accept button takes initial focus (with the tvFocusable ring) and DPAD up/down
 * scroll the document while focus stays on the buttons.
 */
@Composable
private fun TermsAcceptanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val markdown = rememberTermsMarkdown()
    val isTvOrCar = rememberIsTvOrCar()
    val acceptFocus = remember { FocusRequester() }

    MaterialTheme(colorScheme = TermsAuraScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AuraPalette.Ground)
                .systemBarsPadding()
                // Remote/D-pad: scroll the terms with up/down while the buttons keep focus (the two
                // buttons sit side by side, so vertical focus traversal is not needed).
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            scope.launch { listState.animateScrollBy(-260f) }
                            true
                        }
                        Key.DirectionDown -> {
                            scope.launch { listState.animateScrollBy(260f) }
                            true
                        }
                        else -> false
                    }
                }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = AuraPalette.OnGround, fontWeight = FontWeight.Light)) {
                        append("AURA ")
                    }
                    withStyle(SpanStyle(color = BrandAccent, fontWeight = FontWeight.SemiBold)) {
                        append("HI-RES")
                    }
                },
                style = AuraType.SectionLabel,
                fontSize = 22.sp,
                letterSpacing = 5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.terms_title),
                style = AuraType.SheetTitle,
                color = AuraPalette.OnGround,
            )
            Spacer(Modifier.height(12.dp))

            TermsDocumentBody(
                markdown = markdown,
                listState = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = { (context as? Activity)?.finish() },
                    modifier = Modifier.tvFocusable(isTvOrCar, scaleFocused = 1f),
                ) {
                    Text(
                        text = stringResource(R.string.terms_exit),
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundGhost,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            // One transaction: accepted terms version + timestamp + app versionCode.
                            context.dataStore.edit { p ->
                                p[TermsAcceptedVersionKey] = TermsInfo.TERMS_VERSION
                                p[TermsAcceptedAtKey] = System.currentTimeMillis()
                                p[TermsAcceptedAppVersionKey] = BuildConfig.VERSION_CODE
                            }
                        }
                    },
                    modifier = Modifier
                        .focusRequester(acceptFocus)
                        .tvFocusable(isTvOrCar, scaleFocused = 1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraPalette.Teal,
                        contentColor = AuraPalette.OnAccent,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.terms_accept),
                        style = AuraType.RowTitle,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    // TV/remote: land initial focus on the accept button so a single OK press can accept.
    androidx.compose.runtime.LaunchedEffect(isTvOrCar) {
        if (isTvOrCar) runCatching { acceptFocus.requestFocus() }
    }
}

/**
 * Read-only Terms screen for Ajustes ▸ Acerca de (no accept button — the user already accepted).
 * Shows the same full text plus, at the end, when/which version was accepted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val markdown = rememberTermsMarkdown()
    val listState = rememberLazyListState()
    val acceptedVersion by rememberPreference(TermsAcceptedVersionKey, defaultValue = 0)
    val acceptedAt by rememberPreference(TermsAcceptedAtKey, defaultValue = 0L)

    MaterialTheme(colorScheme = TermsAuraScheme) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = AuraPalette.Ground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.terms_title),
                            style = AuraType.SheetTitle,
                            color = AuraPalette.OnGround,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        iad1tya.echo.music.ui.component.IconButton(
                            onClick = navController::navigateUp,
                            onLongClick = null,
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                                tint = AuraPalette.OnGround,
                            )
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = AuraPalette.Ground,
                        scrolledContainerColor = AuraPalette.GroundRaised,
                        titleContentColor = AuraPalette.OnGround,
                        navigationIconContentColor = AuraPalette.OnGround,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            TermsDocumentBody(
                markdown = markdown,
                listState = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 20.dp,
                    bottom = 32.dp,
                ),
                footer = if (acceptedAt > 0L) {
                    stringResource(
                        R.string.terms_accepted_on,
                        DateFormat.getDateInstance().format(Date(acceptedAt)),
                        acceptedVersion,
                    )
                } else {
                    null
                },
            )
        }
    }
}

/** Loads the full terms markdown from the app asset (IO thread, cached across recompositions). */
@Composable
private fun rememberTermsMarkdown(): String {
    val context = LocalContext.current
    val markdown by produceState(initialValue = "") {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(TermsInfo.ASSET_PATH).bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
    }
    return markdown
}

/**
 * Minimal renderer for the terms document — the file only uses `#`–`###` headers, `**bold**`,
 * `- ` lists, `> ` notes and `---` rules, so a tiny line-based mapping to themed [Text] inside a
 * [LazyColumn] keeps the FULL text readable without pulling in a markdown library.
 */
@Composable
private fun TermsDocumentBody(
    markdown: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    footer: String? = null,
) {
    val lines = remember(markdown) { markdown.lines() }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        items(count = lines.size, key = { it }) { index ->
            TermsLine(lines[index])
        }
        footer?.let {
            item(key = "footer") {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = AuraPalette.Divider)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.Teal,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TermsLine(raw: String) {
    val line = raw.trimEnd()
    when {
        line.isBlank() -> Spacer(Modifier.height(6.dp))
        // Explicit OnGround on every heading level. "##" already set a theme colour, but "#" and "###"
        // set none and inherited whatever LocalContentColor the container happened to provide — which
        // defaults to BLACK when no Surface supplies one, so on a dark first-run screen those headings
        // were nearly invisible while the "##" ones showed fine.
        line.startsWith("### ") -> Text(
            text = parseInlineBold(line.removePrefix("### ")),
            style = AuraType.RowTitle,
            fontWeight = FontWeight.Bold,
            color = AuraPalette.OnGround,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        line.startsWith("## ") -> Text(
            text = parseInlineBold(line.removePrefix("## ")),
            style = AuraType.RowTitle,
            fontWeight = FontWeight.Bold,
            color = AuraPalette.Teal,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
        line.startsWith("# ") -> Text(
            text = parseInlineBold(line.removePrefix("# ")),
            style = AuraType.SheetTitle,
            fontWeight = FontWeight.Bold,
            color = AuraPalette.OnGround,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        line.startsWith("---") -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = AuraPalette.Divider,
        )
        line.startsWith("> ") -> Text(
            text = parseInlineBold(line.removePrefix("> ")),
            style = AuraType.RowSubtitle,
            fontStyle = FontStyle.Italic,
            color = AuraPalette.OnGroundFaint,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
        )
        line.startsWith("- ") -> Row(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = "•",
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = parseInlineBold(line.removePrefix("- ")),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
            )
        }
        else -> Text(
            text = parseInlineBold(line),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
        )
    }
}

/** Renders `**bold**` spans of a single line; everything else passes through verbatim. */
private fun parseInlineBold(text: String): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val start = rest.indexOf("**")
        val end = if (start >= 0) rest.indexOf("**", start + 2) else -1
        if (start < 0 || end < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(rest.substring(start + 2, end))
        }
        rest = rest.substring(end + 2)
    }
}
