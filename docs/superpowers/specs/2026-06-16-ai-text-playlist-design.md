# Diseño — Lista AI por texto (JR MUSIC PRO)

**Creado:** 2026-06-16
**Branch:** feat/jr-dsp-and-android-improvements
**Roadmap:** `docs/superpowers/roadmaps/2026-06-16-ux-features-roadmap.md` → Ola A, feature #1
**Estado:** APROBADO — listo para plan de implementación.

---

## 1. Objetivo

El usuario escribe una idea en lenguaje natural ("rock para correr de noche", "boleros tristes para llover")
y la IA arma una playlist. Las canciones se resuelven contra el catálogo (innertube/YouTube) y se crea una
**playlist local** lista para reproducir.

Reusa la IA ya cableada (hoy solo traduce letras) y el pipeline de importación de playlists que ya resuelve
canciones por búsqueda. Feature **additiva**: pantallas/clases nuevas, sin reescribir lo que funciona.

## 2. Decisiones (confirmadas con el usuario)

- **Entrada UI:** botón "Lista AI" en `LibraryPlaylistsScreen`, junto al `+` de crear playlist.
- **Nombre:** la IA propone un nombre corto; si falla, se usa el texto del usuario como fallback.
- **Nº de canciones:** ajustable (10 / 20 / 30 / 50), default **20**.
- **Proveedores:** chat OpenAI-compatible (OpenRouter, OpenAI, Perplexity, Gemini, XAi, Mistral, Nvidia).
  DeepL y Claude **no** se soportan en esta feature (esquemas distintos) → mensaje que invita a elegir un
  proveedor de chat en Ajustes IA.

## 3. Componentes (todos nuevos salvo donde se indica)

### 3.1 `AiPlaylistService` (`app/.../api/AiPlaylistService.kt`)
- Objeto que manda un chat completion OpenAI-compatible, copiando el patrón de `OpenRouterService`
  (OkHttp, headers, retries 5xx, timeouts).
- Firma:
  ```kotlin
  suspend fun generate(
      prompt: String,
      count: Int,
      provider: String,
      apiKey: String,
      baseUrl: String,
      model: String,
  ): Result<AiPlaylistSpec>
  ```
- Construye los mensajes vía `AiPlaylistPrompt`, parsea la respuesta vía `AiPlaylistParser`.
- Proveedor no soportado (DeepL/Claude) o `apiKey` en blanco → `Result.failure` con mensaje claro
  (la UI traduce a recurso string).

### 3.2 `AiPlaylistPrompt` (`app/.../api/AiPlaylistPrompt.kt`) — **puro, TDD**
- `fun buildMessages(prompt: String, count: Int): JSONArray` (system + user).
- System prompt: "Eres un curador musical. Devuelve SOLO un objeto JSON
  `{ \"name\": string, \"tracks\": [ { \"title\": string, \"artist\": string } ] }` con EXACTAMENTE N tracks,
  sin texto adicional." `name` corto (≤ 40 chars).
- Sin dependencias de Android → unit-testable.

### 3.3 `AiPlaylistParser` (`app/.../api/AiPlaylistParser.kt`) — **puro, TDD**
- `fun parse(content: String, expectedCount: Int): Result<AiPlaylistSpec>`.
- Extracción tolerante (mismo enfoque que `OpenRouterService`): intenta JSON directo, luego quita
  ```` ```json ```` / ```` ``` ````, luego recorta entre el primer `{` y el último `}`.
- Valida: `tracks` no vacío; cada track con `title` no en blanco (artist puede faltar).
- **Dedupe** por `(title, artist)` case-insensitive; **recorta** a `expectedCount`.
- `name` en blanco → se deja vacío (el generador aplica el fallback al texto del usuario).
- Sin tracks válidos → `Result.failure`.

```kotlin
data class AiPlaylistSpec(val name: String, val tracks: List<TrackQuery>)
data class TrackQuery(val title: String, val artist: String)
```

### 3.4 `SongResolver` (`app/.../playlistimport/SongResolver.kt`) — **puro-ish, TDD**
- Extraído de la lógica que hoy vive en `JrPlaylistImporter.resolveTrack` para compartirla (DRY).
- `suspend fun resolve(database, title, artist, byVideoId: Map<String, SongItem> = emptyMap(), videoId: String? = null): MediaMetadata?`
- Orden: (1) match local por título+artista, (2) `videoId` embebido vía `byVideoId`, (3) `YouTube.search(query, FILTER_SONG)` → primer `SongItem`.
- **`JrPlaylistImporter` se refactoriza para usar `SongResolver`** (mismo comportamiento; el importador JR
  sigue funcionando igual). Cambio mínimo, sin tocar su API pública.

### 3.5 `AiPlaylistGenerator` (`app/.../playlistimport/AiPlaylistGenerator.kt`)
- Orquesta: `AiPlaylistService.generate(...)` → por cada `TrackQuery` `SongResolver.resolve(...)`
  (reporta progreso `resolved/total`) → `database.transaction { insert(playlist); insert(metadata) + PlaylistSongMap }`.
