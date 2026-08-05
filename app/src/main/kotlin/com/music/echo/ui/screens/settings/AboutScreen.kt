@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanel
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.AuraSpacing
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.theme.BrandAccent
import java.util.Calendar

private data class Feature(val icon: Int, val title: String, val subtitle: String)

private val PLAYBACK_FEATURES = listOf(
    Feature(R.drawable.play, "Reproducción sin cortes y transiciones", "Reproducción gapless y transición suave entre canciones, activada de fábrica: 5 segundos con la curva Ascenso, en la que las dos canciones suenan juntas y la que sale SIEMPRE baja de verdad (cada una con su propio reloj, sin hueco). Puedes elegir entre 9 curvas y de 1 a 15 segundos en Ajustes ▸ Reproductor. La app memoriza dónde acaba la música real de cada canción, así el fundido cubre música y nunca silencio; y al cambiar de canción a mano la entrada es suave (estilo AIMP), desactivable. Límite honesto: la transición y la calidad Sin pérdida de Qobuz son incompatibles — si eliges esa calidad, la app apaga la transición y te lo avisa"),
    Feature(R.drawable.volume_up, "Volumen Seguro", "Activado por defecto. Nivela las canciones muy altas a un volumen parejo, con un ajuste progresivo para que no se noten tirones, y las protege con un limitador de picos: ninguna pista salta de golpe. Puedes apagarlo en Ajustes ▸ Sonido. Es la única nivelación de volumen que hay en la app; no corre ninguna otra por debajo"),
    Feature(R.drawable.tune, "Calidad de audio y ruta de la señal", "La cadena interna trabaja en coma flotante de 32 bits en equipos de gama media y alta (en gama baja se usa entero, para no penalizar la fluidez). Si no activas nada —ni ecualizador ni Volumen Seguro— la señal pasa intacta; ten en cuenta que de fábrica el Volumen Seguro viene encendido, así que sí hay procesado salvo que lo apagues. La calidad sin pérdida se resuelve cuando está disponible y, si no lo consigue, cae a JioSaavn en 320 kbps avisándote en pantalla"),
    Feature(R.drawable.graphic_eq, "Qobuz hi-res con tu propia cuenta", "Vincula tu cuenta de Qobuz en Ajustes ▸ Cuentas ▸ Qobuz (pegando tu token o con correo y contraseña) y las canciones sin pérdida se resuelven contra TU suscripción, en FLAC hi-res. Negocia sola la mejor calidad disponible (24/192 → 24/96 → FLAC 16/44) y muestra la que realmente llegó. Requiere plan Studio o Sublime para 24 bits: con una cuenta gratuita Qobuz solo entrega fragmentos de 30 segundos, y la app los descarta en vez de hacerlos pasar por la canción. Tus credenciales se guardan cifradas en el teléfono y solo viajan a qobuz.com. Si no vinculas cuenta, nada cambia"),
    Feature(R.drawable.refresh, "Recargar en Opus", "Desde el menú del reproductor, vuelve a cargar el audio de la canción actual en Opus si un stream viene con fallos o quieres refrescarlo, conservando el punto donde ibas. No aplica a archivos locales ni mientras emites por Cast"),
    Feature(R.drawable.offline, "Modo ahorro de datos", "Un solo interruptor en Ajustes ▸ Reproductor que fuerza el audio en Opus y desactiva letras automáticas, videos, precarga, fondos animados y scrobbling mientras está activo, para gastar el mínimo de datos. Apagado por defecto: tus ajustes de calidad quedan guardados y vuelven intactos al desactivarlo"),
    Feature(R.drawable.equalizer, "Tempo, tono y temporizador", "Ajusta la velocidad y el tono de forma independiente desde el menú del reproductor, y programa el apagado por minutos o para que se detenga al terminar la canción actual"),
    Feature(R.drawable.skip_next, "Saltar partes sin música (SponsorBlock)", "Activado por defecto: la app salta sola patrocinios, autopromo, interacciones y tramos no musicales usando la base comunitaria SponsorBlock. Solo se envía el identificador del video, nunca tus datos. Nunca corta el audio real de la canción: las categorías de intro y outro están deliberadamente excluidas. Se apaga en Ajustes ▸ Reproductor"),
    Feature(R.drawable.videocam, "Video musical", "Reproduce el videoclip con su sonido cuando está disponible y sigue en video al cambiar de canción. Pantalla completa al girar el teléfono, Picture-in-Picture (ventana flotante, desde Android 8) y cambio rápido entre audio y video. Como el video usa el mismo motor que el audio, la música sigue sonando en segundo plano y con la pantalla apagada. La conexión se pre-calienta solo cuando conviene: wifi, equipo de gama media o alta, y sin Modo Rendimiento ni ahorro de datos"),
    Feature(R.drawable.image, "Modo Ambiente", "Vista a pantalla completa en horizontal: la portada en grande con un resplandor animado de fondo, la letra y la pantalla siempre encendida. Se abre desde el menú del reproductor, y dentro puedes deslizar a los lados para cambiar de canción, arriba y abajo para el volumen, y tocar dos veces la portada para pausar"),
    Feature(R.drawable.queue_music, "Reproductor y cola a pantalla completa", "Me gusta / No me gusta en una sola píldora dividida abajo en los controles: ambos pulgares empiezan sin relleno (contorno) y solo se rellena el que eliges; el 'No me gusta' es conmutable. Desliza hacia arriba y la cola se abre con pestañas SIGUIENTE / LETRA / RELACIONADOS: tu cola con arrastrar-y-soltar, la letra sincronizada en vivo y los relacionados que alimentan la reproducción automática — sin salir del reproductor. Al final de la cola, reproducción automática con interruptor y chips deslizables para dirigir lo que sigue (relacionado, artistas, mixes), y gestión de 'a continuación'"),
    Feature(R.drawable.bluetooth, "Reproducción inteligente", "Puede pausar al silenciar y reanudar al reconectar Bluetooth (actívalo en Ajustes ▸ Reproductor); notificación multimedia enriquecida con carátula y controles"),
    Feature(R.drawable.speed, "Rendimiento adaptable y Modo Rendimiento (ULTRA)", "Detecta la gama del dispositivo por sus características reales (RAM, núcleos), no por la marca, y ajusta la calidad de los efectos. El Modo Rendimiento (ULTRA) es un interruptor maestro que fuerza el modo más ligero posible —ideal en gama baja, Android TV o auto— apagando lo más pesado (fondos animados, video del artista, desenfoques) sin tocar la fidelidad del audio. Además vigila la temperatura del equipo (desde Android 10) y relaja los efectos visuales si se calienta: el freno térmico nunca toca el sonido. Los fondos animados se pausan con la app en segundo plano o la pantalla apagada, para no calentar ni gastar batería"),
)

