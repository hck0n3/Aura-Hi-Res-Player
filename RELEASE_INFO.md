# Aura Hi-Res Player 0.6.44 — Auditoría (parte 8): pulido de rendimiento y robustez

Esta versión aplica **17 hallazgos** más de la auditoría (7 medios + 10 bajos). Sin funciones nuevas; todo son arreglos confirmados.

## Más fluido (sobre todo en gama baja)
- El reproductor recompone **menos** (el contador de posición pasó de 10 a 2 veces por segundo).
- El **widget** se actualiza 1×/s en vez de 5×/s.
- Guardar la cola ya no serializa en el hilo principal (menos tirones); el guardado al cerrar es síncrono para no perder la cola.
- Carrusel de inicio: listas con **key** estable (sin recomposición completa al cargar más / refrescar) y el perfil de gustos se calcula en el dispatcher correcto.
- El fondo difuminado del video usa un radio mucho más barato.
- El slider de volumen ya no lanza una corrutina por cada movimiento.

## Más robusto
- **Inicio no crashea** aunque una canción se borre mientras se ve (se acabaron los `!!`).
- **Reconocimiento** valida el micrófono (equipos baratos / mic ocupado) con mensajes claros, y evita dos sesiones de micrófono a la vez (app + widget).
- Sincronización: una playlist no se **trunca** si YouTube devuelve una página parcial; respuestas de stream incompletas ya no descartan toda la info.
- Letras: al cambiar de canción ya no se escriben letras de la canción anterior.
- `NewPipe` inicializa de forma segura entre hilos.
- Receptores de widget exportados ya no copian extras del broadcast (endurecimiento).

## Pendiente (pasada dedicada de audio)
Quedan 5 hallazgos que tocan **matemática de audio en tiempo real / throttles sensibles** (denormales DSP, soft-clip del EQ, estáticos DSP en crossfade, ritmo de sincronización, tope por datos móviles). Los haré con cuidado y prueba de audio para no arriesgar la calidad.
