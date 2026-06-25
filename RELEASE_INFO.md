# Aura Hi-Res Player 0.6.34

## Mosaico de reconocimiento de música arreglado 🎙️
El mosaico de **Ajustes Rápidos** "Reconocer música" no hacía nada (mandaba la señal a un receptor que no estaba registrado). Ahora **abre la app directo a la pantalla de reconocimiento**, que empieza a grabar y a identificar la canción al instante.
- Funciona en todas las versiones de Android (no depende de un servicio de micrófono en segundo plano, que en Android 14+ es poco fiable).
- El reconocimiento dentro de la app (botón de micrófono) seguía funcionando; esto arregla solo el acceso desde el mosaico.

## Técnico
- `RecognitionTileService`: ahora hace `startActivityAndCollapse` hacia `MainActivity` con `ACTION_RECOGNITION` (PendingIntent en API 34+).
- `MainActivity.handleDeepLinkIntent`: maneja `ACTION_RECOGNITION` → navega a la ruta `recognition` (que auto-inicia el reconocimiento).
