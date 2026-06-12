# Axion-only EQ + YouTube-only Login + Direct Share Links — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar Axion EQ como único ecualizador, login únicamente vía cuenta YouTube/YT Music, y share links directos `https://music.youtube.com/...`, cada feature gated por tests.

**Architecture:** El motor DSP (`eq/`) se conserva (Axion lo usa); solo muere la UI vieja (`EqScreen`+VM+State). El login WebView existente queda como único camino (se elimina el editor de token). Los share links se centralizan en `ShareLinks` (app) y se corrigen los getters de `YTItem` (innertube). Tests JVM puros con JUnit 4.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Media3/ExoPlayer, JUnit 4 (4.13.2 vía `libs.junit`), Gradle (variantes `abi`×`variant`; usar `universalFoss` para unit tests).

**Spec:** `docs/superpowers/specs/2026-06-12-eq-login-share-design.md`

**Comandos de verificación (Git Bash, raíz del repo):**
- App unit tests: `./gradlew :app:testUniversalFossDebugUnitTest --tests "<filtro>"`
- Innertube unit tests: `./gradlew :innertube:testDebugUnitTest --tests "<filtro>"`
- Los tests existentes de innertube (`MainTest`, `SearchVideoTest`) pegan a red — usar SIEMPRE `--tests` con filtro.

---

### Task 0: Infra de tests del módulo app + sanidad del entorno

**Files:**
- Modify: `app/build.gradle.kts` (bloque `dependencies {` en línea 224)

- [ ] **Step 0.1: Verificar entorno de build**

Run: `java -version && ./gradlew --version`
Expected: JDK presente y Gradle responde. Si falla, diagnosticar antes de seguir (local.properties / JAVA_HOME).

- [ ] **Step 0.2: Añadir JUnit al módulo app**

En `app/build.gradle.kts`, dentro de `dependencies {`, justo tras la línea `dependencies {`:

```kotlin
dependencies {
    testImplementation(libs.junit)

```

- [ ] **Step 0.3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "test: add JUnit to app module for unit tests"
```

---

### Task 1 (F1): Tests del DSP del ecualizador (BiquadFilter)

El filtro biquad es el corazón del Axion EQ (vía `CustomEqualizerAudioProcessor`). Es Kotlin puro → test JVM directo.

**Files:**
- Test (create): `app/src/test/kotlin/iad1tya/echo/music/eq/audio/BiquadFilterTest.kt`

- [ ] **Step 1.1: Escribir los tests**

```kotlin
package iad1tya.echo.music.eq.audio

import iad1tya.echo.music.eq.data.FilterType
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class BiquadFilterTest {

    private val sampleRate = 48000

    /** Alimenta un seno de [frequency] y devuelve la amplitud pico en régimen estacionario. */
    private fun measureGain(filter: BiquadFilter, frequency: Double): Double {
        val warmup = 16384
        val measure = 8192
        var peak = 0.0
        for (i in 0 until warmup + measure) {
            val x = sin(2.0 * PI * frequency * i / sampleRate)
            val y = filter.processSample(x)
            if (i >= warmup) peak = maxOf(peak, abs(y))
        }
        return peak
    }

    @Test
    fun peakingWithZeroGainIsPassthrough() {
        val filter = BiquadFilter(sampleRate, 1000.0, 0.0, 1.41, FilterType.PK)
        assertEquals(1.0, measureGain(filter, 1000.0), 0.01)
    }

    @Test
    fun peakingBoostsCenterFrequencyByConfiguredGain() {
        val filter = BiquadFilter(sampleRate, 1000.0, 12.0, 1.41, FilterType.PK)
        // +12 dB == x3.98 lineal en la frecuencia central
        assertEquals(3.98, measureGain(filter, 1000.0), 0.15)
    }

    @Test
    fun peakingLeavesFarFrequenciesUntouched() {
        val filter = BiquadFilter(sampleRate, 1000.0, 12.0, 1.41, FilterType.PK)
        assertEquals(1.0, measureGain(filter, 60.0), 0.05)
    }

    @Test
    fun lowShelfBoostsLowFrequencies() {
        val filter = BiquadFilter(sampleRate, 1000.0, 6.0, 1.41, FilterType.LSC)
        // +6 dB == x2 lineal muy por debajo de la frecuencia de corte
        assertEquals(2.0, measureGain(filter, 40.0), 0.1)
    }

    @Test
    fun highShelfBoostsHighFrequencies() {
        val filter = BiquadFilter(sampleRate, 1000.0, 6.0, 1.41, FilterType.HSC)
        assertEquals(2.0, measureGain(filter, 12000.0), 0.15)
    }

    @Test
    fun resetClearsFilterState() {
        val filter = BiquadFilter(sampleRate, 1000.0, 12.0, 1.41, FilterType.PK)
        filter.processSample(1.0)
        filter.processSample(0.5)
        filter.reset()
        assertEquals(0.0, filter.processSample(0.0), 0.0)
    }

    @Test
    fun stereoChannelsAreProcessedIndependently() {
        val filter = BiquadFilter(sampleRate, 1000.0, 12.0, 1.41, FilterType.PK)
        for (i in 0 until 256) {
            val x = sin(2.0 * PI * 1000.0 * i / sampleRate)
            val (_, right) = filter.processStereo(x, 0.0)
            assertEquals(0.0, right, 0.0)
        }
    }
}
```

- [ ] **Step 1.2: Ejecutar y verificar que pasan**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.eq.audio.BiquadFilterTest"`
Expected: BUILD SUCCESSFUL, 7 tests passed. (El código bajo test ya existe — estos tests fijan el comportamiento del motor que Axion conserva. Si alguno falla, investigar el DSP antes de seguir.)

