# Echo Music — 7 funciones · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir 7 funciones a Echo Music (búsqueda por voz, álbumes favoritos, app por defecto para enlaces YT Music, widget de vinilo, migración de artistas seguidos de Spotify, canciones de playlist a biblioteca, lista de novedades) por fases independientes y compilables.

**Architecture:** Cada fase es un subsistema independiente. Se reutiliza infraestructura existente (paquete `spotifyimport/`, `EchoMusicWidgetManager`, DAO `albumsLikedBy*`, intent-filters del manifest, `SongEntity.inLibrary`). La lógica pura nueva (normalización de nombres, dedupe de lanzamientos, ventana de fechas) se aísla en objetos Kotlin con tests unitarios; el cableado UI/Android/WorkManager se verifica con build + prueba manual.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room, Hilt, WorkManager, Coroutines/Flow, Coil3, RemoteViews (App Widgets), Innertube (YouTube), Spotify GQL interno. Namespace `iad1tya.echo.music` (dirs fuente `com/music/echo`). Build: tasks `universalFoss`, JDK 21.

**Referencia de diseño:** `docs/superpowers/specs/2026-06-15-echo-7-features-design.md`

**Convenciones del repo:**
- Toda cadena visible va en `app/src/main/res/values/strings.xml` **y** `app/src/main/res/values-es/strings.xml`.
- Build local: `JAVA_HOME` a JDK 21 por comando. Compilar debug: `./gradlew :app:assembleUniversalFossDebug`.
- Tests unitarios (lógica pura): `app/src/test/kotlin/...`, correr con `./gradlew :app:testUniversalFossDebugUnitTest`. Antes de la primera fase con test, confirmar que `app/build.gradle.kts` tiene `testImplementation(libs.junit)` (o `junit:junit:4.13.2`); si falta, añadirlo.
- Verificación UI/Widget/Worker: no es unit-testeable aquí → cada tarea de ese tipo termina en build limpio + pasos manuales explícitos.

---

## Fase 1 · #7 Búsqueda por voz

**Objetivo:** botón de micrófono en la barra de búsqueda; dictado → texto → ejecuta búsqueda.

**Files:**
- Modify: `app/src/main/kotlin/com/music/echo/ui/screens/search/SearchScreen.kt` (bloque `trailingIcon`, ~L286-315; imports)
- Create: `app/src/main/res/drawable/ic_search_mic.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml`

- [ ] **Step 1: Añadir strings**

En `values/strings.xml`:
```xml
<string name="voice_search">Voice search</string>
<string name="voice_search_unavailable">Voice recognition not available on this device</string>
```
En `values-es/strings.xml`:
```xml
<string name="voice_search">Búsqueda por voz</string>
<string name="voice_search_unavailable">Reconocimiento de voz no disponible en este dispositivo</string>
```

- [ ] **Step 2: Crear drawable de micrófono**

`app/src/main/res/drawable/ic_search_mic.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92h-2z"/>
</vector>
```

- [ ] **Step 3: Añadir imports y launcher en SearchScreen**

En la zona de imports de `SearchScreen.kt` añadir:
```kotlin
import android.speech.RecognizerIntent
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
```
Dentro del composable de la pantalla (junto a las otras `remember`/estado, antes del `Scaffold`):
```kotlin
val context = LocalContext.current
val voiceLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    val spoken = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
        ?.trim()
    if (!spoken.isNullOrEmpty()) {
        query = TextFieldValue(spoken, TextRange(spoken.length))
        searchActive = true
        onSearch(spoken)
    }
}
```
(Si `LocalContext`/`TextRange` no están importados, añadir `androidx.compose.ui.platform.LocalContext` y `androidx.compose.ui.text.TextRange`.)

- [ ] **Step 4: Añadir el botón de mic en `trailingIcon`**

En el `Row` de `trailingIcon` (L287-314), antes del `IconButton` de cambio de fuente, insertar:
```kotlin
IconButton(
    onClick = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_search))
            }
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
) {
    Icon(
        painter = painterResource(R.drawable.ic_search_mic),
        contentDescription = stringResource(R.string.voice_search),
        tint = MaterialTheme.colorScheme.onSurface
    )
}
```
(`Intent` ya debería estar importado; si no, `android.content.Intent`.)

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleUniversalFossDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Verificación manual**

