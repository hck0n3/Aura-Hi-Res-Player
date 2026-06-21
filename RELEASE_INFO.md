# Aura Hi-Res Player v5.7.92

## Arreglo de cierre inesperado (crash) en las recomendaciones
- Corregido el cierre que podía ocurrir al ordenar por gustos (Inicio, autoplay, shuffle).
- Causa: el "toque de variedad" aleatorio se calculaba dentro de la comparación de ordenamiento, lo que volvía la comparación inconsistente y rompía el ordenador del sistema. Ahora se calcula una sola vez por canción antes de ordenar.
