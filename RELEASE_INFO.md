# Aura Hi-Res Player 0.6.135 — La app aprende cada canción: transiciones sobre MÚSICA, nunca sobre silencio

## 🎧 Memoria de silencios por canción (estilo Apple AutoMix)
La app ahora **mide cada canción mientras suena** y recuerda dónde termina y dónde empieza su música:
- **Final**: aprende cuánto silencio tiene la cola (medición exacta al fin del archivo decodificado). Desde la siguiente reproducción, la bajada de volumen cubre **los últimos 5 segundos de MÚSICA real** y termina justo donde termina la música — el silencio sobrante **nunca se reproduce**.
- **Inicio**: aprende cuánto silencio tiene la intro. La próxima vez que esa canción entre en una transición, arranca **directo en su música** — la subida gradual se escucha sobre audio real, no sobre aire muerto.
- Primera escucha = detección en vivo (la de 0.6.133); desde la segunda = anclaje perfecto. Solo mejora, nunca empeora.

## 🖐️ Cambio manual aún más suave
La entrada al cambiar de canción manualmente ahora dura **1.6s** (antes 1.1s) — el suavizado se nota, sin perder naturalidad.

## 🔧 Además
- Si la radia infinita añadía canciones en los últimos segundos, la transición podía llegar "tarde" y caer en corte duro — ahora dispara el cruce al instante.
- Blindajes del sistema de aprendizaje (4 defectos cazados por auditoría adversarial ANTES de publicar): medición congelada, disparo en el segundo 0 en pistas cortas, intros envenenadas por seeks, y borrado de datos en pistas hi-res. Todo cubierto con 9 tests nuevos (165/165 en verde).

## Recordatorio
- Si un cruce no suena perfecto: **Ajustes ▸ Registros** → compartir → mandar. Cada transición trae su veredicto (incluye el nuevo `hint-anchor` cuando usa la memoria aprendida).
