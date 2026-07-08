# Modo Ultra Ligero — Diseño

**Fecha:** 2026-07-07
**Rama:** feat/perf-mode-offline-universal
**Estado:** Aprobado (pendiente revisión de spec por el usuario)

## Problema

En el Modo Alto Rendimiento actual (`HighPerformanceModeKey` → `effectiveTier = LOW`) la app
todavía se congela en dispositivos de gama **ultra-baja** (≈2 GB de RAM, `isLowRamDevice`). Los
tres puntos de jank reportados son: **arranque**, **scroll en Home** y **abrir el reproductor /
carátula**. El nivel LOW no recorta suficiente para ese hardware.

El objetivo: un nivel más agresivo **dentro del mismo APK** que se active automáticamente en gama
ultra-baja y sea toggle manual; los demás dispositivos siguen funcionando igual.

## No-objetivos (YAGNI)

- **No** un APK / product flavor separado. Un solo build.
- **No** tocar la cadena de audio (fidelidad intacta: EQ, Safe Volume, normalización, limitador).
  El problema es render en el main thread, no audio.
- **No** refactor no relacionado del sistema de tiers.

## Enfoque (aprobado: A)

Extender el enum `DeviceTier` con un nivel **`ULTRA`** por debajo de `LOW`.

- `PerformanceMode.effectiveTier()` devuelve `ULTRA` cuando el flag ultra está ON **o** el hardware
  es ultra-bajo detectado; si no, devuelve el tier real. `LOW`/`MID`/`HIGH` no cambian de semántica.
- **Ultra ⊃ Low.** Activar ultra fuerza también el comportamiento del Modo Alto Rendimiento (todos
  los gates `!perfOn` / LOW siguen aplicando; ultra solo añade recortes encima).
- `when(tier)` en Kotlin es exhaustivo: el compilador **obliga** a cubrir `ULTRA` en cada `when`.
  Hay solo 2 `when(tier)` reales (`Player.kt` buffer del canvas, label en `PerformanceSettings.kt`)
  y ~26 referencias a `DeviceTier.*` en 7 archivos, todas revisables.

Rechazado — Enfoque B (flag booleano suelto sin tocar el enum): más fácil olvidar un gate, sin red
de seguridad del compilador.

## Comportamiento de ULTRA (además de lo que ya hace LOW)

### Arranque
- Coil: cache de memoria más pequeña + límite de decodes concurrentes más bajo. (RGB_565 ya lo hace
  LOW.)
- Diferir inicializaciones no críticas al arranque cuando sea ULTRA.

### Home (raíz del jank de scroll) — decisión: **carátulas pequeñas**
- Reusar las filas ligeras (`LazyRow`) que el modo perf ya renderiza (quick picks + daily discover).
- Carátulas **decodificadas a menor tamaño** (thumbnail chico), no las grandes.
- **Tope de carruseles visibles** (2–3 máx) en ULTRA.
- Sin paginado infinito / continuación de YouTube, sin `ShimmerHost` de fondo (perf ya lo corta).

### Player / carátula
- Fondo sólido y sin crossfade de carátula: ya lo hace el modo perf actual.
- ULTRA añade: carátula **estática** decodificada a menor tamaño; layout sin elementos decorativos
  (rotación / canvas / blur ya están gated por `rememberPerfGatedBoolean`).
- El `when(deviceTier)` del buffer del canvas en `Player.kt` cubre `ULTRA` con el valor más bajo.

## Activación

- **Auto en primer inicio** (en `App.kt`, junto al seed de `HighPerformanceModeKey`): activar ultra
  si el hardware es ultra-bajo. Umbral inicial (afinable): `isLowRamDevice && RAM_reportada ≲ 2 GB`.
  Se decide en `DeviceCapabilities`: el tier real puede ser `ULTRA` para ese hardware.
- **Migración usuarios existentes:** una **key nueva** `UltraLiteSeedAppliedKey` (según la memoria
  del proyecto, forzar defaults en una actualización requiere una key fresca; un `versionCode` o una
  key ya puesta no re-ejecuta el seed). Solo setea si `UltraLiteModeKey` está sin valor.
- **Toggle manual** en `PerformanceSettings.kt`: nuevo switch "Modo ultra ligero" bajo el de alto
  rendimiento. Activarlo enciende también `HighPerformanceModeKey` (ultra implica perf).
- **Reversible:** apagar ultra restaura los toggles del usuario tal cual (mismo patrón que
  `rememberPerfGatedBoolean` — nunca muta la preferencia guardada del usuario).

## Componentes / archivos

| Archivo | Cambio |
|---|---|
| `utils/DeviceCapabilities.kt` | `enum DeviceTier { ULTRA, LOW, MID, HIGH }` + umbral de detección ULTRA en `compute()` |
| `utils/PerformanceMode.kt` | `isUltra(context)`, `effectiveTier()` devuelve ULTRA, helper gated para ultra si hace falta |
| `constants/PreferenceKeys.kt` | `UltraLiteModeKey`, `UltraLiteSeedAppliedKey` |
| `App.kt` | seed primer inicio + migración `migrateUltraLiteSeed`; ajuste Coil (cache/decodes) en ULTRA |
| `ui/screens/settings/PerformanceSettings.kt` | switch "Modo ultra ligero"; `when(tier)` del label cubre ULTRA |
| `ui/screens/HomeScreen.kt` | rama ULTRA: carátulas chicas + tope de carruseles |
| `ui/player/Player.kt` | `when(deviceTier)` del buffer cubre ULTRA; carátula estática a menor tamaño |

## Interfaces clave

```kotlin
enum class DeviceTier { ULTRA, LOW, MID, HIGH }   // orden: más débil → más fuerte

object PerformanceMode {
    fun isOn(context): Boolean          // alto rendimiento (LOW). ultra ⇒ también true.
    fun isUltra(context): Boolean       // flag ultra OR hardware ultra-bajo
    fun effectiveTier(context): DeviceTier  // ULTRA si isUltra, si no el real
}
```

## Verificación

- Compila (el `when` exhaustivo garantiza que ningún gate quedó sin cubrir ULTRA).
- Prueba manual en un dispositivo/emulador de ~2 GB con ultra ON: arranque, scroll Home, abrir player
  sin congelarse.
- Toggle ON→OFF restaura la Home/reproductor normales (reversibilidad).
- Un dispositivo de gama media/alta: sin cambios de comportamiento (tier real, ultra OFF).

## Riesgos

- Umbral de detección ULTRA mal calibrado → un dispositivo capaz cae en ultra (feo) o uno débil no
  (sigue con jank). Mitigación: umbral conservador + toggle manual siempre disponible.
- Añadir ULTRA al enum rompe compilación en `when` no exhaustivos → **es la red de seguridad**, se
  arreglan uno a uno.
