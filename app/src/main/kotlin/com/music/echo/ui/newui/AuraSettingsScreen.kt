package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.NewUiEnabledKey
import iad1tya.echo.music.license.LicenseLogic
import iad1tya.echo.music.license.LicenseManager
import iad1tya.echo.music.license.SubscriptionEntryScreen
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.ui.screens.settings.getAllSearchableSettings
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.rememberPreference

/**
 * # Ajustes — "Interfaz nueva"
 *
 * The grouped settings index of the reference render (`8-DISENO-REAL-con-iconos.html`, the "Ajustes"
 * cell): screen title, a "Buscar un ajuste" field, seven teal-iconed groups with a chevron, and the
 * violet "Interfaz nueva" callout that switches the whole beta back off.
 *
 * ## What this screen is and is not
 * It is **presentation only**. Every row navigates to the SAME route the classic `SettingsScreen`
 * navigates to, every label reuses the SAME string reference or literal the classic screen uses, and
 * the settings-search results come from the SAME [getAllSearchableSettings] index. There is no second
 * copy of anything: the classic sub-screens (Apariencia, Contenido, Reproductor, …) are reused as-is
 * behind this index, so a fix in one is a fix in both.
 *
 * ## Reachability contract
 * The classic index has 17 rows. All 17 are here, plus two destinations the classic index could not
 * reach:
 *  · **Ajustes de Escuchar juntos** (`settings/integrations/listen_together`) — the classic row goes to
 *    the Listen Together *main screen*, while 31 entries of the search index point at the *settings*
 *    screen. That screen had no index row at all. It now has one, inside the "Escuchar juntos" group.
 *  · **Migrar listas** (`migration`) — previously only reachable from the Library "Importar" menu; it is
 *    what the render's "Copias y migración" group is named after.
 *
 * ## The one deviation from the render, stated out loud
 * The render draws seven groups. Four classic destinations — Apariencia, Estadísticas, Registros and
 * Acerca de — belong to none of those seven, and burying them under an unrelated header would be worse
 * than showing them. They sit in a trailing **MÁS** section, using the render's own section-label
 * vocabulary (the merged player menu groups its leftovers under "MÁS" too). Seven groups, then MÁS.
 *
 * ## Escape hatch
 * The "Interfaz nueva" switch is duplicated here on purpose — the identical switch also lives in the
 * classic screen. Whichever UI is showing, the way back is on screen. It writes ONE boolean
 * ([NewUiEnabledKey]) and touches nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraSettingsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    // ── Suscripción Pro ───────────────────────────────────────────────────────────────────────────
    // Identical to the classic screen: an inline sub-screen (not a route) that presents the existing,
    // already-audited subscription entry flow. Nothing about the licence/grace/subscription logic is
    // touched here — this only shows it.
    var showSubscribe by rememberSaveable { mutableStateOf(false) }
    val subState = remember { LicenseManager.lastResolvedState(context) }
    val showSubscribeEntry = BuildConfig.REQUIRE_SUBSCRIPTION &&
        subState != LicenseLogic.AppState.SUBSCRIPTION_ACTIVE
    BackHandler(showSubscribe) { showSubscribe = false }
    if (showSubscribe) {
        SubscriptionEntryScreen(
            onActivated = { showSubscribe = false },
            onBack = { showSubscribe = false },
        )
        return
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchLower = searchQuery.lowercase()

    // ── Labels ────────────────────────────────────────────────────────────────────────────────────
    // Reused verbatim from the classic screen. Where the classic screen hardcodes Spanish in Kotlin the
    // literal is copied character for character; where it uses a string resource the same resource is
    // used. Retyping a label would silently change the Spanish.
    val accountsText = "Cuentas"
    val scrobblingText = stringResource(R.string.scrobbling)
    val appearanceText = stringResource(R.string.appearance)
    val playerText = "Ajustes del reproductor"
    val soundText = "Sonido y ecualización"
    val performanceText = "Rendimiento"
    val listenTogetherText = stringResource(R.string.listen_together)
    val contentText = stringResource(R.string.content)
    val aiLyricsText = stringResource(R.string.ai_lyrics_translation)
    val privacyText = stringResource(R.string.privacy)
    val storageText = stringResource(R.string.storage)
    val backupText = stringResource(R.string.backup_restore)
    val migrateText = stringResource(R.string.migrate_playlist)
    val statsText = stringResource(R.string.stats)
    val logsText = stringResource(R.string.logs)
    val logsDescText = stringResource(R.string.logs_desc)
    val aboutText = stringResource(R.string.about)
    val updateText = "Aura Hi-Res Update"
    val subscribeText = "Suscripción Pro"
    val settingsWordText = stringResource(R.string.settings)

    // Group headers. "Reproductor y sonido", "Almacenamiento", "Escuchar juntos" and "Privacidad"
    // already exist as string resources with exactly the render's wording — reuse them. The other three
    // are new group names introduced by the render and have no classic equivalent.
    val groupPlayerSound = stringResource(R.string.player_and_audio)
    val groupContentLyrics = "Contenido y letras"
    val groupAccounts = "Cuentas y sincronización"
    val groupBackupMigration = "Copias y migración"

    val noResultsText = stringResource(R.string.settings_search_no_results, searchQuery)

    // Wording taken verbatim from the reference render — do not paraphrase.
    val newUiText = "Interfaz nueva"
    val newUiSubtitle = "Apágala y vuelve a la clásica"
    val (newUiEnabled, onNewUiEnabledChange) = rememberPreference(NewUiEnabledKey, defaultValue = false)

    // ── The index ─────────────────────────────────────────────────────────────────────────────────
    val groups = buildList {
        add(
            AuraSettingsGroup(
                icon = AuraIcons.Equalizer,
                title = groupPlayerSound,
                entries = listOf(
                    AuraSettingsEntry(playerText) { navController.navigate("settings/player") },
                    AuraSettingsEntry(soundText) { navController.navigate("settings/sound") },
                    AuraSettingsEntry(performanceText) { navController.navigate("settings/performance") },
                ),
            )
        )
        add(
            AuraSettingsGroup(
                icon = AuraIcons.Lyrics,
                title = groupContentLyrics,
                entries = listOf(
                    AuraSettingsEntry(contentText) { navController.navigate("settings/content") },
                    AuraSettingsEntry(aiLyricsText) { navController.navigate("settings/ai") },
                ),
            )
        )
        add(
            AuraSettingsGroup(
                icon = AuraIcons.Artist,
                title = groupAccounts,
                entries = buildList {
                    if (showSubscribeEntry) {
                        add(
                            AuraSettingsEntry(
                                title = subscribeText,
                                description = "Suscríbete o ingresa tu clave de licencia",
                            ) { showSubscribe = true }
                        )
                    }
                    add(AuraSettingsEntry(accountsText) { navController.navigate("settings/accounts") })
                    add(
                        AuraSettingsEntry(scrobblingText, description = "Last.fm & ListenBrainz") {
                            navController.navigate("settings/lastfm")
                        }
                    )
                    add(
                        AuraSettingsEntry(
                            title = updateText,
                            description = "Cambios de esta versión y nuevas actualizaciones",
                        ) { navController.navigate("settings/update") }
                    )
                },
            )
        )
        add(
            AuraSettingsGroup(
                icon = AuraIcons.Download,
                title = storageText,
                entries = listOf(
                    AuraSettingsEntry(storageText) { navController.navigate("settings/storage") },
                ),
            )
        )
        add(
            AuraSettingsGroup(
                icon = AuraIcons.People,
                title = listenTogetherText,
                entries = listOf(
                    // The classic row: opens the Listen Together MAIN screen.
                    AuraSettingsEntry(listenTogetherText) {
                        navController.navigate(Screens.ListenTogether.route)
                    },
                    // The gap this beta closes: the SETTINGS screen of Listen Together, which 31 entries
                    // of the search index already point at but which had no index row anywhere.
                    AuraSettingsEntry(settingsWordText) {
                        navController.navigate("settings/integrations/listen_together")
                    },
                ),
            )
        )
        add(
            AuraSettingsGroup(
                icon = AuraIcons.Album,
                title = groupBackupMigration,
                entries = listOf(
                    AuraSettingsEntry(backupText) { navController.navigate("settings/backup_restore") },
                    AuraSettingsEntry(migrateText) { navController.navigate("migration") },
                ),
            )
        )
        add(
            AuraSettingsGroup(
                icon = AuraIcons.Settings,
                title = privacyText,
                entries = listOf(
                    AuraSettingsEntry(privacyText) { navController.navigate("settings/privacy") },
                ),
            )
        )
    }

    // The four destinations that belong to none of the render's seven groups. See the KDoc.
    val more = listOf(
        AuraSettingsGroup(
            icon = AuraIcons.Settings,
            title = appearanceText,
            entries = listOf(AuraSettingsEntry(appearanceText) { navController.navigate("settings/appearance") }),
        ),
        AuraSettingsGroup(
            icon = AuraIcons.Equalizer,
            title = statsText,
            entries = listOf(AuraSettingsEntry(statsText) { navController.navigate("stats") }),
        ),
        AuraSettingsGroup(
            icon = AuraIcons.Lyrics,
            title = logsText,
            entries = listOf(
                AuraSettingsEntry(logsText, description = logsDescText) {
                    navController.navigate("settings/logs")
                }
            ),
        ),
        AuraSettingsGroup(
            icon = AuraIcons.Album,
            title = aboutText,
            entries = listOf(AuraSettingsEntry(aboutText) { navController.navigate("settings/about") }),
        ),
    )

    // Only one group is open at a time — a settings index that can be fully unfolded is a wall of rows.
    var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }

    val bloom = rememberAuraBloom(mediaId = null)
    val scrollState = rememberScrollState()

    Box(
        Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 0.32f) // render: `.bl { opacity:.32 }` on Ajustes
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .verticalScroll(scrollState)
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )

            // Back arrow. The render does not draw one (it draws the app-wide bottom bar instead, which
            // this screen does not own), but the inventory records it as a constant control WITH a
            // long-press that jumps home — so it is carried over rather than dropped.
            AuraBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                modifier = Modifier.padding(start = AuraSpacing.Gutter - 14.dp, top = 4.dp),
            )

            Text(
                text = settingsWordText,
                style = AuraType.ScreenTitle,
                color = AuraPalette.OnGround,
                modifier = Modifier.padding(
                    start = AuraSpacing.Gutter,
                    end = AuraSpacing.Gutter,
                    top = 6.dp,
                ),
            )

            AuraSettingsSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.padding(
                    start = AuraSpacing.Gutter,
                    end = AuraSpacing.Gutter,
                    top = 15.dp,
                ),
            )

            Spacer(Modifier.height(18.dp))

            if (searchQuery.isEmpty()) {
                // ── The grouped index ─────────────────────────────────────────────────────────────
                groups.forEach { group ->
                    AuraSettingsGroupRow(
                        group = group,
                        expanded = expandedGroup == group.title,
                        onToggle = {
                            expandedGroup = if (expandedGroup == group.title) null else group.title
                        },
                    )
                }

                AuraMenuGroupLabel("MÁS")
                more.forEach { group ->
                    AuraSettingsGroupRow(group = group, expanded = false, onToggle = {})
                }
            } else {
                // ── Search ────────────────────────────────────────────────────────────────────────
                // Same two-tier behaviour as the classic screen: the index rows themselves, then every
                // individual sub-setting from the SHARED index. `getAllSearchableSettings()` is the one
                // and only source — this screen does not keep a second copy.
                val topMatches = (groups + more).flatMap { group ->
                    group.entries
                        .filter {
                            it.title.lowercase().contains(searchLower) ||
                                group.title.lowercase().contains(searchLower)
                        }
                        .map { entry -> group.title to entry }
                }
                val subMatches = getAllSearchableSettings()
                    .filter { it.first.lowercase().contains(searchLower) }

                val newUiMatches = newUiText.lowercase().contains(searchLower) ||
                    newUiSubtitle.lowercase().contains(searchLower)

                if (topMatches.isEmpty() && subMatches.isEmpty() && !newUiMatches) {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = noResultsText,
                        style = AuraType.MenuLabel,
                        color = AuraPalette.OnGroundMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AuraSpacing.Gutter),
                    )
                } else {
                    topMatches.forEach { (section, entry) ->
                        AuraSettingsIndexRow(
                            icon = AuraIcons.Search,
                            title = entry.title,
                            description = entry.description ?: section,
                            onClick = entry.onClick,
                        )
                    }
                    subMatches.forEach { (title, parentTitle, route) ->
                        AuraSettingsIndexRow(
                            icon = AuraIcons.Search,
                            title = title,
                            description = parentTitle,
                            onClick = { navController.navigate(route) },
                        )
                    }
                }
            }

            // ── "Interfaz nueva" ──────────────────────────────────────────────────────────────────
            // Guarded by the same beta gate as the classic copy. In practice this screen is only
            // reachable when the flag is already on — which cannot happen in a stable build — but the
            // guard stays so the two copies of the switch can never disagree about when it exists.
            if (NEW_UI_SWITCH_VISIBLE &&
                (searchQuery.isEmpty() ||
                    newUiText.lowercase().contains(searchLower) ||
                    newUiSubtitle.lowercase().contains(searchLower))
            ) {
                AuraBetaCallout(
                    title = newUiText,
                    subtitle = newUiSubtitle,
                    checked = newUiEnabled,
                    onCheckedChange = onNewUiEnabledChange,
                    modifier = Modifier.padding(
                        start = AuraSpacing.Gutter - 4.dp,
                        end = AuraSpacing.Gutter - 4.dp,
                        top = 18.dp,
                    ),
                )
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

// ── Model ─────────────────────────────────────────────────────────────────────────────────────────

/** One navigable destination. [onClick] is the classic screen's own lambda — never a reimplementation. */
private class AuraSettingsEntry(
    val title: String,
    val description: String? = null,
    val onClick: () -> Unit,
)

