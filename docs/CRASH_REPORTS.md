# Libro de crashes reportados por usuarios

> Regla permanente: TODO crash report que llegue (del dueño o de clientes) se registra aquí con su
> análisis y estado — nunca se pierde uno ni se re-diagnostica de cero. Complementa
> `REGRESSION_REGISTRY.md` (que registra errores/lecciones de desarrollo).
> Herramientas: el CI sube el mapping R8 de cada release (artifact `r8-mapping`) para desofuscar los
> frames; desde 0.6.134 el propio crash report incluye mensaje + cadena de causas + las últimas 25
> líneas del registro de reproducción (CROSSFADE_TRACE / RESOLVE_TIMING) = contexto de qué pasaba.

| # | Fecha | Versión | Dispositivo | Excepción | Contexto | Diagnóstico | Estado |
|---|---|---|---|---|---|---|---|
| 1 | 2026-07-21 | 0.6.117 (822) | Xiaomi 2512BPNDAC, Android 16 | `IllegalStateException: A migration from 38 to 39 was required but not found` | Abrir la app tras actualizar | MIGRATION_38_39 registrada solo en el builder muerto, no en el de Hilt (registry fila 89) | ✅ Hotfix 0.6.118 |
| 2 | 2026-07-27 | 0.6.133 (838) | Xiaomi 23090RA98G, Android 16 | `IllegalStateException` (sin mensaje) en `iad1tya.echo.music.utils.s2.n` — main thread, Runnable posteado, caller `p6.c0.X1` | Cliente "escuchando música normal", sin interacción | **RETRACEADO COMPLETO** (partition map local reprodujo el naming del CI, verificado frame a frame — `mapping.prt` es un ZIP de R8, una entrada por clase; el primer agente que lo "grepeó" como texto FABRICÓ un retrace — verificar siempre): `s2.n` = helper sintético de throw compartido de R8 (el frame superior MIENTE sobre la ubicación); el lanzador real es **media3 `ExoPlayerImpl.evaluateMediaItemTransitionReason:2550`** dentro de `updatePlaybackInfo` — la rama del "estado imposible" (UID actual cambió sin discontinuidad ni timelineChanged): carrera entre el hilo de reproducción de media3 y una MUTACIÓN de playlist de la app en el instante de una transición. Nuestro mayor mutador sobre players muriendo: el teardown del crossfade (`stop+clearMediaItems+release` sobre el saliente justo cuando su contenido TERMINA — y el cap `durOut` del 0.6.133 hace ese solape rutinario). Fix 0.6.134: quitar `clearMediaItems` (redundante antes de `release()`) de los 3 teardowns del crossfade + guard `runCatching` en el resume desprotegido de PoTokenWebView.onConsoleMessage (latente, mismo hunt). Clase de carrera de librería: no 100% demostrable sin repro — monitorear con los reportes auto-diagnosticables | ✅ Resuelto en 0.6.134 — **sin recurrencia**: el dueño confirma (2026-07-28) que no ha vuelto a pasar tras 0.6.134/135/136 |

## Protocolo al llegar un crash
1. Registrar fila aquí (fecha, versión, dispositivo, excepción, contexto del usuario).
2. Desofuscar con el artifact `r8-mapping` del run del tag correspondiente (retrace / lookup manual).
3. Verificar contra el código ANTES de arreglar (regla de siempre); el fix referencia la fila.
4. Actualizar estado (✅ versión del fix / 🔴 abierto) y, si dejó lección, fila en REGRESSION_REGISTRY.
