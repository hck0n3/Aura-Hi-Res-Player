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

**Arreglos de coincidencia (Tidal)**: se corrigió un fallo que dejaba **0 coincidencias automáticas** en playlists de Tidal (no llegaba el artista de cada pista al emparejador, así que ninguna alcanzaba el umbral y todo caía en "ambiguas"). Ahora emparejan bien. Y si una migración solo produce ambiguas, la playlist se crea al resolver la primera — ya no da "no hay dónde añadir las revisadas".

**Límites reales (sin humo)**: Deezer solo playlists públicas (cerraron el registro de apps). Apple no permite login de terceros (solo su transferencia nativa). Solo Spotify y Tidal permiten login+biblioteca completa.

## 🔐 Login más cómodo
- Botón **"Usar cuenta del teléfono"** en la pantalla de inicio de sesión de YouTube Music: abre el selector de cuentas de Android y pre-rellena tu correo, así no lo tecleas. (La contraseña/verificación se hace una vez; el inicio de sesión de un toque no es posible porque YouTube Music usa cookie de sesión, no un token.)

## 🔧 Correcciones de reproducción
- **Streaming reparado cuando YouTube cambia su reproductor**: se corrigió el descifrado para el reproductor nuevo de YouTube; algunas canciones que tardaban mucho o fallaban ("No hay ninguna fuente disponible") vuelven a sonar. *(Este arreglo también llega a versiones anteriores sin actualizar, vía configuración remota.)*
- **poToken restaurado**: un archivo necesario para autenticarse con YouTube faltaba en las compilaciones; se corrigió. Menos fallos y resoluciones más rápidas.
- **Reconocer canción — ahora reproduce la correcta**: antes mostraba bien la portada/título pero al darle play sonaba **otra canción sin relación**. Eran dos fallos encadenados: usaba un id frágil de Shazam (a menudo el video musical u otra versión) y, al resolver, aceptaba la primera canción con **título parecido aunque fuera de otro artista**. Ahora exige que coincidan **título Y artista**; si no hay una coincidencia fiable, avisa y **no reproduce nada** en vez de sonar cualquier cosa.

## 🎧 Qobuz hi-res con TU suscripción (nuevo)
- Puedes **vincular tu propia cuenta de Qobuz** (Ajustes ▸ Cuentas ▸ Qobuz) pegando tu token o con correo y contraseña. Con la cuenta vinculada, las canciones en calidad sin pérdida se resuelven contra **tu suscripción** y suenan en FLAC hi-res.
- Negocia la mejor calidad disponible automáticamente (24/192 → 24/96 → FLAC 16/44 → 320) y muestra la que realmente se entregó.
- **Requiere suscripción propia**: plan **Studio o Sublime** para 24 bits. Una cuenta gratuita solo obtiene fragmentos de 30 s, y la app lo detecta y no los reproduce como si fueran la canción.
- Tus credenciales se guardan **cifradas en el móvil** y solo se envían a qobuz.com. Desactivado por defecto: si no vinculas cuenta, nada cambia.
- **Letra sincronizada palabra por palabra arreglada**: el estilo por defecto inventaba el ritmo por palabra en canciones que solo traen letra por líneas (la mayoría), y se desincronizaba. Ahora ilumina la línea completa a tiempo; el resaltado real palabra-por-palabra se mantiene solo en las canciones que traen ese dato (estilo Apple).
- **Discografías más completas**: cuando un álbum que falta se completa a través de una lista de la comunidad, ahora se trae el **álbum entero** (todas sus pistas) en vez de solo las que había en esa lista.

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

## 🔊 Reproducción y estabilidad
- **Firma de las peticiones a YouTube corregida**: Aura enviaba sus dos credenciales de reproducción **intercambiadas**, así que cada petición iba firmada con la credencial de la otra. Es la causa típica de los errores 403 y de que una URL de reproducción deje de valer **a mitad de canción**.
- **Menos "se cerró sola"**: al arrancar, la app hacía todo su trabajo pesado **también en los procesos auxiliares** (el de reporte de fallos y el de reinicio). Eso incluía abrir la caché de música por duplicado — dos procesos escribiendo el mismo índice justo cuando la app ya estaba fallando. Ahora ese trabajo solo lo hace el proceso principal: menos riesgo de perder descargas o caché, y menos batería y calor.
- **Cast: el volumen ya funciona siempre.** Si la sesión se reanudaba (volver a la app, o reiniciarse mientras emitías), el deslizador se movía pero **el altavoz no cambiaba de volumen**, sin ningún aviso. Además, ahora el deslizador arranca en el volumen **real** del dispositivo.
- **Alta calidad por JioSaavn más fiable**: se añadió una comprobación de título que impide que suene **otra canción** cuando el servidor devuelve algo que no es lo pedido (antes bastaba con que coincidieran la duración y el artista). Funciona con títulos en cualquier idioma y escritura — español con acentos, hindi, japonés, coreano, ruso — y cuando no puede juzgar (título en un alfabeto y respuesta en otro) se aparta en vez de rechazar, para no dejar sin alta calidad a nadie.
- **Reproducción por Chromecast más robusta** al elegir dispositivo (arregla un fallo en móviles Xiaomi).
- **Auto-reparación de reproducción ampliada**: la lista de configuraciones que permite arreglar la reproducción sin actualizar la app pasa de 5 a **390** entradas.