private val SOUND_FEATURES = listOf(
    Feature(R.drawable.graphic_eq, "Ecualizador de 10 bandas", "Ecualizador gráfico de 10 bandas ISO (de 31,5 Hz a 16 kHz, ±18 dB) con bypass real: la banda que dejas en 0 dB no crea ningún filtro, así que literalmente no toca la señal. Lo que dejes en pantalla se guarda al salir aunque no crees un preset"),
    Feature(R.drawable.tune, "Modo paramétrico (PEQ) interactivo", "Cambia a paramétrico y da forma al sonido arrastrando puntos sobre la curva de respuesta: de 5 a 8 bandas totalmente tuyas, con frecuencia, Q y ganancia exactas para quien quiera hilar fino"),
    Feature(R.drawable.discover_tune, "Auto-EQ por modelo de auricular", "Catálogo con más de 8.000 perfiles de corrección medidos (AutoEq) que abre al instante: elige tu modelo y la corrección se aplica en cascada con tu ecualización manual — primero corrige el auricular, después va tu gusto, sin que uno pise al otro"),
    Feature(R.drawable.volume_up, "Limitador con margen automático", "Un limitador evita la distorsión cuando subes bandas, y el preamplificador se recorta solo lo justo para dejarle margen: subes ganancia sin que el resultado sature ni se apague de golpe"),
)