- [ ] **Step 1.3: Commit**

```bash
git add app/src/test
git commit -m "test: cover BiquadFilter DSP (peaking/shelf gain, reset, stereo isolation)"
```

---

### Task 2 (F1): Eliminar la UI del EQ viejo; Axion como único ecualizador

**Files:**
- Delete: `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/EqScreen.kt`
- Delete: `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/EQViewModel.kt`
- Delete: `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/EQState.kt`
- Modify: `app/src/main/kotlin/com/music/echo/ui/screens/NavigationBuilder.kt:29` (import) y `:407-409` (dialog)
- Modify: `app/src/main/kotlin/com/music/echo/ui/menu/PlayerMenu.kt:720`
- Modify: `app/src/main/kotlin/com/music/echo/ui/menu/OldPlayerMenu.kt:645`

- [ ] **Step 2.1: Borrar los 3 archivos de la UI vieja**

```bash
git rm app/src/main/kotlin/com/music/echo/ui/screens/equalizer/EqScreen.kt \
       app/src/main/kotlin/com/music/echo/ui/screens/equalizer/EQViewModel.kt \
       app/src/main/kotlin/com/music/echo/ui/screens/equalizer/EQState.kt
```

- [ ] **Step 2.2: NavigationBuilder — quitar import y dialog**

Eliminar la línea:
```kotlin
import iad1tya.echo.music.ui.screens.equalizer.EqScreen
```
Eliminar el bloque:
```kotlin
    dialog("equalizer") {
        EqScreen(navController = navController)
    }
```

- [ ] **Step 2.3: PlayerMenu y OldPlayerMenu — redirigir al Axion EQ**

En ambos archivos, en el item de menú "equalizer", cambiar:
```kotlin
navController.navigate("equalizer")
```
por:
```kotlin
navController.navigate("settings/equalizer")
```

- [ ] **Step 2.4: Verificar cero referencias residuales**

Run: `grep -rn "EqScreen\|EQViewModel\|EQState\|navigate(\"equalizer\")" app/src/main/kotlin/`
Expected: solo hits de `AxionEqScreen`/`AxionEqViewModel` (nada del EQ viejo, ningún `navigate("equalizer")`).

- [ ] **Step 2.5: Compilar + tests (gate de F1)**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.eq.audio.BiquadFilterTest"`
Expected: BUILD SUCCESSFUL (la task compila todo el módulo app → valida los borrados) y 7 tests passed.

- [ ] **Step 2.6: Commit**

```bash
git add -A
git commit -m "feat: remove legacy equalizer UI, Axion EQ is the only equalizer"
```

---

### Task 3 (F2): Login YouTube — tests del criterio de sesión (parseCookieString)

El login WebView (`LoginScreen.kt`) ya es el mecanismo correcto y queda intacto. Lo testeable en JVM es `parseCookieString` (innertube), que es exactamente el criterio `isLoggedIn` de la app (`"SAPISID" in parseCookieString(cookie)`).

