# Diferidos — requieren paso cuidadoso (no "a lo loco")

Items reales del catálogo/auditoría que NO se arreglaron en las olas automáticas por riesgo de romper algo sensible (sesiones, licencia, audio, DB, o por necesitar tu CI/pruebas en dispositivo). Cada uno lleva la razón + el plan seguro.

## Estado (tras el pase de diferidos D1–D2, rama `audit/deferred`)
- ✅ **HECHO:** P46 `App.newImageLoader` runBlocking (mirror no-bloqueante), P47 buffer native (passthrough), P48 transitorio EQ (+ lock), AppleToken 401, comentario FGS. Además: perf-mode por capacidad (arregla TV/car boxes débiles), crossfade-off en gama-baja, menos concurrencia/efectos, **anti-sobrecalentamiento** (throttle térmico + video-artista no decodifica en background).
- ⏳ **Sigue diferido:** cifrado de tokens (plan abajo), `DataStore.get` en rememberPreference, modernización de toolchain (Kotlin 2.4), secreto LastFM (tu CI). Cert pinning = **no recomendado**.

## Seguridad
- **Cifrado de tokens de sesión + `subscription_key`** (audit HIGH). DIFERIDO — plan listo. Los tokens (`spotify_sp_dc/sp_key/access_token`, `innerTubeCookie`, `visitorData`, `dataSyncId`) se propagan por **flujos reactivos que leen el `Preferences` crudo** (`App.kt:742/756/769`) y por snapshots (Spotify/ReleaseRadar), sin choke-point por clave: hay **~30 sitios** de lectura/escritura (workers, 10 pantallas Compose, viewmodels, arranque). Cifrar bajo las mismas claves del DataStore "settings" haría `YouTube.cookie = ciphertext` → parseo falla → `forgetAccount()` = **logout de todos**. Plan seguro (pase dedicado + build + pruebas): (1) `SecureTokenStore` sobre EncryptedSharedPreferences (AES-256-GCM, MasterKey Keystore, en fichero propio, try/catch → fallback plaintext, nunca crash/logout); añadir `androidx.security:security-crypto`; (2) accessors tipados con lectura `secure ?: plaintext`; (3) rutear los ~30 sitios + descifrar cada emisión de los 3 flujos reactivos de App.kt; (4) migración one-time con **clave fresca** (`secure_tokens_migrated_v1`): copiar plaintext→cifrado→borrar plano (la sesión sobrevive); (5) `subscription_key` primero (aislado, `LicenseManager.load/save` :42/:51; fallo de descifrado ⇒ re-verify/demo, nunca lockout; no tocar el grace). Probar login/logout/refresh/rotate YouTube+Spotify y licencia activa/demo/mismatch, con Keystore normal y forzando fallo de Keystore.
- **Secreto LastFM embebido** (`app/build.gradle.kts`). Quitarlo del fuente exige moverlo a una variable de entorno/gradle property en tu CI; si se quita sin configurar el env, LastFM (scrobble) deja de firmar. Requiere tu setup de CI + rotar la clave.
- **Cert pinning** — **NO recomendado.** En una app que scrapea YouTube (googlevideo rota certs seguido) + Cloudflare (licencia), un pin equivocado o una rotación rompe streaming/licencia a TODOS, y no es verificable sin dispositivo. El riesgo de rotura supera el valor. La exclusión de backup (0.6.80) ya cubre la vía fácil.

## Estabilidad / ANR (load-bearing)
- **`App.newImageLoader` runBlocking** (P46). El `runBlocking` es load-bearing: `StorageSettings` reconstruye el ImageLoader al cambiar el tamaño de caché y debe leer el valor recién commiteado; cualquier reemplazo async iría por detrás → regresión de settings. Fix seguro = pasar el tamaño explícito por el path de reset o un espejo escrito atómicamente antes del `reset()`.
- **`DataStore.get` en `rememberPreference`** (P33-Compose). Necesita valor síncrono en el primer frame; un snapshot rezagado da flicker/valor viejo. Fix seguro = snapshot en memoria poblado async + fallback bloqueante solo si no está listo, garantizando valor inicial correcto. Requiere pruebas de settings.

## Correctness que necesitan cambio estructural / DB / native
- **P39 índice en `event.timestamp`** (DB perf). ~12 queries de analytics hacen full-scan. Fix = añadir índice vía **migración Room v37→v38** (no destructiva). Cambia la versión de DB → requiere su migración + prueba.
- **P47 `SuperpoweredBridge.cpp` buffer overflow** (native audio). Un bloque > MAX_BUFFER_SIZE devuelve sin escribir el output → glitch audible con el buffer reusado. Nativo/tiempo-real → arreglar con cuidado (política: solo tocar el motor para mejorarlo, probado).
- **P48 re-aplicación de EQ bajo mutex** (audio). Un cambio de perfil re-aplica el EQ en muchas llamadas JNI con lock; el hilo de audio puede procesar a mitad (curva transitoria) o calarse. Fix = aplicar el perfil de forma atómica. Requiere prueba de audio.
- **P13 MiniPlayer dual surface**. MiniPlayer y el player inmersivo enlazan dos TextureView al único ExoPlayer durante el drag del sheet. Fix necesita exponer el `BottomSheetState`/progreso a MiniPlayer (gate en `Player.kt`), fuera del alcance de solo-MiniPlayer.kt.

## Cosméticos (bajo, sin impacto funcional)
- Comentario engañoso del fallback FGS en `AudioExportService` (P11).
- `AppleMusicTokenProvider.invalidate()` sin cablear (el expiry/TTL sí funciona).
- P26 latente similar en `fetchAllArtists` (myArtists) — mismo patrón rawCount si Spotify fija pseudo-ítems; aplicar igual que P6/P26 si se reporta.
- 2 carruseles Material3 del Home sin `tvFocusRestorer` (pocos ítems, casi siempre en pantalla) — reutilizable el helper si se reporta pérdida de foco ahí.