private val DISCOVERY_FEATURES = listOf(
    Feature(R.drawable.discover_tune, "Recomendación calculada en tu teléfono", "Un motor de afinidad aprende de lo que escuchas hace poco, de lo que saltas, de la hora del día, de tu biblioteca importada y de los géneros que elegiste al empezar, y con eso ordena Inicio, la reproducción automática, la radio y el aleatorio. Tu perfil de gustos se calcula y se guarda en tu teléfono: no se sube a ningún servidor. Para saber el género de un artista que no conoce sí consulta el catálogo público de iTunes (solo el nombre del artista, y solo por wifi salvo que lo permitas en datos)"),
    Feature(R.drawable.shuffle, "Aleatorio Mejorado", "Aleatorio que no repite: recorre toda la lista antes de volver a ninguna canción, y esa memoria es persistente por lista — si vuelves a una playlist, a un álbum, a un artista o a toda tu biblioteca días después, recuerda cuáles ya oíste. Cada vez que tocas el botón vuelve a revolver de verdad lo que aún no ha sonado. Separa a los artistas: el mismo no vuelve hasta pasadas al menos dos canciones (salvo tramos donde solo hay canciones suyas), y el azar manda sobre tus gustos en una proporción de 4 a 1 para que se sienta aleatorio. Al agotar la lista, la música sigue sola con la radio infinita. Se apaga en Ajustes ▸ Reproductor ▸ 'Aleatorio mejorado'. Límite honesto: las playlists de YouTube Music que no has guardado no tienen memoria entre sesiones, solo dentro de la misma"),
    Feature(R.drawable.shuffle, "¿Continuar o empezar de cero?", "Cuando tocas aleatorio en una lista que ya tiene canciones escuchadas, la app te pregunta si quieres continuar sin repetirlas o empezar de nuevo, y te dice cuántas llevas de cuántas. Si la lista no tiene memoria, no pregunta nada y suena al instante. En Biblioteca ▸ Canciones, en las listas automáticas y en tus playlists además ves el contador y las canciones ya reproducidas marcadas"),
    Feature(R.drawable.queue_music, "Cola infinita y radio", "Cuando se acaba lo que pusiste, la música sigue sola sin repetir lo que acabas de oír. La continuación se siembra con varios artistas de lo que estabas escuchando (no solo el último), analiza los géneros del álbum o playlist que terminó para seguir en ese estilo, y reserva alrededor de una de cada cinco canciones para artistas que aún no conoces. Con 'menos de esto' castigas lo que no quieres: baja bastantes puestos en la cola, aunque no desaparece del todo — es un sesgo deliberado, porque las etiquetas de género no son fiables y un descarte total se llevaba por delante a artistas correctos"),
    Feature(R.drawable.auto_awesome, "Listas con IA", "Crea playlists describiéndolas con una frase: le pides el número de canciones, el género, el idioma y el ánimo, y genera de más para poder descartar las que no se resuelven (puede quedarse algo corta si muchas no existen en el catálogo). Usa varios proveedores de IA gratuitos y, si ninguno responde, completa la lista con búsqueda y radio — nunca se queda en 'IA ocupada'"),
    Feature(R.drawable.auto_awesome, "Recomendado para ti (IA)", "Una playlist fija en Inicio con 20 canciones nuevas según tu historial, refrescada una vez al día en segundo plano y con la hora de la última actualización a la vista; si la IA no responde se conserva la última versión buena en vez de vaciarla. Opcional y apagada por defecto: actívala en Ajustes ▸ IA"),
    Feature(R.drawable.music_history, "Radar de novedades", "Estrenos de los artistas que sigues, renovados cada viernes: solo la tanda de la semana, las anteriores desaparecen. Si la red falla conserva la tanda anterior en vez de dejarte la pantalla vacía. Para fechas de lanzamiento exactas usa tu sesión de Spotify; sin ella, YouTube solo da el año, así que la selección es aproximada"),
    Feature(R.drawable.library_music, "Discografías que se completan solas", "La app resuelve el canal real del artista y completa su catálogo cruzando el catálogo de Apple/iTunes en varias tiendas con listas de la comunidad, y cuando recupera un álbum que faltaba se trae el disco entero, no solo las pistas sueltas que aparecían. Si en la primera visita algo queda incompleto, lo marca y lo repara después en segundo plano. 'Aparece en' muestra las colaboraciones (hasta 40, para no saturar la búsqueda), 'Canciones más escuchadas' carga a la primera y 'Videos oficiales' es reproducible"),
    Feature(R.drawable.home_outlined, "Tu inicio a tu gusto", "Elige tus artistas y géneros al empezar —y esa elección sí alimenta las recomendaciones— y el inicio se llena con tus artistas, lo que escuchas y tus favoritos. Secciones en orden fijo (el orden aleatorio es opcional en Ajustes), con 'Reproducido recientemente' cronológico en un sitio fijo que ni el aleatorio ni el Modo Rendimiento pueden mover, 'Nuevos lanzamientos', hasta tres 'Mix diario' y un mix según la hora del día. Al tocar un chip de estado de ánimo el contenido se actualiza al instante y la reproducción se sesga a ese ánimo mientras el chip esté activo"),
)

