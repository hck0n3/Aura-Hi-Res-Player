# Aura Hi-Res Player 0.6.119 — El Aleatorio Mejorado se ve y se corrige

## 🔴 Corregido: el aleatorio saltaba a la canción equivocada
Con la transición suave (crossfade) activada, a veces **oías venir la siguiente canción y de pronto saltaba a otra distinta**. Causa: el fundido cambia de canción por un camino interno que no anotaba la canción como "ya reproducida", así que el aleatorio volvía a barajar como si nada hubiera sonado. Ya se anota siempre — el aleatorio continúa con la canción correcta y no repite. (El fundido de 9s no se tocó: mismo sonido de siempre.)

## 👁️ Ahora VES que el aleatorio mejorado funciona
- **Contador en cada lista:** una píldora "🔀 Aleatorio mejorado · X/Y reproducidas" en la playlist, las listas automáticas (Me gusta, Descargadas…) y la biblioteca. Verla = está activo; el número sube según avanzas el ciclo sin repetir.
- **Canciones ya reproducidas, marcadas:** en la lista se ven **atenuadas y con un ✓** las que ya sonaron en este ciclo del aleatorio. Lo que falta por sonar se ve normal. La que está sonando ahora nunca se atenúa.
- Todo se actualiza **en vivo** mientras suena la música, y desaparece si apagas el aleatorio mejorado en Ajustes.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — necesito tu registro en esa calidad.
- **Volumen Seguro** sube máximo +3 dB.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
