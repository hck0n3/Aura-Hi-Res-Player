

package iad1tya.echo.music.ui.screens.recognition

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ThumbnailCornerRadius
import iad1tya.echo.music.db.entities.RecognitionHistory
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.NavigationTitle
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanel
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecognitionHistoryScreen(
    navController: NavController
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    val historyItems by database.recognitionHistory().collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<RecognitionHistory?>(null) }

    
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    
    val filteredItems = remember(historyItems, query) {
        if (query.text.isEmpty()) historyItems
        else historyItems.filter { item ->
            item.title.contains(query.text, ignoreCase = true) ||
                item.artist.contains(query.text, ignoreCase = true)
        }
    }

    
    val groupedItems = remember(filteredItems) {
        val today = LocalDate.now()
        filteredItems.groupBy { item ->
            val date = item.recognizedAt.toLocalDate()
            when {
                date == today                -> "Today"
                date == today.minusDays(1)  -> "Yesterday"
                date >= today.minusDays(7)  -> "This Week"
                else -> item.recognizedAt.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            }
        }
    }

    if (showClearDialog) {
        DefaultDialog(
            onDismiss = { showClearDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.clear_recognition_history)) },
            buttons = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                clearRecognitionHistory()
                            }
                        }
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.clear))
                }
            }
        ) {
            Text(
                text = stringResource(R.string.clear_recognition_history_confirm),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    itemToDelete?.let { item ->
        DefaultDialog(
            onDismiss = { itemToDelete = null },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.delete)) },
            buttons = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                deleteRecognitionHistoryById(item.id)
                            }
                        }
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        ) {
            Text(
                text = stringResource(R.string.delete_playlist_confirm, item.title),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // ONE flag read for the whole screen; the list rows take it as a parameter, so a 200-entry
    // history still holds exactly one DataStore subscription.
    val skin = rememberAuraPanelSkin()
    val auraDark = skin.enabled && skin.darkGround
    val ground = if (auraDark) AuraPalette.Ground else MaterialTheme.colorScheme.background

    Scaffold(
        // `MaterialTheme.colorScheme.background` IS the `Scaffold` default.
        containerColor = ground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognition_history)) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = null
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (historyItems.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.clear_all),
                                contentDescription = stringResource(R.string.clear_recognition_history)
                            )
                        }
                    }
                },
                colors = if (auraDark) {
                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = AuraPalette.Ground,
                        titleContentColor = skin.ink,
                        navigationIconContentColor = skin.ink,
                        actionIconContentColor = skin.ink,
                    )
                } else {
                    // The `TopAppBar` default, spelled out so the classic bar is unchanged.
                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors()
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.text.isNotEmpty()) {
                        IconButton(onClick = { query = TextFieldValue() }) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    // The render's search field is the same translucent wash as its cards.
                    focusedContainerColor   = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor  = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            
            when {
                historyItems.isEmpty() -> {
                    
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.history),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = if (skin.enabled) skin.inkFaint
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No recognition history",
                                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyLarge,
                                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                filteredItems.isEmpty() -> {
                    
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = if (skin.enabled) skin.inkFaint
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No results for \"${query.text}\"",
                                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyLarge,
                                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues()
                    ) {
                        if (query.text.isEmpty()) {
                            
                            groupedItems.forEach { (label, groupItems) ->
                                stickyHeader(key = "header_$label") {
                                    NavigationTitle(
                                        title = label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // The header is OPAQUE by construction — rows scroll under
                                            // it — so it has to match whatever ground the screen is
                                            // painting, or the new UI gets a light stripe on black.
                                            .background(if (auraDark) ground else MaterialTheme.colorScheme.surface)
                                    )
                                }
                                items(
                                    items = groupItems,
                                    key = { it.id }
                                ) { item ->
                                    RecognitionHistoryItem(
                                        item = item,
                                        skin = skin,
                                        onClick = {
                                            val searchQuery = "${item.title} ${item.artist}"
                                            navController.navigate("search/${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")
                                        },
                                        onDelete = {
                                            itemToDelete = item
                                        }
                                    )
                                }
                            }
                        } else {
                            
                            items(
                                items = filteredItems,
                                key = { it.id }
                            ) { item ->
                                RecognitionHistoryItem(
                                    item = item,
                                    skin = skin,
                                    onClick = {
                                        val searchQuery = "${item.title} ${item.artist}"
                                        navController.navigate("search/${java.net.URLEncoder.encode(searchQuery, "UTF-8")}")
                                    },
                                    onDelete = {
                                        itemToDelete = item
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionHistoryItem(
    item: RecognitionHistory,
    skin: AuraPanelSkin,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm") }

    AuraPanel(
        skin = skin,
        classicShape = RoundedCornerShape(ThumbnailCornerRadius),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            AsyncImage(
                model = item.coverArtUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    // The render's artwork radius, matching every restyled row elsewhere.
                    .clip(if (skin.enabled) AuraShapes.Artwork else RoundedCornerShape(ThumbnailCornerRadius)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))


            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (skin.enabled) skin.ink else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist,
                    style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                    color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.recognizedAt.format(dateFormatter),
                    // A timestamp is technical data — tracked monospace in the render.
                    style = if (skin.enabled) AuraType.Technical else MaterialTheme.typography.bodySmall,
                    color = if (skin.enabled) skin.inkFaint
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }


            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.delete_from_history),
                    tint = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