/**
 * One row of the render's grouped index. A group with a single [entries] element navigates straight
 * there (its chevron keeps pointing right, which is what the render draws); a group with several
 * unfolds in place and its chevron rotates down.
 */
private class AuraSettingsGroup(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val entries: List<AuraSettingsEntry>,
)

// ── Rows ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AuraSettingsGroupRow(
    group: AuraSettingsGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val single = group.entries.size == 1
    val rotation by animateFloatAsState(
        targetValue = if (!single && expanded) 90f else 0f,
        label = "aura-settings-chevron",
    )
    Column {
        AuraSettingsIndexRow(
            icon = group.icon,
            title = group.title,
            description = if (single) group.entries.first().description else null,
            iconTint = AuraPalette.Teal,
            chevronRotation = rotation,
            onClick = { if (single) group.entries.first().onClick() else onToggle() },
        )
        AnimatedVisibility(visible = !single && expanded) {
            Column {
                group.entries.forEach { entry ->
                    AuraSettingsSubRow(
                        title = entry.title,
                        description = entry.description,
                        onClick = entry.onClick,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/**
 * The render's `.mrow`: a 17 px icon at full opacity in teal, an 11.5 px label, a ghost chevron on the
 * right, 8×15 px padding — scaled ×1.4, with the touch target lifted to the mandatory 48 dp.
 */
@Composable
private fun AuraSettingsIndexRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = AuraPalette.OnGround.copy(alpha = 0.75f),
    chevronRotation: Float = 0f,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .padding(horizontal = AuraSpacing.Gutter, vertical = 11.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AuraType.MenuLabel,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = AuraType.CalloutSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 2,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
        Icon(
            imageVector = AuraIcons.ChevronRight,
            contentDescription = null,
            tint = AuraPalette.OnGroundDisabled,
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation),
        )
    }
}

/** A destination inside an unfolded group: no icon, indented to the group label's text column. */
@Composable
private fun AuraSettingsSubRow(
    title: String,
    description: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .padding(
                start = AuraSpacing.Gutter + 24.dp + 15.dp,
                end = AuraSpacing.Gutter,
                top = 9.dp,
                bottom = 9.dp,
            ),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = AuraType.MenuLabel,
            color = AuraPalette.OnGround.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = AuraDefaultOverflow,
        )
        if (description != null) {
            Text(
                text = description,
                style = AuraType.CalloutSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
        }
    }
}

// ── Search field ──────────────────────────────────────────────────────────────────────────────────

/**
 * Render: `padding:8px 11px; border-radius:11px; background:rgba(255,255,255,.07);
 * border:1px solid rgba(255,255,255,.1)`, a 14 px search glyph at opacity .5 and the placeholder
 * "Buscar un ajuste" at opacity .42.
 */
@Composable
private fun AuraSettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AuraPalette.SurfaceFill, shape)
            .border(1.dp, AuraPalette.SurfaceLine, shape)
            .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
            .padding(start = 15.dp, end = 4.dp),
    ) {
        Icon(
            imageVector = AuraIcons.Search,
            contentDescription = null,
            tint = AuraPalette.OnGroundFaint,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = "Buscar un ajuste",
                    style = AuraType.MenuLabel,
                    color = AuraPalette.OnGroundGhost,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = AuraType.MenuLabel.copy(color = AuraPalette.OnGround),
                cursorBrush = SolidColor(AuraPalette.Teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            AuraIconButton(
                icon = AuraIcons.Plus,
                contentDescription = "Borrar búsqueda",
                onClick = { onQueryChange("") },
                size = 18.dp,
                tint = AuraPalette.OnGroundMuted,
                modifier = Modifier.rotate(45f), // the render has no × glyph; a rotated + is one
            )
        }
    }
}

