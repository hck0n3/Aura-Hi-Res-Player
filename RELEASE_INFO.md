# Aura Hi-Res Player 0.1.8

## Potenciador de Graves y Agudos (Tono estilo Poweramp) 🎚️
- Nuevos sliders **Graves** y **Agudos** en el ecualizador: shelves anchos y musicales (graves ~90 Hz, agudos ~10 kHz) que dan cuerpo y brillo "que suena rico", manipulables a mano (±12 dB), separados de las 24 bandas.
- Se integran con el limitador multibanda y el headroom automático: pegan fuerte sin distorsión ni "oleadas".

## Arreglo: dar "me gusta" ya no altera el volumen 🔊
- Dar like dispara la auto-descarga, que re-guardaba el formato de la canción en curso y recalculaba la normalización a mitad de tema → saltaba el volumen. Ahora la normalización se fija una sola vez por pista: el volumen ya no salta al dar me gusta.

## Transición suave: 10 segundos y curva lineal
- La transición entre canciones (crossfade) pasa a **10 s** con curva **lineal** por defecto. Se aplica automáticamente al actualizar.

## Ajuste del nivel del EQ (de 0.1.7)
- Al subir bandas, el ecualizador da headroom automático: ya no queda demasiado alto ni hay que bajar el preamp a mano; sigue limpio y sin oleadas (multibanda).

## Técnico
- AxionEqViewModel/Screen: tono Graves/Agudos como shelves LSC/HSC (Q 0.7) añadidos al perfil; persistidos y con migración de reset.
- MusicService.setupLoudnessEnhancer: guard por mediaId (lastNormalizedId) — no re-aplica la ganancia de normalización para la pista ya normalizada.
- App.kt/PreferenceKeys: PlaybackDefaultsV2 → crossfade 10 s lineal (seed + migración + defaults de Settings/servicio).