**Files:**
- Test (create): `innertube/src/test/kotlin/com/music/innertube/utils/ParseCookieStringTest.kt`

- [ ] **Step 3.1: Escribir los tests**

```kotlin
package com.music.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseCookieStringTest {

    @Test
    fun parsesYouTubeMusicCookieAndDetectsLogin() {
        val cookie = "VISITOR_INFO1_LIVE=abc123; SAPISID=xyz789/AbCdEf; HSID=A1B2C3; SSID=secret"
        val parsed = parseCookieString(cookie)
        assertEquals("xyz789/AbCdEf", parsed["SAPISID"])
        assertEquals("abc123", parsed["VISITOR_INFO1_LIVE"])
        // Mismo check que usa la app para considerar al usuario logueado
        assertTrue("SAPISID" in parsed)
    }

    @Test
    fun missingSapisidMeansLoggedOut() {
        val parsed = parseCookieString("VISITOR_INFO1_LIVE=abc123; PREF=f1=50000000")
        assertFalse("SAPISID" in parsed)
    }

    @Test
    fun keepsEqualsSignsInsideValues() {
        val parsed = parseCookieString("PREF=f1=50000000&f6=8; SAPISID=a=b=c")
        assertEquals("f1=50000000&f6=8", parsed["PREF"])
        assertEquals("a=b=c", parsed["SAPISID"])
    }

    @Test
    fun emptyCookieGivesEmptyMap() {
        assertTrue(parseCookieString("").isEmpty())
    }

    @Test
    fun ignoresPartsWithoutEquals() {
        val parsed = parseCookieString("garbage; SAPISID=ok")
        assertEquals(mapOf("SAPISID" to "ok"), parsed)
    }
}
```

- [ ] **Step 3.2: Ejecutar y verificar que pasan**

Run: `./gradlew :innertube:testDebugUnitTest --tests "com.music.innertube.utils.ParseCookieStringTest"`
Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 3.3: Commit**

```bash
git add innertube/src/test
git commit -m "test: cover parseCookieString login-session detection"
```

---

### Task 4 (F3): Eliminar el login por token (Advanced login)

**Files:**
- Modify: `app/src/main/kotlin/com/music/echo/ui/screens/settings/AccountSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/music/echo/viewmodels/AccountSettingsViewModel.kt`

- [ ] **Step 4.1: AccountSettingsScreen — quitar prefs que solo alimentaban el editor de token**

Cambiar:
```kotlin
    val (accountNamePref, _) = rememberPreference(AccountNameKey, "")
    val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, _) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, _) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, _) = rememberPreference(DataSyncIdKey, "")
```
por:
```kotlin
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
```

- [ ] **Step 4.2: AccountSettingsScreen — quitar estados del token**

Cambiar:
```kotlin
    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
```
por:
```kotlin
    var showLogoutDialog by remember { mutableStateOf(false) }
```

- [ ] **Step 4.3: AccountSettingsScreen — quitar el grupo "Advanced login" completo**

Eliminar este bloque (incluido su `Spacer` posterior):
```kotlin
            Material3SettingsGroup(
                title = stringResource(R.string.advanced_login),
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.token),
                        title = {
                            Text(
                                when {
                                    !isLoggedIn -> stringResource(R.string.advanced_login)
                                    showToken -> stringResource(R.string.token_shown)
                                    else -> stringResource(R.string.token_hidden)
                                }
                            )
                        },
                        onClick = {
                            if (!isLoggedIn) showTokenEditor = true
                            else if (!showToken) showToken = true
                            else showTokenEditor = true
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
```

- [ ] **Step 4.4: AccountSettingsScreen — quitar el dialog del editor de token**

Eliminar el bloque entero `if (showTokenEditor) { ... }` (desde `if (showTokenEditor) {` con su `val text = """...` y el `TextFieldDialog(...)` hasta el cierre antes de `if (showLogoutDialog)`).

- [ ] **Step 4.5: AccountSettingsScreen — limpiar import huérfano**

Eliminar:
```kotlin
import androidx.compose.ui.text.input.TextFieldValue
```
(`parseCookieString` se queda — lo usa `isLoggedIn`.)

- [ ] **Step 4.6: AccountSettingsViewModel — eliminar saveTokenAndRestart e imports huérfanos**