// ── The escape hatch ──────────────────────────────────────────────────────────────────────────────

/**
 * The violet callout of the render: `background:rgba(122,92,255,.12); border:1px solid
 * rgba(122,92,255,.3); border-radius:12px`, title + subtitle on the left, the switch on the right.
 *
 * It writes ONE boolean and nothing else. Toggling it must not touch the database, playback, the queue
 * or any other preference — turning it off puts every screen straight back on the classic path.
 */
@Composable
private fun AuraBetaCallout(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(AuraShapes.Card)
            .background(AuraPalette.BetaFill, AuraShapes.Card)
            .border(1.dp, AuraPalette.BetaLine, AuraShapes.Card)
            .padding(start = 15.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AuraType.CalloutTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            Text(
                text = subtitle,
                style = AuraType.CalloutSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
        }
        AuraSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            contentDescription = title,
        )
    }
}

// ── Back ──────────────────────────────────────────────────────────────────────────────────────────

/** Back, with the long-press-to-home the inventory records on the classic top bar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuraBackButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(AuraSpacing.MinTouchTarget)
            .clip(AuraShapes.Pill)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
                onClickLabel = "Atrás",
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AuraIcons.ChevronRight,
            contentDescription = null,
            tint = AuraPalette.OnGroundMuted,
            modifier = Modifier
                .size(22.dp)
                .rotate(180f),
        )
    }
}
