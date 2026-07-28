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
| 2 | 2026-07-27 | 0.6.133 (838) | Xiaomi 23090RA98G, Android 16 | `IllegalStateException` (sin mensaje) en `iad1tya.echo.music.utils.s2.n` — main thread, Runnable posteado, caller librería (`p6.c0.X1`) | Cliente "escuchando música normal", sin interacción | Ofuscado; mapping del build 838 NO se guardó (el CI no lo subía — corregido desde 0.6.134). Cacería en curso: superficie ISE de utils/ + inferencia de orden R8 + rutas de Runnables en main durante reproducción (ScrobbleManager/Discord/widgets/ticks) | 🔴 Abierto |

## Protocolo al llegar un crash
1. Registrar fila aquí (fecha, versión, dispositivo, excepción, contexto del usuario).
2. Desofuscar con el artifact `r8-mapping` del run del tag correspondiente (retrace / lookup manual).
3. Verificar contra el código ANTES de arreglar (regla de siempre); el fix referencia la fila.
4. Actualizar estado (✅ versión del fix / 🔴 abierto) y, si dejó lección, fila en REGRESSION_REGISTRY.