private val LYRICS_FEATURES = listOf(
    Feature(R.drawable.lyrics, "Letras sincronizadas", "Letras en tiempo real buscadas en nueve fuentes distintas, que se prueban en orden hasta encontrar una buena. El resaltado palabra por palabra se usa SOLO en las canciones que traen ese dato; en las demás (que son la mayoría, con tiempos por línea) se ilumina la línea completa a tiempo, en vez de inventarse un ritmo por palabra que acababa desincronizado"),
    Feature(R.drawable.palette, "Estilos y lectura", "Diez estilos de presentación, entre ellos Apple y Metro, con desenfoque estilo Apple Music (opcional, apagado de fábrica y desactivado solo si el equipo se calienta o está en Modo Rendimiento) y ajuste de desfase que se guarda por canción, para cuadrarla a tu gusto una sola vez"),
    Feature(R.drawable.tune, "Traducción y romanización", "Traducción de la letra sin que tengas que poner ninguna clave, y romanización en 12 idiomas: japonés, coreano, chino, hindi, panyabí, ruso, ucraniano, serbio, búlgaro, bielorruso, kirguís y macedonio"),
)

private val LIBRARY_FEATURES = listOf(
    Feature(R.drawable.library_music, "Biblioteca y sincronización", "Sincroniza tu contenido de YouTube Music desde Ajustes ▸ Importar: me gusta, álbumes, artistas, suscripciones, playlists, subidas y biblioteca — manual o automática (diaria o semanal), con la hora de la última sincronización a la vista. Esa hora solo se marca cuando la sincronización termina de verdad, así que si ves una fecha vieja es porque algo falló, no porque se olvidara"),
    Feature(R.drawable.account, "Cuentas", "Un solo apartado reúne tus servicios conectados —YouTube Music, Spotify, Last.fm, ListenBrainz y Qobuz— para ver el estado, iniciar sesión o desconectar cada uno desde el mismo sitio; al tocar YouTube Music o Spotify vas directo a su sección de importación, y Last.fm/ListenBrainz abren su configuración de Scrobbling"),
    Feature(R.drawable.search, "Buscadores en Biblioteca", "Un buscador dentro de tus artistas seguidos, otro dentro de tus canciones, otro entre tus playlists y otro dentro de cada playlist, para encontrar al instante lo que ya tienes guardado"),
    Feature(R.drawable.add, "Agregar música a tus playlists", "Al final de tus playlists editables aparece 'Canciones sugeridas' según el contenido de la lista (con vista previa sin salir y botón + para agregar al instante) y 'Artistas destacados'. El botón 'Agregar música' abre una ventana con búsqueda global en todo el catálogo, Desde Replay, Agregado recientemente y selección múltiple de tu biblioteca para meter varias de una vez. Al agregar a playlist, las listas que usaste hace poco aparecen primero"),
    Feature(R.drawable.sync, "Sincronizar una playlist a mano", "En cada playlist vinculada a tu cuenta de YouTube tienes 'Sincronizar ahora' para actualizarla cuando quieras, sin esperar a la sincronización programada. Solo aparece en listas vinculadas y con la sesión iniciada"),
    Feature(R.drawable.download, "Modo sin conexión y descargas", "Descarga canciones, álbumes y playlists para escucharlas sin datos, con descarga por bloques que esquiva la limitación de velocidad (aplica a las descargas desde YouTube, no a Qobuz ni Saavn). El interruptor de la barra superior cambia entre online y offline con un toque y te avisa del estado, y todo lo descargado se reúne en su propia lista automática"),
    Feature(R.drawable.queue_music, "Podcasts", "Motor propio basado en el directorio de Apple/iTunes y en RSS, sin depender de YouTube: buscador, tendencias, progreso guardado para continuar donde lo dejaste, shows fijados, búsqueda universal y reproducción por URL directa. En los episodios que publican video en su propio feed puedes elegir entre audio y video"),
    Feature(R.drawable.folder_managed, "Medios locales", "Reproduce los archivos de música guardados en el dispositivo, con escáner propio, duración mínima configurable, carpetas excluidas y lectura de la carátula incrustada. Las canciones locales nunca se envían a Last.fm ni a ListenBrainz"),
    Feature(R.drawable.music_history, "Historial, estadísticas y resumen", "Tu historial de escucha y estadísticas detalladas, con el tiempo total escuchado (del periodo y de siempre) y cuántas canciones, artistas y álbumes distintos"),
    Feature(R.drawable.sync, "Scrobbling (Last.fm y ListenBrainz)", "Registra lo que escuchas en Last.fm y ListenBrainz, con detección de fallos y reintentos para no perder escuchas. Totalmente opcional y apagado por defecto: no se envía absolutamente nada hasta que conectas tu cuenta en Ajustes ▸ Scrobbling"),
    Feature(R.drawable.backup, "Copia de seguridad local", "Exporta e importa tu biblioteca en un archivo local, cuando quieras y sin depender de la nube. También puedes hacerlo selectivo: elige solo las playlists, todos los artistas y/o todos los presets del ecualizador, y se importan de forma aditiva, sin borrar nada de lo que ya tienes"),
)

