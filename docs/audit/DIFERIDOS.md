# Diferidos — requieren paso cuidadoso (no "a lo loco")

Items reales del catálogo/auditoría que NO se arreglaron en las olas automáticas por riesgo de romper algo sensible (sesiones, licencia, audio, DB, o por necesitar tu CI/pruebas en dispositivo). Cada uno lleva la razón + el plan seguro.

## Seguridad
- **Cifrado de tokens de sesión + `subscription_key`** (audit HIGH). Hoy en claro en DataStore/`jr_license` (ya excluidos del backup en Ola 3, que corta la vía fácil `adb backup`). Cifrarlos exige mover esos valores a EncryptedSharedPreferences / serializer cifrado con **migración**: leer el valor plano una vez → escribir cifrado → borrar el plano, sin desloguear a los usuarios actuales. Riesgo alto (auth/sesiones) → hacerlo aislado + probado en dispositivo. NO tocar el grace de 3 días (decisión: conservador).
- **Secreto LastFM embebido** (`app/build.gradle.kts`). Quitarlo del fuente exige moverlo a una variable de entorno/gradle property en tu CI; si se quita sin configurar el env, LastFM (scrobble) deja de firmar. Requiere tu setup de CI + rotar la clave.
- **Cert pinning** (ausente en todos los clientes). Reduce MITM, pero pinnear mal rompe la app cuando YouTube/Cloudflare rotan certificados. Evaluar con cuidado (pin de respaldo + expiración).

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