- Nombre playlist = `spec.name.ifBlank { userPrompt }` recortado.
- Devuelve `Result(playlistId, name, total, resolved)`.
- 0 resueltas → `Result.failure` (no crea playlist vacía).

### 3.6 `AiPlaylistViewModel` (`app/.../viewmodels/AiPlaylistViewModel.kt`, `@HiltViewModel`)
- Expone `StateFlow<AiPlaylistState>`:
  `Idle | Generating | Resolving(done, total) | Success(playlistId, name, resolved, total) | Error(msg)`.
- `fun generate(prompt, count, provider, apiKey, baseUrl, model)` lanza en `viewModelScope` (IO), cancelable.
- Inyecta `MusicDatabase`.

### 3.7 `AiPlaylistDialog` (`app/.../ui/component/AiPlaylistDialog.kt`)
- Campo de texto (prompt) + selector de nº (10/20/30/50, def 20) + botón **Generar**.
- Lee prefs IA con `rememberPreference`: `AiProviderKey`, `OpenRouterApiKey`, `OpenRouterBaseUrlKey`, `OpenRouterModelKey`.
- Estados: progreso ("Generando…", "Resolviendo n/m"), error inline.
- `apiKey` en blanco o proveedor no soportado → mensaje + acción "Ir a Ajustes IA".
- Éxito → `onPlaylistCreated(playlistId)` → navega a `LocalPlaylistScreen` + toast `X/Y resueltas`.

### 3.8 Entrada en `LibraryPlaylistsScreen` (existente — cambio aditivo)
- Botón/acción "Lista AI" (icono sparkle) que abre `AiPlaylistDialog`. Visible solo si `AiPlaylistEnabledKey`.

### 3.9 Settings toggle
- `AiPlaylistEnabledKey` (`PreferenceKeys.kt`), default `true`.
- Item en Ajustes (junto a la sección IA / contenido) para apagar la feature si falla.

## 4. Flujo de datos

```
AiPlaylistDialog (prompt, count, prefs IA)
  → AiPlaylistViewModel.generate()
      → AiPlaylistService.generate()  → AiPlaylistPrompt + HTTP + AiPlaylistParser → AiPlaylistSpec
      → for each TrackQuery: SongResolver.resolve()  (progreso Resolving n/m)
      → AiPlaylistGenerator: database.transaction { playlist + songs + PlaylistSongMap }
  → Success(playlistId) → navega a LocalPlaylistScreen + toast
```

## 5. Manejo de errores

| Caso | Resultado |
|------|-----------|
| `apiKey` en blanco | Mensaje + "Ir a Ajustes IA"; no llama red |
| Proveedor DeepL/Claude | Mensaje "elige proveedor de chat"; no llama red |
| IA responde error / 5xx tras retries | `Error(msg)` en el diálogo |
| JSON no parseable | `Error` ("respuesta IA inválida, reintenta") |
| 0 canciones resueltas | toast "no se encontraron canciones"; no crea playlist |
| Resolución parcial | crea playlist con lo resuelto + toast `X/Y` |
| Cancelación (cerrar diálogo) | `viewModelScope` cancela; sin escritura parcial (transacción atómica) |

## 6. Tests (TDD)

Lógica pura sin red ni DB:
- **`AiPlaylistParserTest`**: JSON limpio; con ```` ```json ````; con prosa antes/después; `name` ausente;
  más tracks de los pedidos (recorta); menos (acepta); duplicados (dedupe); malformado → `failure`;
  tracks sin título descartados; sin tracks válidos → `failure`.
- **`AiPlaylistPromptTest`**: incluye el `count` y el prompt del usuario; pide JSON; roles system/user.
- **`SongResolverTest`**: precedencia local > videoId > search (con dobles/fakes de `database`/`YouTube`
  donde sea viable; si `YouTube` es objeto difícil de mockear, cubrir al menos local-match y orden con un
  resolver inyectable). `JrPlaylistImporter` sigue compilando y resolviendo igual tras el refactor.

Red (`AiPlaylistService`) y DB (`AiPlaylistGenerator`) quedan como integración manual (APK), igual que el
resto del proyecto que no tiene tests de red.

## 7. Principios / no romper

- Additivo: clases y un diálogo nuevos; el único cambio a código existente es extraer `SongResolver`
  desde `JrPlaylistImporter` (mismo comportamiento) y añadir el botón en `LibraryPlaylistsScreen`.
- Toggle en Settings (`AiPlaylistEnabledKey`).
- BYO API key: usa la clave/proveedor que el usuario ya configuró para traducir letras. Sin coste para nosotros.
- Build verde + APK en nube antes de pasar a la siguiente feature del roadmap. No mergear a main salvo que el usuario lo pida.

## 8. Fuera de alcance (YAGNI)

- Soporte Claude (`/v1/messages`) y DeepL (no es chat) — posible fase posterior.
- Edición del nombre por el usuario antes de guardar (se eligió "IA propone").
- Streaming de la respuesta IA (no aporta aquí; la lista se usa completa).
- Sincronizar la playlist con YouTube Music (es local; el usuario puede sincronizar luego con el flujo existente).
- Regenerar / "más como esta" — futuro.
