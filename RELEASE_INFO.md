# Aura Hi-Res Player 0.6.73 — Radio infinita con transición suave (crossfade)

## 📻 Transición fluida en la cola infinita (radio)
Antes, cuando llegabas a la última canción y la radio automática agregaba más canciones, el reproductor hacía un "corte duro" sin crossfade. Ahora, la radio se siembra **temprano** (mientras la última canción todavía está sonando) y el crossfade se **re-arma automáticamente**. Resultado: la música nunca se corta y la mezcla fluye suavemente hacia la nueva canción generada por la radio.

## 🛠️ Mejoras internas
- Se corrigió un posible problema de concurrencia (condición de carrera) al calcular el crossfade de la radio para asegurar que nunca se "siembre dos veces" la cola de reproducción accidentalmente.

## 👉 Para probar
- Reproduce una canción con el interruptor de "Cargar más automáticamente" (radio infinita) encendido, y sin más canciones en la cola.
- Escucha el final de la canción: la nueva canción debe aparecer antes de que termine y debe mezclarse (crossfade) sin interrupciones.
