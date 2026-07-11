# Plan super-auditado — Copia de seguridad + (opcional) sync con Google Drive

> Generado por investigación multi-agente sobre el código real + auditoría adversarial. Anclado a `archivo:línea`.
> **Estado: DECISIONES FIJADAS por el owner (abajo). Implementación por fases en rama test/*, sin publicar.**

## DECISIONES DEL OWNER (fijadas 2026-07-10)
1. **Alcance = AMBOS** (local + Drive). El **usuario elige en Ajustes** cómo se hace su copia: **Local / Google Drive / Ambas**.
2. **Unión entre dispositivos = esperar al MERGE REAL (v2).** NO se lanza el restore destructivo LWW como "sincronización". v1 solo hace restore seguro en dispositivo **nuevo/vacío** (sin conflicto que fundir). El merge por-fila real (SUM playCount, orden playlists, renumerado historial) es v2, diferido.
3. **Ubicación Drive = carpeta oculta** `appDataFolder` (privada, no-sensible, se borra al desinstalar).
4. Se mantienen los 3 fixes de blocker: ZIP con secretos despojados + licencia excluida; `VACUUM INTO`; Drive deshabilitado en TV.

## Veredicto rápido
- **Es factible y GRATIS**, pero con matices importantes que la auditoría destapó.
- El login de YouTube es **cookie `SAPISID` en WebView**, NO OAuth (`LoginScreen.kt:181`, `InnerTube.kt:176-183`) → **hace falta un Google Sign-In NUEVO e independiente solo para Drive**.
- Scope **`drive.appdata`** (carpeta oculta privada de la app) → **no-sensible: sin verificación, sin CASA, sin coste, sin tope de 100 usuarios**. Backup en los **15 GB del propio usuario**.
- Solo funciona en el flavor **`gms`**; el flavor por defecto **`foss` (F-Droid) NO tiene Play Services** → ahí queda **backup local-only**.
- **Lo que el usuario pidió ("levantar toda la base de los 2 dispositivos" = union/merge) NO se entrega en v1.** v1 = snapshot + restore que **SOBRESCRIBE** (destructivo). El merge real por-fila es v2, difícil y diferido.

## Los 3 BLOCKERS que la auditoría exige resolver antes de publicar
1. **Secretos en almacenamiento público.** El ZIP incluiría `settings.preferences_pb` con la **cookie `SAPISID` + tokens Spotify/LastFM/Discord/OpenRouter/DeepL en claro**. El espejo local va a `Music/` (world-readable). → **Fix obligatorio: construir UN único ZIP con los secretos DESPOJADOS + licencia excluida**, y usarlo tanto para local como para Drive. El usuario re-loguea YouTube en el dispositivo nuevo.
2. **Corrupción de SQLite en caliente + "sync" mal etiquetado.** El worker corre desatendido y copia `song.db` **sin cerrarla** mientras el playback escribe `event`/`playCount` → archivo corrupto. → **Fix: `VACUUM INTO`** (snapshot consistente sin cerrar). Y re-encuadrar: v1 es **BACKUP** (sobrescribe, puede perder playCounts/datos nuevos entre dispositivos), no "sincronización".
3. **Google Sign-In en Android TV.** El consentimiento OAuth por navegador **no funciona con D-pad**. → **Fix: deshabilitar Drive en TV (local-only)** hasta tener flujo device-code. Y **NO añadir `google-services.json`** para el OAuth (encendería Firebase Analytics/Crashlytics por sorpresa) — usar server-client-ID en código.

Runners-up: retención debe borrar **permanente** (`files.delete`, no papelera, o la cuota nunca se libera); acotar retención **por bytes** no solo por conteo; el token en background es **best-effort** (Drive no garantizado, degradar a local).

## Recomendación corregida — construir en 2 escalones

### Escalón A — Copia de seguridad LOCAL segura (funciona en foss y gms, cero Google, cero coste)
- Cerrar el hueco: añadir al ZIP las 4 SharedPreferences de EQ/apariencia que hoy se pierden (`nanosonic_eq_profiles`, `echo_eq_prefs`, `eq_device_profiles`, `echomusic_settings`).
- **UN ZIP con secretos despojados** + **licencia excluida** (invariante demo/license).
- Snapshot con **`VACUUM INTO`** (sin cortar playback).
- Espejo local `Music/Copia de seguridad de AURA HI-RES` vía **MediaStore.Files (API 29+) / SAF (API ≤28)** — sin permiso nuevo en 29+.
- Retención **por bytes + conteo** con dedupe-por-hash.
- Worker diario `UNMETERED + charging + idle + batteryNotLow` (verificar calor/batería on-device).
- Restore SIEMPRE explícito, muestra **fecha + dispositivo + "esto SOBRESCRIBE"**, gated por `BackupGate`.

Esto ya cubre ~90% del valor ("no perder mi biblioteca"), es 100% gratis y no puede filtrar secretos ni corromper la DB.

### Escalón B — Drive `appdata`, solo `gms`, best-effort
- Proyecto Google Cloud propio + OAuth client Android (package + SHA-1 release **y** debug), scope `drive.appdata`, **publicar a Production** (Testing caduca a 7 días). **Sin `google-services.json`.**
- `AuthorizationClient` (moderno). Token background best-effort → si no hay auth silenciosa, local-only sin fallar.
- `about.get storageQuota` antes **+** manejar `403 storageQuotaExceeded` en la subida. Retención con `files.delete` permanente.
- **Drive deshabilitado en Android TV** (local-only) hasta device-code flow.
- Restore-on-first-run desde Drive: hook en la rama first-run (`MainActivity.kt:1628`) → encontrado (descargar+restore) / no-encontrado (avisar "no se encontró nada" + sugerir Ajustes ▸ Copia de seguridad).

### Diferido explícito (v2)
Merge/union real por-fila (SUM de `playCount`, orden de playlists, renumerado de historial). Si quieres "no perder nada entre dispositivos" antes de eso, usar solo la **capa aditiva `importSelective()`** (insert-IGNORE, peor caso duplicado, nunca pérdida) — nunca un LWW que borre reproducciones.

## Fases (rama test/*, build verde por fase, sin publicar hasta tu orden)
- **F0** Cimientos backup local seguro (VACUUM INTO + ZIP despojado + espejo Music) — S — foss+gms.
- **F1** WorkManager diario local-only (constraints batería) — S/M.
- **F2** Google Cloud + Sign-In de Drive bajo `app/src/gms/` + stub `foss` — M.
- **F3** Subida a Drive + pre-check cuota + 403 + retención permanente — M/L.
- **F4** Restore-on-login + filtrado de secretos — M.
- **F5** UI en Ajustes + Welcome/About (como "copia de seguridad", no "sync") — S/M.
- **F6** (opcional) capa aditiva de merge híbrido — L.

## Decisiones abiertas para ti (sección H)
1. **SDK sign-in:** Credential Manager + AuthorizationClient (moderno, +deps Gradle) vs GoogleSignIn legacy (ya en catálogo, deprecado).
2. **Ubicación Drive:** `appDataFolder` oculto (recomendado) vs carpeta visible `AURA HI-RES` (scope `drive.file`, el usuario la ve/gestiona).
3. **Alcance:** ¿solo Escalón A (local, seguro, ya) o A+B (con Drive)?
4. **API ≤28 local:** `WRITE_EXTERNAL_STORAGE maxSdkVersion=28` vs SAF (grant una vez).
5. **Secretos:** confirmar re-login (no subir cookie/tokens) vs cifrado con passphrase para incluirlos.
6. **FOSS:** confirmar que F-Droid queda local-only (forzoso).
7. **Consentimiento:** aviso explícito la primera vez (reusar el Terms gate existente).

## Archivos clave (patrones a reutilizar)
`viewmodels/BackupRestoreViewModel.kt` (backup/restore ya existe), `viewmodels/BackupGate.kt` (gate de versión DB), `releaseradar/ReleaseRadarWorker.kt` (patrón WorkManager diario), `echomusic/updater/PublicDownloads.kt` (patrón MediaStore→Music), `echomusic/updater/downloadmanager/UpdateDownloadWorker.kt` (foreground worker), `eq/data/EQProfileRepository.kt` (prefs EQ), `db/MusicDatabase.kt` (DB v38, close), `App.kt:184` (scheduleNonCriticalWork), `MainActivity.kt:1628` (hook first-run), `app/src/gms/` + `app/src/foss/` (patrón flavor/stub como Cast).