private val IMPORT_FEATURES = listOf(
    Feature(R.drawable.download, "Migrar desde otros servicios", "Trae tus playlists de otras plataformas a tu cuenta de YouTube Music, desde Ajustes ▸ Importar ▸ Migrar playlist. Cada canción se busca y se puntúa por duración, artista, título, álbum y versión: si la coincidencia no está clara no la mete a escondidas, la manda a 'revisar' y decides tú, y tu corrección se guarda como definitiva. Las etiquetas Remix, Live o Acústico penalizan fuerte para no traerte la grabación equivocada. Requisito real: la playlist se crea en tu cuenta de YouTube Music, así que necesitas la sesión iniciada — la pantalla te lo pide antes de dejarte elegir nada"),
    Feature(R.drawable.folder_managed, "Desde un archivo (CSV, M3U, JSPF)", "Exporta tu lista desde cualquier servicio (TuneMyMusic, Soundiiz, el volcado de datos de Spotify o Apple, o un reproductor local) e impórtala tal cual. No hace falta ninguna cuenta del servicio de origen"),
    Feature(R.drawable.library_music, "Deezer y Tidal (biblioteca completa)", "De Deezer: pega el enlace de una playlist pública, o el de tu PERFIL para traer de golpe tus canciones favoritas, álbumes guardados, artistas seguidos y playlists. Límite real y sin rodeos: tu perfil y tus listas tienen que ser PÚBLICOS, porque Deezer cerró el registro de aplicaciones nuevas y no existe forma de iniciar sesión. De Tidal (en beta): inicias sesión con tu cuenta y traes toda tu colección, incluidas las listas privadas, o pegas el enlace de una — pero siempre con sesión de Tidal iniciada"),
    Feature(R.drawable.share, "Apple Music", "Aquí no hay importación dentro de la app y no queremos fingir que la hay: Apple no permite que otras apps entren en tu biblioteca. Lo que Aura hace es guiarte paso a paso por la transferencia oficial de Apple y abrirte la página correcta, que es la vía que de verdad funciona"),
    Feature(R.drawable.account, "Importar de Spotify", "Trae tus playlists, tus me gusta y tus álbumes guardados, y vuelve a seguir a tus artistas. También puedes pegar el enlace de cualquier lista pública de Spotify —aunque no sea tuya y sin iniciar sesión— para importarla. Lo importado se queda en tu biblioteca de Aura: aquí no hace falta cuenta de YouTube Music"),
    Feature(R.drawable.sync, "Sincronización programada", "Mantén al día YouTube Music (todo) y las listas de Spotify que elijas, con la frecuencia que prefieras: diaria o semanal"),
)

private val SOCIAL_FEATURES = listOf(
    Feature(R.drawable.group_outlined, "Escuchar juntos", "Escucha sincronizada en tiempo real con tus amigos, en salas con chat integrado: el anfitrión manda la reproducción y todos van al mismo punto, con reconexión automática si se corta. Cómo funciona de verdad: quien invita controla la música y los invitados escuchan (no pueden pausar ni saltar), y si creas una sala sin música sonando y te vas a otra app puedes quedarte desconectado"),
)