Abrir Búsqueda → tocar mic → dictar una frase → el texto aparece en el campo y se ejecuta la búsqueda. En emulador sin reconocedor → aparece toast "Reconocimiento de voz no disponible".

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/ui/screens/search/SearchScreen.kt app/src/main/res/drawable/ic_search_mic.xml app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat(search): voice search via RecognizerIntent in search bar"
```

---

## Fase 2 · #4 Álbumes favoritos (pantalla nueva)

**Objetivo:** pantalla dedicada de álbumes favoritos usando los queries `albumsLikedBy*` existentes.

**Files:**
- Create: `app/src/main/kotlin/com/music/echo/viewmodels/FavoriteAlbumsViewModel.kt`
- Create: `app/src/main/kotlin/com/music/echo/ui/screens/library/FavoriteAlbumsScreen.kt`
- Modify: `app/src/main/kotlin/com/music/echo/ui/screens/NavigationBuilder.kt` (registrar ruta)
- Modify: punto de entrada (ítem en `LibraryScreen.kt` o Home) + `strings.xml` (es + default)
- Reference: `LibraryAlbumsScreen.kt` y `LibraryAlbumsViewModel` como patrón; `LibraryAlbumGridItem`/`LibraryAlbumListItem` para los ítems.

- [ ] **Step 1: Strings**

`values/strings.xml`: `<string name="favorite_albums">Favorite albums</string>`
`values-es/strings.xml`: `<string name="favorite_albums">Álbumes favoritos</string>`

- [ ] **Step 2: ViewModel**

Crear `FavoriteAlbumsViewModel` siguiendo el patrón de `LibraryAlbumsViewModel` (revisar ese archivo para Hilt/inject del `DatabaseDao`). Exponer un `Flow<List<Album>>` desde `database.albumsLikedByCreateDateDesc()` (usar el orden por defecto que ya use Library; confirmar nombre exacto del query desc en `DatabaseDao.kt`, existen `albumsLikedBy*Asc` y sus `Desc`). Esqueleto:
```kotlin
@HiltViewModel
class FavoriteAlbumsViewModel @Inject constructor(
    database: MusicDatabase, // o DatabaseDao, según patrón del repo
) : ViewModel() {
    val albums = database.albumsLikedByCreateDateDesc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```
Ajustar tipos/inyección al patrón real de `LibraryAlbumsViewModel`.

- [ ] **Step 3: Pantalla**

Crear `FavoriteAlbumsScreen` replicando la estructura de grid/list de `LibraryAlbumsScreen` (Scaffold + TopAppBar con título `R.string.favorite_albums` + botón atrás; grid usando `LibraryAlbumGridItem`). Sin los `FilterChip` de LIKED/LIBRARY/UPLOADED (esta pantalla es solo favoritos). Reusar navegación al detalle de álbum igual que `LibraryAlbumsScreen`.

- [ ] **Step 4: Registrar ruta en navegación**

En `NavigationBuilder.kt`, añadir `composable("favorite_albums") { FavoriteAlbumsScreen(navController) }` siguiendo el patrón de rutas existente del archivo.

- [ ] **Step 5: Punto de entrada**

Añadir un ítem visible que navegue a `"favorite_albums"` (en el landing de Biblioteca o en Home), siguiendo el patrón de ítems existentes de esa pantalla.

- [ ] **Step 6: Build + verificación**

Run: `./gradlew :app:assembleUniversalFossDebug` → BUILD SUCCESSFUL.
Manual: marcar un álbum como favorito → abrir "Álbumes favoritos" → aparece; quitar favorito → desaparece.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/viewmodels/FavoriteAlbumsViewModel.kt app/src/main/kotlin/com/music/echo/ui/screens/library/FavoriteAlbumsScreen.kt app/src/main/kotlin/com/music/echo/ui/screens/NavigationBuilder.kt app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat(library): dedicated Favorite Albums screen"
```

---

## Fase 3 · #5 Predeterminar enlaces de YouTube Music

**Objetivo:** sección en Ajustes para abrir enlaces YT Music por defecto + manejar `ACTION_SEND`.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (añadir `intent-filter` SEND a la Activity de deep-link)
- Modify/Create: sección en Ajustes (`ui/screens/settings/SettingsScreen.kt` o `ContentSettings.kt`) con composable que abre la pantalla del sistema.
- Modify: handler de intent (la Activity/parse que procesa enlaces) para soportar texto compartido.
- Modify: `strings.xml` (es + default)

- [ ] **Step 1: Strings**

`values/strings.xml`:
```xml
<string name="open_links_title">Open links</string>
<string name="open_links_summary">Set Echo as default to open YouTube Music links</string>
<string name="open_links_button">Open system default-apps settings</string>
```
`values-es/strings.xml`:
```xml
<string name="open_links_title">Abrir enlaces</string>
<string name="open_links_summary">Predeterminar Echo para abrir enlaces de YouTube Music</string>
<string name="open_links_button">Abrir ajustes del sistema "Abrir por defecto"</string>
```

- [ ] **Step 2: Intent-filter SEND en manifest**

En la Activity que ya tiene los `intent-filter` VIEW de YouTube (`app/src/main/AndroidManifest.xml`, ~L109-151), añadir dentro de la misma `<activity>`:
```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

- [ ] **Step 3: Manejar texto compartido en el handler**

En la Activity/handler que procesa el intent entrante, además de `Intent.ACTION_VIEW` (`intent.data`), manejar `Intent.ACTION_SEND`: leer `intent.getStringExtra(Intent.EXTRA_TEXT)`, extraer la primera URL de YouTube/YT Music del texto y enrutarla al mismo parser de enlaces existente (`[LINK_PARSE_DEBUG]`). Seguir el patrón del parse actual.

- [ ] **Step 4: Sección de Ajustes "Abrir enlaces"**

Añadir entrada en Ajustes que muestre un botón "Abrir ajustes del sistema". Acción:
```kotlin
val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    Intent(
        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
} else {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
try { context.startActivity(intent) } catch (e: ActivityNotFoundException) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}
```
Insertar siguiendo el DSL de items que ya use `SettingsScreen.kt`/`ContentSettings.kt`.

- [ ] **Step 5: Build + verificación**

Run: `./gradlew :app:assembleUniversalFossDebug` → BUILD SUCCESSFUL.
Manual: Ajustes → Abrir enlaces → botón abre la pantalla del sistema. Compartir un enlace `music.youtube.com/...` desde el navegador/otra app → Echo aparece en el selector y reproduce. Abrir un enlace `music.youtube.com` directamente → Echo lo maneja (tras habilitarlo en "Abrir por defecto").

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/com/music/echo/ui/screens/settings app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat(settings): open-links section + handle shared YouTube Music links"
```

---

## Fase 4 · #1 Widget de disco de vinilo

**Objetivo:** estado en reposo del widget turntable = vinilo vacío; al reproducir = portada circular (ya existente).

**Files:**
- Create: `app/src/main/res/drawable/widget_vinyl_empty.xml`
- Modify: `app/src/main/res/layout/widget_turntable.xml:17` (`android:src`)
- Modify: `app/src/main/kotlin/com/music/echo/widget/EchoMusicWidgetManager.kt` (`createTurntableRemoteViews` else-branch + `getCircularDefaultIcon`)

**Contexto verificado:** `createTurntableRemoteViews` ya hace: si `circularAlbumArt != null` → portada; else → `getCircularDefaultIcon()`. El layout usa `@drawable/widget_turntable_default_art`. El "estado vacío" = rama else (sin pista/artwork). Basta sustituir el arte por defecto por un vinilo.

- [ ] **Step 1: Drawable de vinilo vacío**

`app/src/main/res/drawable/widget_vinyl_empty.xml` (disco negro con surcos y label central):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="300dp" android:height="300dp"
    android:viewportWidth="300" android:viewportHeight="300">
    <path android:fillColor="#1A1A1A" android:pathData="M150,10a140,140 0 1,0 0.1,0z"/>
    <path android:strokeColor="#333333" android:strokeWidth="1" android:fillColor="#00000000"
        android:pathData="M150,40a110,110 0 1,0 0.1,0z"/>
    <path android:strokeColor="#333333" android:strokeWidth="1" android:fillColor="#00000000"
        android:pathData="M150,70a80,80 0 1,0 0.1,0z"/>
    <path android:strokeColor="#333333" android:strokeWidth="1" android:fillColor="#00000000"
        android:pathData="M150,100a50,50 0 1,0 0.1,0z"/>
    <path android:fillColor="#C0392B" android:pathData="M150,118a32,32 0 1,0 0.1,0z"/>
    <path android:fillColor="#1A1A1A" android:pathData="M150,146a4,4 0 1,0 0.1,0z"/>
</vector>
```

- [ ] **Step 2: Layout default art → vinilo**

En `widget_turntable.xml:17` cambiar:
```xml
android:src="@drawable/widget_turntable_default_art"
```
por:
```xml
android:src="@drawable/widget_vinyl_empty"
```

- [ ] **Step 3: Rama else del RemoteViews**

En `EchoMusicWidgetManager.createTurntableRemoteViews`, cambiar la rama else para mostrar el vinilo en vez del icono por defecto circular:
```kotlin
if (circularAlbumArt != null) {
    views.setImageViewBitmap(R.id.widget_turntable_album_art, circularAlbumArt)
} else {
    views.setImageViewResource(R.id.widget_turntable_album_art, R.drawable.widget_vinyl_empty)
}
```
(Esto evita depender de `getCircularDefaultIcon()` para el estado vacío; si `getCircularDefaultIcon()` queda sin usos, eliminarlo.)

- [ ] **Step 4: Build + verificación**

Run: `./gradlew :app:assembleUniversalFossDebug` → BUILD SUCCESSFUL.
Manual: añadir el widget turntable a la pantalla de inicio sin reproducir nada → muestra vinilo vacío. Reproducir una canción → muestra portada circular. Detener/cerrar reproducción → vuelve a vinilo vacío.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/widget_vinyl_empty.xml app/src/main/res/layout/widget_turntable.xml app/src/main/kotlin/com/music/echo/widget/EchoMusicWidgetManager.kt
git commit -m "feat(widget): empty vinyl disc placeholder until playback starts"
```

---

## Fase 5 · #2 Migrar artistas seguidos de Spotify

**Objetivo:** importar artistas seguidos en Spotify → seguir local (`ArtistEntity.bookmarkedAt`) + suscribir canal en YouTube Music.

**Files:**
- Create: `app/src/main/kotlin/com/music/echo/spotifyimport/ArtistNameMatching.kt` (lógica pura + test)
- Create: `app/src/test/kotlin/com/music/echo/spotifyimport/ArtistNameMatchingTest.kt`
- Modify: `app/src/main/kotlin/com/music/echo/spotifyimport/SpotifyImportModels.kt` (nuevo source type)
- Modify: `app/src/main/kotlin/com/music/echo/spotifyimport/SpotifyImportRepository.kt` (`loadSources`, `importSources`)
- Modify: `app/src/main/kotlin/com/music/echo/ui/screens/SpotifyImportScreen.kt` (mostrar la fuente Artistas)
- Modify: `strings.xml` (es + default)
- Reference: `Spotify.myArtists()`, `YouTube.search(query, FilterType.ARTIST)`, `ArtistEntity`, `YouTube.subscribeChannel`, `ArtistMenu.kt` (patrón de follow).

- [ ] **Step 1: Test de normalización de nombre (falla primero)**

`app/src/test/kotlin/com/music/echo/spotifyimport/ArtistNameMatchingTest.kt`:
```kotlin
package iad1tya.echo.music.spotifyimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ArtistNameMatchingTest {
    @Test fun normalizesCaseAndAccents() {
        assertEquals("beyonce", ArtistNameMatching.normalize("Beyoncé"))
        assertEquals("acdc", ArtistNameMatching.normalize("AC/DC"))
        assertEquals("the weeknd", ArtistNameMatching.normalize("  The   Weeknd "))
    }
    @Test fun matchesWhenNormalizedEqual() {
        assertTrue(ArtistNameMatching.isLikelyMatch("Beyoncé", "BEYONCE"))
    }
    @Test fun rejectsDifferentArtists() {
        assertFalse(ArtistNameMatching.isLikelyMatch("Drake", "Drizzy XYZ"))
    }
}
```

- [ ] **Step 2: Correr test → falla**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "*ArtistNameMatchingTest"`
Expected: FAIL (clase `ArtistNameMatching` no existe). Si la tarea de test no existe, primero confirmar `testImplementation(libs.junit)` en `app/build.gradle.kts`.

- [ ] **Step 3: Implementar `ArtistNameMatching`**

`app/src/main/kotlin/com/music/echo/spotifyimport/ArtistNameMatching.kt`:
```kotlin
package iad1tya.echo.music.spotifyimport

import java.text.Normalizer

object ArtistNameMatching {
    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")        // quitar diacríticos
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")       // quitar símbolos (AC/DC -> acdc)
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isLikelyMatch(a: String, b: String): Boolean {
        val na = normalize(a); val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na == nb || na.contains(nb) || nb.contains(na)
    }
}
```

- [ ] **Step 4: Correr test → pasa**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "*ArtistNameMatchingTest"`
Expected: PASS.

- [ ] **Step 5: Commit lógica pura**

```bash
git add app/src/main/kotlin/com/music/echo/spotifyimport/ArtistNameMatching.kt app/src/test/kotlin/com/music/echo/spotifyimport/ArtistNameMatchingTest.kt
git commit -m "feat(spotifyimport): pure artist-name matching helper with tests"
```

- [ ] **Step 6: Nuevo source type en modelos**

En `SpotifyImportModels.kt`, añadir a `SpotifyImportSourceType` el valor `ARTISTS`. En `SpotifyImportRepository` (donde está la `sealed class SpotifyImportSource` — confirmar ubicación; los tipos `LikedSongs`/`Playlist` ya existen) añadir:
```kotlin
object FollowedArtists : SpotifyImportSource() {
    override val title get() = "Followed artists"
    override val trackCount: Int? get() = null
}
```
Ajustar a la forma real de la `sealed class` (campos `title`/`trackCount`).

- [ ] **Step 7: `loadSources` incluye artistas**

En `SpotifyImportRepository.loadSources()` (~L138), tras agregar liked songs y playlists, añadir la fuente de artistas con su conteo:
```kotlin
val artistsTotal = spotifyCallWithTokenRetry {
    Spotify.myArtists(limit = 1).getOrThrow()
}.total // confirmar nombre del campo total en el modelo de myArtists
if (artistsTotal > 0) add(SpotifyImportSource.FollowedArtists)
```

- [ ] **Step 8: `importSources` rama artistas**

En `importSources(...)`, al inicio del `forEachIndexed`, derivar la rama de artistas antes de `fetchAllTracks`:
```kotlin
if (source is SpotifyImportSource.FollowedArtists) {
    val artists = fetchAllFollowedArtists() // paginar Spotify.myArtists
    var imported = 0
    artists.forEachIndexed { i, sp ->
        val match = YouTube.search(sp.name, YouTube.SearchFilter.FILTER_ARTIST)
            .getOrNull()?.items
            ?.filterIsInstance<ArtistItem>()
            ?.firstOrNull { ArtistNameMatching.isLikelyMatch(sp.name, it.title) }
        if (match != null) {
            database.upsertArtist(/* ArtistEntity(id=match.id, name=match.title, bookmarkedAt=now, channelId=...) */)
            runCatching { YouTube.subscribeChannel(match.channelId ?: match.id, true) }
            imported++
        }
        onProgress(/* progreso por artista */)
    }
    summaries += SpotifyImportSourceSummaryUi(source.title, artists.size, imported, artists.size - imported)
    return@forEachIndexed
}
```
Confirmar firmas reales: tipo de retorno de `YouTube.search`, `ArtistItem` (paquete innertube), API exacta de `subscribeChannel`, y el upsert de artista en el DAO (ver `ArtistMenu.kt`/`SyncUtils.kt` para el patrón de bookmark + subscribe). Implementar `fetchAllFollowedArtists()` paginando `Spotify.myArtists(limit, offset)` igual que `fetchAllPlaylists()`.

- [ ] **Step 9: UI**

`SpotifyImportScreen.kt`: la lista de fuentes ya itera sources; verificar que "Followed artists" se muestra con su título y conteo. Añadir string `spotify_followed_artists` (default: "Followed artists", es: "Artistas seguidos") y usarlo si el título se localiza en UI.

- [ ] **Step 10: Build + verificación**

Run: `./gradlew :app:assembleUniversalFossDebug` → BUILD SUCCESSFUL.
Manual: conectar Spotify → seleccionar "Artistas seguidos" → importar → en Biblioteca→Artistas aparecen como seguidos; con login YT, verificar suscripción. Artista sin match → contado como fallido en el resumen, sin crash.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/spotifyimport app/src/main/kotlin/com/music/echo/ui/screens/SpotifyImportScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat(spotifyimport): migrate followed Spotify artists (local follow + YT subscribe)"
```

---

## Fase 6 · #3 Canciones de playlist → Biblioteca

**Objetivo:** al importar/agregar una playlist, marcar sus canciones con `inLibrary` para que aparezcan en Biblioteca→Canciones.

**Files:**
- Modify: `app/src/main/kotlin/com/music/echo/spotifyimport/SpotifyImportRepository.kt` (`mirrorPlaylist`)
- Modify: camino de "guardar playlist" de YouTube (localizar: `grep -rn "savePlaylist\|PlaylistEntity(" app/src/main`), aplicar el mismo `inLibrary`.
- Modify (opcional): `constants/PreferenceKeys.kt` + Ajustes (toggle "Agregar canciones de playlists a la biblioteca", default ON)
- Reference: `SongEntity.inLibrary` (flag), Library Songs = `WHERE inLibrary IS NOT NULL`.

- [ ] **Step 1: Marcar inLibrary en mirrorPlaylist**

Leer `mirrorPlaylist(...)` completo en `SpotifyImportRepository.kt`. Donde inserta/upserta cada `SongEntity` (a partir de `matched.map { it.metadata }`), poblar `inLibrary = LocalDateTime.now()` en la entidad insertada (o llamar al update de librería existente). Para canciones ya presentes en DB, hacer update que set `inLibrary` si es null (no sobrescribir si ya estaba). Seguir el patrón de inserción de canción del propio archivo.

- [ ] **Step 2: Toggle en Ajustes (opcional, default ON)**

Si se añade el toggle: nueva `booleanPreferencesKey("add_playlist_songs_to_library")` en `PreferenceKeys.kt` (default true); en `mirrorPlaylist` respetar el valor. Strings `add_playlist_songs_to_library` (default: "Add imported playlist songs to library", es: "Agregar canciones de playlists importadas a la biblioteca").

- [ ] **Step 3: Mismo comportamiento al guardar playlist YT**

En el camino de guardar/agregar playlist de YouTube, aplicar el mismo set de `inLibrary` a las canciones de la playlist guardada.

- [ ] **Step 4: Build + verificación**

Run: `./gradlew :app:assembleUniversalFossDebug` → BUILD SUCCESSFUL.
Manual: importar una playlist de Spotify (o guardar una de YT) → ir a Biblioteca→Canciones → las canciones de esa playlist aparecen. Verificar que no se duplican canciones ya existentes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/spotifyimport/SpotifyImportRepository.kt app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat(library): added/imported playlist songs appear in Library Songs (inLibrary)"
```

---

## Fase 7 · #6 Lista de novedades (Release Radar)

**Objetivo:** lanzamientos recientes de artistas seguidos (YT + Spotify, dedupe), cacheados en DB, refrescados por WorkManager semanal (viernes) con notificación, y una pantalla que lee la cache.

**Files:**
- Create: `app/src/main/kotlin/com/music/echo/releaseradar/ReleaseRadarMatching.kt` (lógica pura) + test
- Create: `app/src/test/kotlin/com/music/echo/releaseradar/ReleaseRadarMatchingTest.kt`
- Create: `app/src/main/kotlin/com/music/echo/db/entities/ReleaseRadarItem.kt` (entidad Room)
- Modify: `app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt` (+queries) y `db/MusicDatabase.kt` (entidad + bump versión + migración)
- Create: `app/src/main/kotlin/com/music/echo/worker/ReleaseRadarWorker.kt`
- Create: `app/src/main/kotlin/com/music/echo/viewmodels/ReleaseRadarViewModel.kt`
- Create: `app/src/main/kotlin/com/music/echo/ui/screens/ReleaseRadarScreen.kt`
- Modify: `NavigationBuilder.kt` (ruta + entrada), notificación (canal), `strings.xml` (es + default)
- Reference: `YouTube.artist(browseId)` / `YouTube.artistItems(endpoint)` (singles/álbumes con año), `Spotify` discografía (~L1568), `ArtistEntity` seguidos.

- [ ] **Step 1: Test de dedupe + ventana de fechas (falla primero)**

`app/src/test/kotlin/com/music/echo/releaseradar/ReleaseRadarMatchingTest.kt`:
```kotlin
package iad1tya.echo.music.releaseradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class ReleaseRadarMatchingTest {
    private fun item(title: String, artist: String, date: LocalDate) =
        ReleaseCandidate(title = title, artist = artist, date = date, source = "yt", artworkUri = null, playId = title)

    @Test fun dedupeKeyIgnoresCaseAndPunctuation() {
        assertEquals(
            ReleaseRadarMatching.dedupeKey(item("Hello!", "Adele", LocalDate.of(2026, 6, 12))),
            ReleaseRadarMatching.dedupeKey(item("hello", "ADELE", LocalDate.of(2026, 6, 12)))
        )
    }
    @Test fun dedupeKeepsOnePerKey() {
        val a = item("Song", "X", LocalDate.of(2026, 6, 12))
        val b = item("song", "x", LocalDate.of(2026, 6, 12))
        assertEquals(1, ReleaseRadarMatching.dedupe(listOf(a, b)).size)
    }
    @Test fun windowFiltersOld() {
        val ref = LocalDate.of(2026, 6, 12)
        assertTrue(ReleaseRadarMatching.isWithinWindow(LocalDate.of(2026, 6, 10), ref, days = 7))
        assertFalse(ReleaseRadarMatching.isWithinWindow(LocalDate.of(2026, 1, 1), ref, days = 7))
    }
}
```

- [ ] **Step 2: Correr test → falla**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "*ReleaseRadarMatchingTest"`
Expected: FAIL (clases no existen).

- [ ] **Step 3: Implementar lógica pura**

`app/src/main/kotlin/com/music/echo/releaseradar/ReleaseRadarMatching.kt`:
```kotlin
package iad1tya.echo.music.releaseradar

import java.time.LocalDate
import kotlin.math.abs

data class ReleaseCandidate(
    val title: String,
    val artist: String,
    val date: LocalDate,
    val source: String,      // "yt" | "spotify"
    val artworkUri: String?,
    val playId: String,      // videoId/browseId YT para reproducir
)

object ReleaseRadarMatching {
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun dedupeKey(c: ReleaseCandidate): String = norm(c.artist) + "|" + norm(c.title)

    /** Conserva uno por clave; prefiere fuente "yt" (reproducible directo). */
    fun dedupe(items: List<ReleaseCandidate>): List<ReleaseCandidate> =
        items.groupBy { dedupeKey(it) }
            .values
            .map { group -> group.firstOrNull { it.source == "yt" } ?: group.first() }

    fun isWithinWindow(date: LocalDate, ref: LocalDate, days: Long): Boolean {
        val diff = abs(date.toEpochDay() - ref.toEpochDay())
        return diff <= days
    }
}
```

- [ ] **Step 4: Correr test → pasa; commit lógica pura**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "*ReleaseRadarMatchingTest"` → PASS.
```bash
git add app/src/main/kotlin/com/music/echo/releaseradar/ReleaseRadarMatching.kt app/src/test/kotlin/com/music/echo/releaseradar/ReleaseRadarMatchingTest.kt
git commit -m "feat(releaseradar): pure dedupe + date-window logic with tests"
```

- [ ] **Step 5: Entidad Room + DAO + migración**

Crear `ReleaseRadarItem` (`@Entity`): `id: String` (PK = dedupeKey), `artistId: String`, `title`, `artist`, `type` ("single"/"album"), `releaseDate: LocalDate`, `artworkUri: String?`, `source: String`, `playId: String`, `fetchedAt: LocalDateTime`, `seen: Boolean = false`. Añadir a `MusicDatabase` entities, **bump de versión** (la siguiente libre; confirmar versión actual en `MusicDatabase.kt`) y migración Room (manual o auto). DAO: `upsertReleases(items)`, `releasesByDateDesc(): Flow<List<ReleaseRadarItem>>`, `markAllSeen()`, `clearReleases()`, `unseenCount(): Flow<Int>`.

- [ ] **Step 6: Worker semanal**

`ReleaseRadarWorker` (`CoroutineWorker` + Hilt `@HiltWorker`). `doWork()`: leer artistas seguidos (`ArtistEntity` bookmarked) → por artista: YT `artist()`/`artistItems()` (singles+álbumes con fecha) y Spotify discografía → mapear a `ReleaseCandidate` → `ReleaseRadarMatching.dedupe` → filtrar `isWithinWindow(date, today, 7)` (primera corrida: 56 días) → `upsertReleases` → si hay nuevos, notificación (canal `release_radar`) "Novedades de tus artistas". Programar `PeriodicWorkRequestBuilder<ReleaseRadarWorker>(7, TimeUnit.DAYS)` con `initialDelay` calculado al próximo viernes ~08:00, `ExistingPeriodicWorkPolicy.UPDATE`, registrado en `App.kt`/inicialización. Acotar concurrencia (p.ej. `chunked`/semaphore) y `runCatching` por artista.

- [ ] **Step 7: ViewModel + Pantalla**

`ReleaseRadarViewModel`: expone `database.releasesByDateDesc()` como state. Al abrir, `markAllSeen()`. `ReleaseRadarScreen`: lista por fecha desc (sección por día/semana opcional), tap reproduce vía `playId` (YT), pull-to-refresh / botón "Actualizar" que encola `OneTimeWorkRequest<ReleaseRadarWorker>(EXPEDITED)`. Strings `release_radar_title` (default "New releases", es "Novedades"), `release_radar_notification` (es "Novedades de tus artistas seguidos").

- [ ] **Step 8: Ruta + entrada + canal de notificación**

Registrar ruta `"release_radar"` en `NavigationBuilder.kt`; añadir entrada visible (Home/Biblioteca) con badge de `unseenCount`. Crear canal de notificación `release_radar` donde se crean los demás canales (`App.kt`).

- [ ] **Step 9: Build + verificación**

Run: `./gradlew :app:assembleUniversalFossDebug` → BUILD SUCCESSFUL.
Manual: seguir artistas con lanzamientos recientes → botón "Actualizar" → aparecen ordenados por fecha y llega la notificación; tap reproduce. Sin red → muestra cache. Verificar migración Room (la app abre sin crash tras actualizar esquema).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/releaseradar app/src/main/kotlin/com/music/echo/db app/src/main/kotlin/com/music/echo/worker app/src/main/kotlin/com/music/echo/viewmodels/ReleaseRadarViewModel.kt app/src/main/kotlin/com/music/echo/ui/screens/ReleaseRadarScreen.kt app/src/main/kotlin/com/music/echo/ui/screens/NavigationBuilder.kt app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml
git commit -m "feat(releaseradar): weekly new-releases radar (YT+Spotify) with worker, cache and notification"
```

---

## Self-review (cobertura del spec)

- #7 Voz → Fase 1 ✓ · #4 Álbumes favoritos (pantalla nueva) → Fase 2 ✓ · #5 Links YT por defecto + SEND → Fase 3 ✓ · #1 Widget vinilo → Fase 4 ✓ · #2 Migrar artistas Spotify (local + YT subscribe) → Fase 5 ✓ · #3 Playlist→biblioteca (`inLibrary`) → Fase 6 ✓ · #6 Release Radar (YT+Spotify, semanal+notif) → Fase 7 ✓.
- Lógica pura con tests reales: normalización de artista (F5), dedupe/ventana (F7). UI/widget/worker → build + verificación manual (no unit-testeable en este repo).
- Consistencia de tipos: `ArtistNameMatching.normalize/isLikelyMatch`, `ReleaseCandidate`/`ReleaseRadarMatching.dedupeKey/dedupe/isWithinWindow` usados igual en sus fases.

## Puntos a confirmar en ejecución (firmas exactas, no placeholders de diseño)

- F2: nombre exacto del query `albumsLikedByCreateDateDesc` y patrón de `LibraryAlbumsViewModel`.
- F5: nombre real de la Activity/handler de deep-link y su parser.
- F5/F6: forma real de la `sealed class SpotifyImportSource` y de `Spotify.myArtists`/discografía.
- F5: firmas innertube `YouTube.search`/`SearchFilter`/`ArtistItem`/`subscribeChannel`.
- F7: versión actual de Room en `MusicDatabase.kt` para el bump.
