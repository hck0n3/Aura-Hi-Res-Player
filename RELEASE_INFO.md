# Aura Hi-Res Player 0.6.133 — La que sale AHORA SÍ baja su volumen (bug estructural encontrado)

## 🔴 Tu reporte exacto, resuelto de raíz: "la que entra está bien, la que sale no baja"
Encontramos el bug estructural en el corazón del fundido — dos fallas juntas:

1. **El fundido entero se CONGELABA mientras la canción entrante cargaba**: había una espera interna que pausaba TODO el cruce hasta que la nueva empezara a sonar (normal que tarde unos segundos en streaming). Durante esa espera, la saliente quedaba **clavada a volumen alto**; cuando el fundido por fin corría, a la saliente casi no le quedaba vida y moría sin bajar.
2. **La rampa de bajada nunca se ajustaba al tiempo real restante**: si el cruce arrancaba tarde, la bajada programada era más larga que lo que le quedaba a la canción — el archivo terminaba con la rampa apenas empezada.

**El rediseño — cada lado con su propio reloj:**
- La que **sale** baja siguiendo SU reproducción real: su bajada siempre se oye completa y siempre termina antes de que se acabe su audio. Nada la congela.
- La que **entra** sube siguiendo SU audio real: si tarda en cargar, sube cuando de verdad suena — sin golpes y sin frenar a la otra.
- Pausa del usuario: ambas se congelan y reanudan correctamente.

## Recordatorio
Si algo aún no suena perfecto: **Ajustes ▸ Registros** → compartir → mándamelo. Cada transición deja su línea `CROSSFADE_TRACE` con el veredicto.
