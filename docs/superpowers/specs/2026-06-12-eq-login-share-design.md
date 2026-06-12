# Diseño: Axion EQ único, login solo YouTube, share links directos

**Fecha:** 2026-06-12
**Proyecto:** JR-MUSIC-PRO (Echo Music 5.1.8 fork)
**Estado:** Aprobado por instrucción directa del usuario (ejecución autónoma)

## Objetivo

Cuatro cambios pedidos por el usuario:

1. **F1 — Ecualizador:** eliminar el ecualizador original de Echo y dejar el ecualizador Axion (integrado en sesión anterior) como único ecualizador de la app.
2. **F2 — Login YouTube:** el único login de la app es la cuenta de YouTube / YouTube Music (WebView de Google), usada para sincronizar la biblioteca del usuario.
3. **F3 — Eliminar otros logins:** quitar el "Advanced login" (editor de token/cookie manual). No existe UI de login de Discord (solo Rich Presence inerte y links de comunidad), así que no hay nada más que quitar.
4. **F4 — Share links:** al compartir, generar links directos de YouTube Music (`https://music.youtube.com/...`) en vez de `https://share.echomusic.fun/...`.

Metodología exigida por el usuario: cada feature se implementa, se deja testeable y con tests pasando antes de avanzar a la siguiente.

## Contexto descubierto

- Axion EQ (`ui/screens/equalizer/axion/`) es UI sobre el **mismo motor** que usa la app: `eq/EqualizerService` + `eq/audio/CustomEqualizerAudioProcessor` (biquads en cadena de ExoPlayer) + `eq/data/EQProfileRepository`. El motor se conserva.
- EQ viejo = solo 3 archivos de UI: `EqScreen.kt` (dialog ruta `"equalizer"`), `EQViewModel.kt`, `EQState.kt`. Nadie más los referencia (verificado por grep).
- Rutas: `"settings/equalizer"` ya abre `AxionEqScreen`. `dialog("equalizer")` abre el EQ viejo y lo invocan `PlayerMenu.kt:720` y `OldPlayerMenu.kt:645`.
- Login actual: ruta `"login"` → `LoginScreen` (WebView Google → cookie SAPISID + visitorData + dataSyncId → `YouTube.accountInfo()` valida → reinicio). `AccountSettingsScreen` además expone "Advanced login" (editor de token) vía `saveTokenAndRestart`.
- Discord: solo `DiscordRPC`/`SuperProperties` (Rich Presence; el token nunca se puede setear desde UI — no hay pantalla) y links de invitación en `WelcomeDialog`/`AboutScreen`. No es un método de login de biblioteca.
- Share links `share.echomusic.fun` en 12 sitios de `app` + 4 getters `shareLink` en `innertube/models/YTItem.kt` + `db/entities/PlaylistEntity.kt`.
- **No se tocan** (no son share): `https://echomusic.fun/p/privacy-policy` y `/p/toc` (SettingDialoge), `https://canvas.echomusic.fun/canvas.json` (API de canvas), menciones en README/docs.
- Tests: ningún módulo tiene tests escritos; `app` ni siquiera declara `testImplementation(junit)`. Los módulos de librería (innertube, etc.) ya declaran junit.

## Diseño por feature

### F1 — Axion EQ único

Cambios:
- Borrar `app/src/main/kotlin/com/music/echo/ui/screens/equalizer/EqScreen.kt`, `EQViewModel.kt`, `EQState.kt`.
- `NavigationBuilder.kt`: quitar `import ...equalizer.EqScreen` y el bloque `dialog("equalizer") { EqScreen(...) }`.
- `PlayerMenu.kt:720` y `OldPlayerMenu.kt:645`: `navigate("equalizer")` → `navigate("settings/equalizer")` (Axion).
- Conservar: paquete `eq/` completo (motor), `CrossfeedAudioProcessor`, `SpectrumAudioProcessor`, entrada en `PlayerSettings` (ya apunta a Axion).

Tests (nuevos, `app/src/test`):
- `BiquadFilterTest`: filtro peaking con gain 0 ≈ passthrough; gain +12 dB a 1 kHz amplifica seno de 1 kHz (~×3.98 en régimen) y deja casi plano 100 Hz; `reset()` limpia estado; shelf bajo/alto suben la banda esperada.
- `ParametricEQTest` (si la clase es pura): parseo/estructura de bandas.
- Infra: añadir `testImplementation(libs.junit)` a `app/build.gradle.kts`.

