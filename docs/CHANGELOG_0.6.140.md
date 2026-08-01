# Registro de cambios — 0.6.140 (acumulado de las betas)

> **Uso**: este archivo junta TODO lo que se probó en las betas privadas 0.6.140-betaN.
> Cuando 0.6.140 salga al público, estas notas son su registro de cambios.
> Los usuarios no ven "beta1/beta2"; ven las secciones de abajo agrupadas por tema.

---

## 🎵 Nuevo: migrar playlists de otros servicios a YouTube Music
Trae tus playlists de otras plataformas a tu cuenta de YouTube Music.
**Dónde**: Biblioteca ▸ botón "Importar" ▸ **Migrar playlist**, y también Ajustes ▸ Copia y restauración ▸ Importar (junto a Spotify).

- **Archivo (CSV / M3U / JSPF)** — exporta tu playlist de cualquier servicio (TuneMyMusic, Soundiiz, export de datos de Spotify/Apple, o un reproductor local) e impórtala. Sin login.
- **Deezer** — pega el enlace de una playlist pública.
- **Tidal** — inicia sesión con tu cuenta y trae **todas tus playlists** (incluidas las privadas), o pega el enlace de una. El client-id ya viene en la app: no tienes que pegar nada.
- **Apple Music** — guía hacia la transferencia nativa de Apple (lo más fiable para Apple).

**Cómo elige las canciones**: cada canción se busca en YouTube Music y se puntúa por duración + artista + título + álbum + versión. Nunca inserta una coincidencia dudosa en silencio: si no está claro, va a "revisar" y decides tú. Las etiquetas Remix/Live/Acústico penalizan fuerte para no traer la grabación equivocada. Tu corrección manual se guarda como definitiva.

**Requisito honesto**: la playlist se crea en tu cuenta de **YouTube Music**, así que debes tener sesión de YT Music iniciada. Si no la tienes, la pantalla te lo pide primero (no te deja perder el viaje eligiendo una playlist para fallar al final).

**Límites reales (sin humo)**: Deezer solo playlists públicas (cerraron el registro de apps). Apple no permite login de terceros (solo su transferencia nativa). Solo Spotify y Tidal permiten login+biblioteca completa.

## 🔐 Login más cómodo
- Botón **"Usar cuenta del teléfono"** en la pantalla de inicio de sesión de YouTube Music: abre el selector de cuentas de Android y pre-rellena tu correo, así no lo tecleas. (La contraseña/verificación se hace una vez; el inicio de sesión de un toque no es posible porque YouTube Music usa cookie de sesión, no un token.)

## 🔧 Correcciones de reproducción
- **Streaming reparado cuando YouTube cambia su reproductor**: se corrigió el descifrado para el reproductor nuevo de YouTube; algunas canciones que tardaban mucho o fallaban ("No hay ninguna fuente disponible") vuelven a sonar. *(Este arreglo también llega a versiones anteriores sin actualizar, vía configuración remota.)*
- **poToken restaurado**: un archivo necesario para autenticarse con YouTube faltaba en las compilaciones; se corrigió. Menos fallos y resoluciones más rápidas.
- **Reconocer canción — ahora reproduce la correcta**: antes mostraba bien la portada/título pero al darle play sonaba otra canción (usaba un id frágil de Shazam, a menudo el video musical u otra versión). Ahora reproduce exactamente la canción que reconoció; si no encuentra una coincidencia fiable, avisa en vez de sonar cualquier cosa.
- **Letra sincronizada palabra por palabra arreglada**: el estilo por defecto inventaba el ritmo por palabra en canciones que solo traen letra por líneas (la mayoría), y se desincronizaba. Ahora ilumina la línea completa a tiempo; el resaltado real palabra-por-palabra se mantiene solo en las canciones que traen ese dato (estilo Apple).

## 📚 Biblioteca y playlists
- **Buscar entre tus playlists**: campo de búsqueda en la pestaña Playlists — filtra tus listas por nombre al escribir.
- **Sincronizar una playlist a mano**: en cada playlist vinculada a tu cuenta de YouTube, opción "Sincronizar ahora" para actualizar esa lista cuando quieras. (De paso se corrigió un fallo que podía vaciar una playlist si la sincronización traía una respuesta vacía momentánea.)

## 🔗 Abrir enlaces de YouTube / YouTube Music
- Aura abre más tipos de enlace correctamente: canción (`watch`, `embed`, `/v/`, `shorts`, `youtu.be`, `vnd.youtube`), playlist, álbum, **artista** y búsqueda — antes varios se caían o abrían la app en blanco.
- Ajuste para que Aura **aparezca en el selector "Abrir con"**. Nota: Android no permite reemplazar a la fuerza a la app de YouTube Music; para que Aura los abra, ponla por defecto en Ajustes ▸ Apps ▸ Aura ▸ "Abrir de forma predeterminada".

## ⚡ Fluidez y optimización (todas las gamas)
- Los fondos animados del reproductor ya **no se dibujan cuando el reproductor está minimizado** (gasto invisible eliminado — más fluidez y batería en todas las gamas).
- El fondo animado del **mini-reproductor** (siempre visible) ahora respeta el Modo Alto Rendimiento y el freno térmico, igual que el reproductor grande.
- **Piso por hardware**: en dispositivos de gama baja/ultra-baja los fondos con shaders y el segundo decodificador nunca se activan, aunque se apague el Modo Alto Rendimiento — la app se mantiene fluida en equipos débiles.

---

## Detalle técnico (interno — no para el público)
- Migración: módulo `:migration` (resolver duración+artista+título+álbum+versión, caché de coincidencias en Room propio, tumbas fuera de MusicDatabase v39), `YtmClientInnerTube` sobre el innertube de Aura, import en `Dispatchers.IO` (fix crítico: corría en Main → Deezer muerto/ANR), freno 120 ms/pista, playlist creada con `bookmarkedAt` + `browseId`. Scorer recalibrado (falsos positivos previos). Tidal: PKCE, tokens en EncryptedSharedPreferences, callback `echomusic://tidal-callback` antes que Listen Together, colección `/me` con respaldo a id numérico + lectura included/data, client-id en `BuildConfig.TIDAL_CLIENT_ID` (no es secreto, patrón Last.fm). Logging del fallo de colección (403/404/vacío distinguibles). Gate de sesión YT Music en el picker.
- Login: `AccountManager.newChooseAccountIntent` (framework AOSP, sin GMS ni permiso GET_ACCOUNTS → funciona en FOSS y GMS), URL con `Email`/`login_hint`, construida off-Main.
- Cipher: `player_configs.json` con la config del player hash nuevo (self-healing remoto).
- poToken: `app/src/main/assets/po_token.html` estaba en `.gitignore` (patrón `*token*`) y nunca commiteado → ausente en el APK de CI; `.gitignore` corregido con negación + asset commiteado. Sin secretos (harness BotGuard).
- Verificación: cada lote pasó re-auditoría adversarial antes de etiquetar. Rondas previas cazaron ~30 defectos, la mayoría en los propios arreglos.
