# Aura Hi-Res Player 0.5.2

## 🔊 ARREGLO CRÍTICO: reproducía sin sonido
- Síntoma: la música "sonaba" (avanzaba) pero **no se escuchaba ni en altavoz ni en Bluetooth**.
- Causa: durante un crossfade/atenuación el `player.volume` baja temporalmente, y el estado del reproductor **guardaba ese volumen transitorio**. Si la app se guardaba/cerraba durante un fundido, persistía un volumen ~0 y **toda la reproducción quedaba muda** para siempre.
- Arreglo en 3 capas:
  1. **Repara**: si tu volumen quedó guardado en ~0, al abrir se restablece a volumen completo → tu app deja de estar muda.
  2. **Previene**: el estado ahora guarda **tu volumen real**, nunca el transitorio del fundido.
  3. **Blinda**: un crossfade cancelado (al saltar de tema) **siempre** restaura el volumen (try/finally).

## 🎬 YouTube Music en la introducción — paso propio, sin login forzado
- El primer inicio tiene dos pasos de migración **separados**: **Spotify**, luego **YouTube Music**.
- El paso de YouTube **ya no fuerza el login**: "Sincronizar YouTube Music" abre la pantalla para **elegir qué traer**; si no tienes sesión, la inicias ahí (opcional). También puedes **Omitir**.

## Técnico
- MusicService: persistir `playerVolume.value` (no `player.volume`); reparar volumen persistido <0.05 → 1; crossfade con `try/finally` que restaura el volumen del usuario y limpia.
- OnboardingYouTubeScreen (ruta `onboarding_youtube`) encadenado tras Spotify; navega a `settings/ytm_sync` (sin login automático).
