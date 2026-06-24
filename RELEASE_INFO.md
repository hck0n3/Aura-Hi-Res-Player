# Aura Hi-Res Player 0.6.8 (diagnóstico de video)

## Build de diagnóstico 🔎
El modo video sigue fallando al obtener el stream y, tras varios intentos, en vez de adivinar quiero el **motivo exacto**. Esta versión cambia el toast genérico por uno que muestra **por qué** falló (ej. "Could not find format" = ningún cliente entregó formato de video, error de red, etc.).

**Qué hacer:** activa video en una canción con videoclip y manda el **texto completo del toast** (sale en grande unos segundos). Con eso se arregla el punto exacto en una sola ronda.

No cambia nada del audio ni rompe nada — solo expone la causa.

## Técnico
- `YTPlayerUtils.videoStreamUrlDiag()` devuelve `Result<String>` (no traga la excepción). `toggleVideoMode` muestra `"${ExceptionClass}: ${message}"` en el toast (LENGTH_LONG) al fallar.