## 🔀 Modo aleatorio: que de verdad no repita
- **Al reabrir la app ya no empieza de cero.** Si la app se cerraba (o el sistema la cerraba) y volvías a entrar, el aleatorio arrancaba **sin tu historial**: seguía guardado, pero no se leía. Ahora se recupera siempre.
- **La última canción sin escuchar ya no se pierde.** Al cerrar cada vuelta, la única que te quedaba por oír acababa **al fondo del montón** y sonaban repetidas hasta terminar el ciclo.
- **Las canciones de la radio infinita también cuentan** como escuchadas (antes se rebarajaban todas por igual al acabarse la playlist).
- **"···" → Aleatorio** en **álbumes y artistas** ahora enciende el aleatorio de verdad. Antes solo desordenaba la lista una vez, con el modo apagado y sin sistema anti-repetición.
- **Memoria entre días donde no la había**: **"Mi Top"** (con memoria separada por periodo), **"Caché"**, **álbumes** y **artistas**.
- **"Me gusta", "Descargadas", "Subidas" y "Exportadas"**: antes se guardaban en **dos sitios distintos** según entraras por Biblioteca o por la tarjeta, así que lo escuchado por un lado era invisible por el otro. Ahora es uno solo.
- **Android Auto**: lo que reproduces en el coche ya se apunta **en la lista correcta** (antes se anotaba en la última lista abierta en el móvil, ensuciando una que ni estabas oyendo) y su botón "Aleatorio" activa el sistema completo.
- **Al cambiar de playlist**: si la nueva tardaba en cargar, lo que sonaba mientras tanto se apuntaba en la lista equivocada. Corregido.

## 📱 Interfaz y varios
- La **cola colapsada ya no tapa** la parte baja del reproductor en horizontal y en tablet.
- Los **botones flotantes** respetan tu color de texto de Liquid Glass (eran la única superficie de cristal que no lo hacía).
- **Cast**: el volumen que cambies **en el altavoz o con el mando de la tele** se refleja en la app.
- **La app pesa 4 MB menos**: se empaquetaba una tipografía que no usaba nadie.
- **Reconocer canción**: cerrado un último caso en el que podía sonar otra canción del mismo artista cuyo título contenía al reconocido (por ejemplo *"Sola"* → *"Ella Baila Sola"*).

---

## Detalle técnico (interno — no para el público)
- Migración: módulo `:migration` (resolver duración+artista+título+álbum+versión, caché de coincidencias en Room propio, tumbas fuera de MusicDatabase v39), `YtmClientInnerTube` sobre el innertube de Aura, import en `Dispatchers.IO` (fix crítico: corría en Main → Deezer muerto/ANR), freno 120 ms/pista, playlist creada con `bookmarkedAt` + `browseId`. Scorer recalibrado (falsos positivos previos). Tidal: PKCE, tokens en EncryptedSharedPreferences, callback `echomusic://tidal-callback` antes que Listen Together, colección `/me` con respaldo a id numérico + lectura included/data, client-id en `BuildConfig.TIDAL_CLIENT_ID` (no es secreto, patrón Last.fm). Logging del fallo de colección (403/404/vacío distinguibles). Gate de sesión YT Music en el picker.
- Login: `AccountManager.newChooseAccountIntent` (framework AOSP, sin GMS ni permiso GET_ACCOUNTS → funciona en FOSS y GMS), URL con `Email`/`login_hint`, construida off-Main.
- Cipher: `player_configs.json` con la config del player hash nuevo (self-healing remoto).
- poToken: `app/src/main/assets/po_token.html` estaba en `.gitignore` (patrón `*token*`) y nunca commiteado → ausente en el APK de CI; `.gitignore` corregido con negación + asset commiteado. Sin secretos (harness BotGuard).
- Verificación: cada lote pasó re-auditoría adversarial antes de etiquetar. Rondas previas cazaron ~30 defectos, la mayoría en los propios arreglos.
