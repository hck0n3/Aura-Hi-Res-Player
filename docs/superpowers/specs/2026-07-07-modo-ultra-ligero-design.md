# Modo Rendimiento (fusionado con Ultra) — Diseño

**Fecha:** 2026-07-07
**Rama:** feat/perf-mode-offline-universal
**Estado:** Aprobado (pendiente revisión de spec por el usuario)

## Problema

En el Modo Alto Rendimiento actual (`HighPerformanceModeKey` → `effectiveTier = LOW`) la app
todavía se congela en dispositivos de gama **ultra-baja** (≈2 GB de RAM, `isLowRamDevice`). Los
tres puntos de jank reportados son: **arranque**, **scroll en Home** y **abrir el reproductor /
carátula**. El nivel LOW no recorta suficiente para ese hardware.

## Decisión: un solo modo

Se **fusionan** el Modo Rendimiento y el "ultra" en **un único modo**. No hay dos switches ni dos
niveles visibles al usuario:

- **Un solo switch** "Modo rendimiento" (el `HighPerformanceModeKey` actual, reutilizado).
- **ON = recorte máximo (ULTRA) para TODOS los dispositivos**, sin importar su hardware. ULTRA ⊃ LOW
  (incluye todo lo que hacía el perf actual, y más).
- **OFF** = comportamiento normal según el tier real del dispositivo. Los dispositivos capaces
  siguen funcionando igual.

Esto redefine el **significado** del flag existente (ON pasa de LOW a ULTRA), no su valor: un usuario
que ya tenía el modo encendido obtiene ULTRA automáticamente; uno que lo tenía apagado sigue apagado.
No hace falta key de migración nueva.

## No-objetivos (YAGNI)

- **No** un segundo switch "ultra" ni una nueva preference key. Un solo flag.
- **No** un APK / product flavor separado. Un solo build.
- **No** tocar la cadena de audio (fidelidad intacta: EQ, Safe Volume, normalización, limitador).
  El problema es render en el main thread, no audio.
- **No** auto-escalar por hardware cuando el modo está ON (ON siempre = ULTRA).

## Enfoque

Extender el enum `DeviceTier` con un nivel **`ULTRA`** por debajo de `LOW`.

- `PerformanceMode.effectiveTier()` devuelve **`ULTRA`** cuando el modo está ON; si está OFF devuelve
  el tier real del hardware. `LOW`/`MID`/`HIGH` mantienen su semántica.
- El tier **real** (`DeviceCapabilities.tier()`) sigue devolviendo solo `LOW`/`MID`/`HIGH`. `ULTRA`
  es un tier **de comportamiento**: solo aparece vía `effectiveTier()` cuando el modo está ON.
- `when(tier)` en Kotlin es exhaustivo: el compilador **obliga** a cubrir `ULTRA` en cada `when`.
  Solo hay 2 `when(tier)` reales (`Player.kt` buffer del canvas, label en `PerformanceSettings.kt`).
- **Seguridad de orden confirmada:** nada compara `DeviceTier` por `.ordinal`; todas las
  comparaciones son `==` o `when()`. Reordenar el enum (ULTRA primero) no rompe nada.

Rechazado — flag booleano suelto sin tocar el enum: más fácil olvidar un gate, sin red de seguridad
del compilador.

## Comportamiento de ULTRA (todo lo del perf actual + estos recortes extra)

### Arranque
- Coil: cache de memoria más pequeña + límite de decodes concurrentes más bajo. (RGB_565 ya lo hacía
  el perf actual.)
- Diferir inicializaciones no críticas al arranque.

### Home (raíz del jank de scroll) — carátulas pequeñas
- Reusar las filas ligeras (`LazyRow`) que el modo ya renderiza (quick picks + daily discover).
- Carátulas **decodificadas a menor tamaño** (thumbnail chico), no las grandes.
- **Tope de carruseles visibles** (2–3 máx).
- Sin paginado infinito / continuación de YouTube, sin `ShimmerHost` de fondo (ya cortados).

### Player / carátula
- Fondo sólido y sin crossfade de carátula: ya lo hace el modo actual.
- ULTRA añade: carátula **estática** decodificada a menor tamaño; layout sin elementos decorativos
  (rotación / canvas / blur ya están gated por `rememberPerfGatedBoolean`).
- El `when(deviceTier)` del buffer del canvas en `Player.kt` cubre `ULTRA` con el valor más bajo.

## Activación (sin cambios respecto al flag actual)

- **Auto en primer inicio** (`App.kt`): el modo se auto-enciende en hardware LOW / Android TV / car,
  igual que hoy. Como ON ahora = ULTRA, esos dispositivos reciben el recorte máximo automáticamente.
- **Usuarios existentes:** el seed/migración actual de `HighPerformanceModeKey` se mantiene tal cual.
  No se necesita key nueva porque cambia el significado del flag, no su valor.
- **Toggle manual** en `PerformanceSettings.kt`: el switch existente. Se actualiza el texto para
  reflejar que ahora es el recorte máximo.
- **Reversible:** apagar restaura los toggles del usuario (patrón `rememberPerfGatedBoolean` — nunca
  muta la preferencia guardada).

## Componentes / archivos

| Archivo | Cambio |
|---|---|
| `utils/DeviceCapabilities.kt` | `enum DeviceTier { ULTRA, LOW, MID, HIGH }`. `compute()` **no** devuelve ULTRA (tier real intacto) |
| `utils/PerformanceMode.kt` | `effectiveTier()` devuelve `ULTRA` cuando `isOn`; doc actualizada |
| `App.kt` | ajuste Coil (cache/decodes) cuando el modo está ON; seed sin cambios |
| `ui/screens/settings/PerformanceSettings.kt` | texto del switch actualizado; `when(tier)` del label cubre ULTRA |
| `ui/screens/HomeScreen.kt` | carátulas chicas + tope de carruseles cuando el modo está ON |
| `ui/player/Player.kt` | `when(deviceTier)` del buffer cubre ULTRA; carátula estática a menor tamaño |

**Sin cambios en:** `constants/PreferenceKeys.kt` (no hay key nueva).

## Interfaces clave

```kotlin
enum class DeviceTier { ULTRA, LOW, MID, HIGH }   // orden: más débil → más fuerte

object PerformanceMode {
    fun isOn(context): Boolean               // el modo rendimiento (único)
    fun effectiveTier(context): DeviceTier   // ULTRA si isOn, si no el tier real
}
// DeviceCapabilities.tier() sigue devolviendo solo LOW/MID/HIGH (nunca ULTRA).
```

## Verificación

- Compila (el `when` exhaustivo garantiza que ningún gate quedó sin cubrir ULTRA).
- Prueba manual con el modo ON en un dispositivo/emulador de ~2 GB: arranque, scroll Home, abrir
  player sin congelarse.
- Modo ON→OFF restaura la Home/reproductor normales (reversibilidad).
- Dispositivo de gama media/alta con modo OFF: sin cambios de comportamiento.

## Riesgos

- ON aplica ULTRA a TODOS, incluidos dispositivos capaces que enciendan el modo a mano → verán una UI
  más recortada. Es esperado: el usuario lo pidió explícitamente (un solo modo, siempre máximo).
- Añadir ULTRA al enum rompe compilación en `when` no exhaustivos → **es la red de seguridad**, se
  arreglan uno a uno.
