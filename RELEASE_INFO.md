# Aura Hi-Res Player 0.6.20

## Reconocer música desde Ajustes Rápidos 🎙️
- Nuevo **tile de Ajustes Rápidos** (el panel que bajas desde arriba): tócalo para **reconocer la canción que suena** sin abrir la app. Reusa el mismo motor (Shazam) del widget.
- Para añadirlo: baja el panel de Ajustes Rápidos → editar/lápiz → arrastra "Aura" (o el ícono de micrófono) a tus tiles activos.

## Técnico
- Nuevo `RecognitionTileService` (`android.service.quicksettings.TileService`) que emite el mismo broadcast `ACTION_START_RECOGNITION` del widget (reusa permisos + foreground service). Registrado en el manifest con `BIND_QUICK_SETTINGS_TILE` + `QS_TILE`.
- Picture-in-Picture queda como siguiente pieza (requiere prueba en dispositivo).
