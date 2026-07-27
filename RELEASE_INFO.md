# Aura Hi-Res Player 0.6.132 — Dos causas de "me corta la transición" eliminadas + diagnóstico por transición

## ✂️ Causa 1: la app se RENDÍA si la siguiente canción tardaba en cargar
Cuando llegaba la hora del cruce y la canción entrante aún no estaba lista (pasa seguido en Lossless, que tarda más en resolver), la app esperaba solo 2.5 segundos y luego **cortaba en seco**. Ahora espera **todo el tiempo que le quede a la canción saliente** — un cruce tardío y más corto siempre es mejor que un corte. Se acabó ese corte.

## ✂️ Causa 2: canciones del MISMO álbum saltaban el cruce a propósito
Había un ajuste (activado por defecto) que entre canciones del mismo álbum hacía avance directo "gapless" en vez de cruce — tú lo oías como "me cortó la transición". **Desactivado por defecto con esta actualización**: ahora TODAS las transiciones automáticas llevan su cruce. (El purista del gapless puede reactivarlo en Ajustes ▸ Reproductor.)

## 🔍 Y desde ahora: cada transición deja su veredicto en tu registro
En **Ajustes ▸ Registros** ahora aparece una línea `CROSSFADE_TRACE` por cada transición: si el cruce arrancó (y por cuál detector), si el swap se completó, o **exactamente por qué cortó**. 

**Si vuelves a oír un corte o un cruce que no te gusta:** anota más o menos la hora, ve a Ajustes ▸ Registros, comparte el registro y mándamelo — esa transición exacta tendrá su línea con la causa. Con eso lo arreglo sin adivinar.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — mismo registro, línea RESOLVE_TIMING.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