Eliminar el método `saveTokenAndRestart(...)` completo y estos imports que quedan sin uso:
```kotlin
import android.content.Intent
import iad1tya.echo.music.constants.AccountChannelHandleKey
import iad1tya.echo.music.constants.AccountEmailKey
import iad1tya.echo.music.constants.AccountNameKey
import iad1tya.echo.music.constants.DataSyncIdKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.VisitorDataKey
import iad1tya.echo.music.utils.dataStore
import androidx.datastore.preferences.core.edit
```
(Quedan los dos métodos de logout intactos.)

- [ ] **Step 4.7: Verificar que no queda nada del token login**

Run: `grep -rn "saveTokenAndRestart\|showTokenEditor\|advanced_login" app/src/main/kotlin/`
Expected: 0 resultados.

- [ ] **Step 4.8: Compilar + tests (gate de F3)**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.eq.audio.BiquadFilterTest"`
Expected: BUILD SUCCESSFUL, 7 tests passed.

- [ ] **Step 4.9: Commit**

```bash
git add -A
git commit -m "feat: remove token-based advanced login, YouTube WebView login is the only sign-in"
```

Nota: las strings `advanced_login`/`token_*` quedan sin uso en los xml de locales — se dejan a propósito (quitarlas tocaría decenas de traducciones sin beneficio runtime). No existe UI de login de Discord (solo Rich Presence inerte y links de comunidad) — nada más que eliminar.

---

### Task 5 (F4): Util central ShareLinks + test (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/music/echo/utils/ShareLinks.kt`
- Test (create): `app/src/test/kotlin/iad1tya/echo/music/utils/ShareLinksTest.kt`

- [ ] **Step 5.1: Escribir el test que falla**

```kotlin
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
```

- [ ] **Step 5.2: Ejecutar y verificar que FALLA**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.utils.ShareLinksTest"`
Expected: FAIL de compilación — `Unresolved reference: ShareLinks`.

- [ ] **Step 5.3: Implementación mínima**

`app/src/main/kotlin/com/music/echo/utils/ShareLinks.kt`:

```kotlin
package iad1tya.echo.music.utils

/**
 * Builds direct YouTube Music links for sharing. IDs are interpolated as-is so a
 * null ID behaves exactly like the previous inline string templates.
 */
object ShareLinks {
    private const val BASE = "https://music.youtube.com"

    fun song(videoId: String?) = "$BASE/watch?v=$videoId"

    fun playlist(playlistId: String?) = "$BASE/playlist?list=$playlistId"

    fun channel(channelId: String?) = "$BASE/channel/$channelId"
}
```

- [ ] **Step 5.4: Ejecutar y verificar que PASA**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.utils.ShareLinksTest"`
Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 5.5: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/utils/ShareLinks.kt app/src/test/kotlin/iad1tya/echo/music/utils/ShareLinksTest.kt
git commit -m "feat: add ShareLinks util producing direct YouTube Music URLs (TDD)"
```

---

### Task 6 (F4): Reemplazar los 12 usos inline en app por ShareLinks

**Files (Modify):** los 12 call sites actuales de `share.echomusic.fun`:

| Archivo | Línea aprox. | Reemplazo |
|---|---|---|
| `ui/menu/PlayerMenu.kt` | 345 | `ShareLinks.song(mediaMetadata.id)` |
| `ui/menu/OldPlayerMenu.kt` | 292 | `ShareLinks.song(mediaMetadata.id)` |
| `ui/menu/QueueMenu.kt` | 282 | `ShareLinks.song(mediaMetadata.id)` |
| `ui/menu/SongMenu.kt` | 404 | `ShareLinks.song(song.id)` |
| `ui/component/Lyrics.kt` | 1991 | `ShareLinks.song(mediaMetadata?.id)` |
| `playback/MusicService.kt` | 2936 | `ShareLinks.song(songId)` |
| `ui/menu/AlbumMenu.kt` | 367 | `ShareLinks.playlist(album.album.playlistId)` |
| `ui/screens/AlbumScreen.kt` | 938 | `ShareLinks.playlist(albumWithSongs.album.playlistId)` |
| `ui/menu/PlaylistMenu.kt` | 354 | `ShareLinks.playlist(dbPlaylist?.playlist?.browseId)` |
| `ui/menu/PlaylistScreenMenus.kt` | 159 | `ShareLinks.playlist(playlist.playlist.browseId)` |
| `db/entities/PlaylistEntity.kt` | 47 | `ShareLinks.playlist(browseId)` |
| `ui/menu/ArtistMenu.kt` | 206 | `ShareLinks.channel(artist.id)` |

- [ ] **Step 6.1: Reemplazar cada string template por la llamada a ShareLinks**

Patrón en cada sitio — la expresión vieja es un string template, p. ej.:
```kotlin
"https://share.echomusic.fun/watch?v=${mediaMetadata.id}"
```
se convierte en la llamada de la tabla:
```kotlin
ShareLinks.song(mediaMetadata.id)
```
y añadir a cada archivo el import (si no existe ya por wildcard):
```kotlin
import iad1tya.echo.music.utils.ShareLinks
```
Nota `ShareLinks.song` acepta `String?` — los sitios nullable (`Lyrics`, `PlaylistMenu`) conservan semántica idéntica al template original.

- [ ] **Step 6.2: Verificar que app quedó limpia**

Run: `grep -rn "share.echomusic.fun" app/src/`
Expected: 0 resultados.

- [ ] **Step 6.3: Compilar + tests (gate parcial F4)**

Run: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.utils.ShareLinksTest" --tests "iad1tya.echo.music.eq.audio.BiquadFilterTest"`
Expected: BUILD SUCCESSFUL, 10 tests passed.