private val PLATFORM_FEATURES = listOf(
    Feature(R.drawable.play, "Android Auto", "Aura aparece en la pantalla del coche con tu biblioteca navegable —me gusta, descargadas, playlists, álbumes y artistas—, búsqueda por voz y un botón de aleatorio que enciende el sistema anti-repetición completo, no un simple desorden. Lo que reproduces en el coche se apunta en la lista correcta y su continuación se siembra con lo que suena ahí, no con lo último que abriste en el móvil. El coche no ofrece la tarjeta de 'reanudar' antes de abrir la app: eso es a propósito, para no arrancar música que no pediste"),
    Feature(R.drawable.grid_view, "Android TV y pantallas grandes", "Se instala y se navega con el control del televisor, con anillo de foco para el mando en las listas. En tablets, TVs y plegables desplegados la app cambia a una barra de navegación lateral y a un panel fijo de 'sonando ahora' que aprovecha el ancho extra; en TV la calidad del video se adapta sola al ancho de banda. En plegables, al desplegar, el ecualizador se ensancha para que la curva se vea mejor"),
    Feature(R.drawable.cast, "Google Cast", "Envía el audio a dispositivos Chromecast, con control de volumen que también refleja los cambios hechos en el altavoz o con el mando de la tele. Solo audio (el video no se emite) y solo en la versión con servicios de Google"),
    Feature(R.drawable.share, "Widgets y accesos rápidos", "Cuatro widgets para tu pantalla de inicio —reproductor, tocadiscos de vinilo, playlists y reconocer canción— más un mosaico de Ajustes Rápidos para reconocer sin abrir la app. Los widgets de reproductor y tocadiscos se refrescan mientras la música está activa; si la app lleva tiempo cerrada muestran su aspecto por defecto hasta que la abres. Compartir genera enlaces de YouTube Music desde el reproductor y desde cualquier menú de canción, álbum, artista o playlist"),
    Feature(R.drawable.ic_ringtone, "Establecer como tono", "Usa cualquier canción como tono del dispositivo, con recorte incluido y descarga fiable por rangos y con reanudación: aprovecha la copia ya descargada si existe y solo baja lo que el recorte necesita"),
)

private val RECOGNITION_FEATURES = listOf(
    Feature(R.drawable.mic, "Reconocer canción", "Identifica la música que suena a tu alrededor con un botón dedicado siempre visible: tócalo y empieza a escuchar de inmediato, también desde el widget o desde Ajustes Rápidos. Escucha unos diez segundos y reintenta una vez si no hay suerte. 'Reproducir con Aura' reproduce exactamente el resultado que ves, y desde ahí mismo puedes darle me gusta o agregarlo a una playlist. Si no encuentra una coincidencia fiable de título Y artista, te lo dice y no reproduce nada: preferimos quedarnos callados a ponerte otra canción"),
    Feature(R.drawable.ic_search_mic, "Búsqueda por voz y filtros", "Busca hablando, también en Android TV: cuando el televisor no tiene la ventana de voz del sistema, la app usa su propio reconocimiento con su propio diálogo. Explora Estados de ánimo y Géneros, y filtra los resultados por canciones, álbumes, artistas, playlists, podcasts o videos. Si pegas un enlace de YouTube en el buscador, suena al instante sin pasar por la lista de resultados"),
)

private val APPEARANCE_FEATURES = listOf(
    Feature(R.drawable.palette, "Temas y color", "El color de la app se toma de la carátula de lo que estás escuchando (activado de fábrica), o eliges tú un acento fijo; si no tocas nada y tu Android lo soporta, se usa el color del sistema (Material You). Modo oscuro puro AMOLED para pantallas negras de verdad, e intensidad del acento configurable"),
    Feature(R.drawable.image, "Fondos animados (Canvas)", "Videos cortos del artista y del álbum de fondo mientras suena la música, a pantalla completa al girar el teléfono. Se pausan cuando sales de la app o apagas la pantalla, y se desactivan si el equipo se calienta o estás en Modo Rendimiento, para no gastar batería a cambio de nada"),
    Feature(R.drawable.tune, "Liquid Glass (Beta)", "Estilo de cristal con desenfoque real para el mini-reproductor, la barra de navegación y el botón flotante. Se enciende solo en equipos de gama alta capaces (Android 12 o superior, y nunca en TV, coche o Modo Rendimiento); en el resto puedes activarlo a mano en Ajustes ▸ Apariencia. El reproductor a pantalla completa todavía NO tiene cristal: su interruptor aparece desactivado a propósito, en vez de dejarte encender algo que no se ve"),
    Feature(R.drawable.image, "Transición de carátula (Apple Music)", "Al pasar de una canción a otra la carátula hace un zoom y fundido dinámico estilo Apple Music. Se desactiva sola en Modo Rendimiento"),
    Feature(R.drawable.tune, "Opciones de pantalla", "Escala de densidad, alta tasa de refresco (la app pide de verdad el modo más rápido de tu panel, y vuelve a 60 Hz al activar Modo Rendimiento), ocultar miniatura, videos o Shorts, y recortar carátula"),
    Feature(R.drawable.manage_search, "Búsqueda de ajustes", "Un buscador en Ajustes encuentra las opciones por su nombre y te lleva a la sección donde viven, sin recorrer menús"),
)