Verificación: grep sin referencias residuales a EqScreen/EQViewModel/EQState ni `navigate("equalizer")`; `:app:testDebugUnitTest` verde (compila app completa).

### F2 — Login YouTube/YT Music para biblioteca

El flujo WebView existente se conserva tal cual (es el mecanismo correcto: cookie de cuenta Google → InnerTube con SAPISID → biblioteca y sync con `YtmSyncKey`). No requiere cambios de producto, solo dejarlo como único camino y cubrirlo con tests en lo testeable:

Tests (nuevos, `innertube/src/test`):
- `ParseCookieStringTest`: cookie real-formato (`SAPISID=...; HSID=...`) → mapa correcto; detección `"SAPISID" in parseCookieString(c)` (criterio de "logueado" usado por la app); cookies vacías/malformadas no crashean; valores con `=` internos.

Verificación: `:innertube:test` verde.

### F3 — Eliminar otros métodos de login

Cambios:
- `AccountSettingsScreen.kt`: eliminar el grupo "Advanced login" completo (item token, estados `showToken`/`showTokenEditor`, bloque `TextFieldDialog` del editor de token) e imports que queden huérfanos (`TextFieldValue`). `parseCookieString` se queda (lo usa `isLoggedIn`).
- `AccountSettingsViewModel.kt`: eliminar `saveTokenAndRestart` (queda huérfano). Conservar logout.
- Strings `advanced_login`/`token_*` quedan sin uso en xml — se dejan (quitarlas rompería locales traducidos; sin impacto runtime).
- Discord: sin UI de login que quitar; Rich Presence queda inerte como está. Links de comunidad (Discord/Telegram) no son logins y se quedan.

Verificación: grep sin `saveTokenAndRestart`/`showTokenEditor`; app compila; login WebView sigue siendo la única entrada (`navigate("login")`).

### F4 — Share links directos de YouTube Music

Cambios:
- Nuevo `app/src/main/kotlin/com/music/echo/utils/ShareLinks.kt`:
  ```kotlin
  object ShareLinks {
      const val BASE = "https://music.youtube.com"
      fun song(videoId: String) = "$BASE/watch?v=$videoId"
      fun playlist(playlistId: String) = "$BASE/playlist?list=$playlistId"
      fun channel(channelId: String) = "$BASE/channel/$channelId"
  }
  ```
- Reemplazar los 12 usos inline en `app`: PlayerMenu, OldPlayerMenu, QueueMenu, SongMenu, AlbumMenu (playlist), ArtistMenu (channel), PlaylistMenu, PlaylistScreenMenus, AlbumScreen, Lyrics, MusicService (share intent), PlaylistEntity (`shareLink` getter; entidad Room no puede depender de utils de UI — sí puede, mismo módulo; usar ShareLinks).
- `innertube/models/YTItem.kt`: los 4 getters `shareLink` pasan a `https://music.youtube.com/...` (módulo independiente, string directo).

Tests:
- `app/src/test/.../ShareLinksTest`: formato exacto de las 3 funciones con IDs reales (`_KZEkEb_dvA` etc.).
- `innertube/src/test/.../YTItemShareLinkTest`: `SongItem.shareLink == "https://music.youtube.com/watch?v=..."`, ídem Album (playlistId), Playlist, Artist (channel).

Verificación: grep `share.echomusic.fun` → 0 resultados en código (solo docs); tests verdes en ambos módulos.

## Orden de implementación

F1 → F2 → F3 → F4, cada una gated por sus tests (regla del usuario). Al final: `testDebugUnitTest` completo + `assembleDebug`, commit y push.

## Riesgos

- Compilación local de `app` es pesada (gradle.properties ya capado por OOM de CI); si el equipo local no aguanta, fallback a CI de GitHub Actions como verificación.
- `PlaylistEntity` es entidad Room: añadir import de utils es seguro (getter no persistido, `@Ignore` implícito por ser val computado sin backing field).
- Borrar EqScreen: riesgo bajo, referencias verificadas por grep antes y después.