- [ ] **Step 6.4: Commit**

```bash
git add -A
git commit -m "feat: share songs/playlists/artists with direct YouTube Music links"
```

---

### Task 7 (F4): YTItem.shareLink (innertube) → music.youtube.com, con test primero

**Files:**
- Test (create): `innertube/src/test/kotlin/com/music/innertube/models/YTItemShareLinkTest.kt`
- Modify: `innertube/src/main/kotlin/com/music/innertube/models/YTItem.kt:44,59,76,91`

- [ ] **Step 7.1: Escribir el test que falla (URLs nuevas)**

```kotlin
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
```

- [ ] **Step 7.2: Ejecutar y verificar que FALLA**

Run: `./gradlew :innertube:testDebugUnitTest --tests "com.music.innertube.models.YTItemShareLinkTest"`
Expected: 4 tests FAILED — los getters aún devuelven `share.echomusic.fun`.

- [ ] **Step 7.3: Cambiar los 4 getters en YTItem.kt**

```kotlin
// SongItem
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"

// AlbumItem
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$playlistId"

// PlaylistItem
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"

// ArtistItem
    override val shareLink: String
        get() = "https://music.youtube.com/channel/$id"
```

- [ ] **Step 7.4: Ejecutar y verificar que PASA**

Run: `./gradlew :innertube:testDebugUnitTest --tests "com.music.innertube.models.YTItemShareLinkTest"`
Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 7.5: Verificación global de la migración de links**

Run: `grep -rn "share.echomusic.fun" --include="*.kt" .`
Expected: 0 resultados (solo podría quedar en README/docs .md, no en código).

- [ ] **Step 7.6: Commit**

```bash
git add innertube/src
git commit -m "feat: YTItem share links point directly to music.youtube.com"
```

---

### Task 8: Verificación final, push y aviso

- [ ] **Step 8.1: Suite completa de unit tests**

Run: `./gradlew :app:testUniversalFossDebugUnitTest :innertube:testDebugUnitTest --tests "iad1tya.echo.music.*" --tests "com.music.innertube.utils.ParseCookieStringTest" --tests "com.music.innertube.models.YTItemShareLinkTest"`

Nota: si `--tests` mezclado entre módulos da problemas, correr los dos comandos por separado (app completo sin filtro es seguro: solo existen nuestros tests).

Expected: BUILD SUCCESSFUL — 19 tests (7 Biquad + 5 cookie + 3 ShareLinks + 4 YTItem).

- [ ] **Step 8.2: Build del APK debug (smoke de empaquetado)**

Run: `./gradlew :app:assembleUniversalFossDebug`
Expected: BUILD SUCCESSFUL. Si el equipo local se queda sin memoria, push y verificar vía GitHub Actions (workflow ya capado para 7GB).

- [ ] **Step 8.3: Push**

```bash
git push origin main
```

- [ ] **Step 8.4: Notificar al usuario** (resumen + estado de CI).