private val TRUST_FEATURES = listOf(
    Feature(R.drawable.info, "Diagnóstico y estabilidad", "Si la app llegara a fallar, genera un reporte que se explica solo: el mensaje, la cadena de causas y las últimas 25 líneas del registro de reproducción (solo identificadores y tiempos, nunca títulos ni artistas). Desde Android 11 también registra los cierres provocados por el sistema —batería, memoria, ANR— para poder explicar esos cierres 'sin motivo'. Todo se consulta desde Ajustes y no sale del teléfono salvo que tú lo envíes"),
    Feature(R.drawable.refresh, "Descifrado de reproducción que se repara solo", "Cuando YouTube cambia su reproductor y la música deja de sonar, Aura puede recibir la corrección sin que actualices la app. En Ajustes ▸ Reproductor ▸ Avanzado ves la última comprobación y cuántas configuraciones conoce, y puedes forzar la actualización a mano"),
    Feature(R.drawable.notification, "Fiabilidad en segundo plano", "En marcas conocidas por cerrar apps en segundo plano, Aura ofrece una sola vez desactivar la optimización de batería para que la música no se corte, y hace un reinicio limpio tras iniciar sesión con Google. Es una propuesta puntual, no un aviso que reaparece"),
    Feature(R.drawable.download, "Actualizaciones", "Comprueba si hay versión nueva cada 6 horas y se actualiza sin desinstalar; el último paso siempre lo confirmas tú en el instalador de Android, porque ninguna app puede instalarse sola"),
    Feature(R.drawable.auto_awesome, "Suscripción y demo", "Prueba gratis de 3 días y después suscripción mensual, que se contrata fuera de la app y se activa pegando tu clave. Funciona en un equipo a la vez y tolera unos días sin conexión antes de pedirte que vuelvas a validar"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
) {
    // ONE flag read for the whole screen — 13 panels and ~70 rows all take it as a parameter, so the
    // screen holds exactly one DataStore subscription instead of one per card.
    val skin = rememberAuraPanelSkin()
    // The redesign's ground. Only on a dark theme: on a light one `darkGround` is false and the page
    // keeps `surface`, exactly as today.
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.surface
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = ground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onBack?.invoke() ?: navController.navigateUp() },
                        onLongClick = null,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = ground,
                    scrolledContainerColor = if (skin.enabled && skin.darkGround)
                        AuraPalette.GroundRaised
                    else
                        MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AboutAppCard(skin) }

            item {
                AboutSectionCard(title = "Reproducción y calidad de audio", skin = skin) {
                    FeatureList(PLAYBACK_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Ecualizador y sonido", skin = skin) {
                    FeatureList(SOUND_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Descubrimiento y cola inteligente", skin = skin) {
                    FeatureList(DISCOVERY_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Letras", skin = skin) {
                    FeatureList(LYRICS_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Biblioteca y listas", skin = skin) {
                    FeatureList(LIBRARY_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Importar y migrar", skin = skin) {
                    FeatureList(IMPORT_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Escuchar juntos", skin = skin) {
                    FeatureList(SOCIAL_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "En el coche, en la tele y en tu pantalla de inicio", skin = skin) {
                    FeatureList(PLATFORM_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Reconocer y buscar", skin = skin) {
                    FeatureList(RECOGNITION_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Apariencia y personalización", skin = skin) {
                    FeatureList(APPEARANCE_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Privacidad, control y estabilidad", skin = skin) {
                    FeatureList(TRUST_FEATURES, skin)
                }
            }
            item {
                AboutSectionCard(title = "Información legal", skin = skin) {
                    LegalTermsRow(skin = skin, onClick = { navController.navigate("settings/terms") })
                    AboutDivider(skin)
                    SuperpoweredAttributionRow(skin)
                }
            }
        }
    }
}

/**
 * Ajustes ▸ Acerca de ▸ "Términos y condiciones": opens the read-only Terms screen (the same full
 * text the user accepted on first launch — clause 17.3 promises it is always available here).
 */
@Composable
private fun LegalTermsRow(skin: AuraPanelSkin, onClick: () -> Unit) {
    // The only clickable row on this screen. It reuses [AboutFeatureRow]'s body verbatim rather than
    // keeping a near-copy of it, so the redesign lands on both at once.
    AboutFeatureRow(
        icon = painterResource(R.drawable.info),
        title = stringResource(R.string.terms_about_entry),
        subtitle = stringResource(R.string.terms_about_entry_desc),
        skin = skin,
        onClick = onClick,
    )
}

/**
 * Ajustes ▸ Acerca de ▸ "Información legal": the attribution the SUPERPOWERED SDKS MASTER LICENSE
 * AGREEMENT (Oct 16 2019) requires.
 *
 * Section 5.2(b) obliges us to place this exact notice "in the credits for any Software Application",
 * with the year kept current — hence [Calendar.YEAR] rather than a hardcoded year that would silently
 * go stale. The notice text itself is deliberately NOT translated to Spanish: the agreement dictates
 * it verbatim, so it is a legal string, not user-facing copy. The Spanish label above it explains what
 * the reader is looking at. The full agreement ships in the repo at docs/licenses/.
 */
@Composable
private fun SuperpoweredAttributionRow(skin: AuraPanelSkin) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    AboutFeatureRow(
        icon = painterResource(R.drawable.graphic_eq),
        title = "Motor de audio Superpowered",
        subtitle = "El ecualizador y el Volumen Seguro usan el SDK de audio de Superpowered, " +
            "bajo licencia y con esta atribución obligatoria:\n\n" +
            "Aura Hi-Res Player uses Superpowered SDKs. Superpowered.com\n" +
            "Copyright 2013 – $currentYear, Superpowered, Inc. All rights reserved.",
        skin = skin,
    )
}

@Composable
private fun ColumnScope.FeatureList(features: List<Feature>, skin: AuraPanelSkin) {
    features.forEachIndexed { index, feature ->
        AboutFeatureRow(
            icon = painterResource(feature.icon),
            title = feature.title,
            subtitle = feature.subtitle,
            skin = skin,
        )
        if (index != features.lastIndex) AboutDivider(skin)
    }
}

@Composable
private fun AboutAppCard(skin: AuraPanelSkin) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(28.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Light,
                        )
                    ) {
                        append("AURA ")
                    }
                    withStyle(
                        SpanStyle(
                            // The wordmark's accent half. On the new skin it takes the render's teal
                            // (or the theme's primary on a light ground) so the mark matches the rest
                            // of the redesign; off it, `BrandAccent`, unchanged.
                            color = if (skin.enabled) skin.accent else BrandAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    ) {
                        append("HI-RES")
                    }
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 26.sp,
                    letterSpacing = 6.sp,
                ),
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The version / build chips are TECHNICAL DATA, which the render sets in tracked
                // monospace — the same treatment "FLAC / 24 BIT / 96 kHz" gets in the player.
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (skin.enabled) skin.accent.copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                        color = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (BuildConfig.DEBUG) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = "DEBUG",
                            style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (skin.enabled) skin.ink.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = BuildConfig.ARCHITECTURE.uppercase(),
                            style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    skin: AuraPanelSkin,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            // The render's `.mh` group header: tracked monospace at low opacity. The STRING is never
            // touched — no `.uppercase()`, no retype — so the Spanish and its accents stay exactly
            // what the caller passes.
            style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleSmall,
            fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
            color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
        AuraPanel(
            skin = skin,
            modifier = Modifier.fillMaxWidth(),
            classicShape = RoundedCornerShape(28.dp),
            classicColors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AboutFeatureRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    // No default: every caller resolves the skin ONCE at the screen root and passes it down. A
    // default here would quietly allocate a fresh classic skin per row.
    skin: AuraPanelSkin,
    onClick: (() -> Unit)? = null,
) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Identity unless a caller passes one ([LegalTermsRow] is the only one that does), and
            // placed exactly where that row's own `clickable` used to sit.
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // The redesign's rhythm is tighter than the classic 14 dp; the 42 dp glyph frame already
            // clears 48 dp with padding, but the minimum is asserted rather than assumed.
            .then(if (skin.enabled) Modifier.sizeIn(minHeight = AuraSpacing.MinTouchTarget) else Modifier)
            .padding(horizontal = 20.dp, vertical = if (skin.enabled) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            // The frame keeps its classic 42 dp in BOTH paths so the text column does not shift the
            // moment the flag flips; only the plate behind the glyph goes, exactly as the render
            // draws a bare accent glyph with no icon plate.
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (skin.enabled) Color.Transparent else tint.copy(alpha = 0.10f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (skin.enabled) skin.accent else tint,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    // `CalloutSubtitle` (13 sp) rather than `RowSubtitle` (14 sp): these subtitles are
                    // long explanatory paragraphs and they are replacing a 12 sp `bodySmall`, so the
                    // nearest Aura size is the one that reflows them least.
                    style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                    color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AboutDivider(skin: AuraPanelSkin) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = if (skin.enabled) skin.hairline
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}
