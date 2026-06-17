# Plan — Android TV + Sonido unificado + Auto-EQ online + fix backup

Date: 2026-06-16 · Branch: feat/jr-dsp-and-android-improvements

Cinco frentes. Orden por prioridad (el bug primero). Cada uno deja build verde antes del siguiente.

---

## 1. FIX backup/restore (BUG — primero)

**Causa raíz A (no restaura):** `BackupRestoreViewModel.restore()` rechaza `backupVersion > 35`, pero la BD actual es **36**. Un backup de la app actual se rechaza con "Backup is from a newer app version".
**Causa raíz B (novedades no cargan):** `reseedAfterRestoreIfNeeded()` solo borra 2 guards (`JrDefaultsAppliedKey`, `SpanishDefaultAppliedKey`). Frágil: cada feature nueva exige editarlo.

**Solución:**
1. **Gate de versión dinámico**: leer la versión real de la BD (Room `helper.readableDatabase.version` / constante `InternalDatabase` ) en vez de `35` hardcodeado. Permitir `backupVersion <= currentVersion`. Extraer la comparación a una función pura `canRestoreDbVersion(backupVersion, currentVersion)` → unit-test.
2. **Re-seed robusto por versión de semilla**: sustituir los guards booleanos por un único `SeedVersionKey` (Int, p.ej. `seed_version`). `seedDefaults` corre si `stored < CURRENT_SEED_VERSION`. Como un backup viejo trae un `SeedVersion` menor (o ausente), **el re-seed se dispara solo tras restore** — sin flag-file ni listas de guards. Migrar los 2 guards actuales a este esquema (compatibilidad: si existen los viejos, tratarlos como seed v1).
3. Mantener el reinicio del proceso tras restore (ya está). Quitar el flag-file `POST_RESTORE_REINIT_FLAG` (ya no hace falta) o dejarlo como no-op.
4. Test: `canRestoreDbVersion` (igual/menor OK, mayor rechaza) + lógica de `shouldReseed(storedSeedVersion)`.

**Archivos:** `BackupRestoreViewModel.kt`, `App.kt`, `PreferenceKeys.kt`, nuevo `BackupGate.kt` (puro, testeable) + test.

---

## 2. Sección "Sonido" unificada y ordenada

Hoy el audio está disperso dentro de `PlayerSettings` (EQ enlaza a pantalla Axion; DSP/efectos/sonoridad/visualizador como toggles sueltos). Unificar en **una pantalla `SoundSettings` ("Sonido")** con subsecciones claras y colapsables:

```
Ajustes → Sonido
 ├─ Ecualizador            → abre EQ Axion (gráfico + bandas)
 ├─ Auto-EQ (auricular)    → buscar/aplicar perfil (frente 3)
 ├─ Efectos / DSP
 │    Sonoridad (FM) · Realce graves · Excitador · Comp. multibanda
 │    · Ancho estéreo · Diálogo · Sala virtual (HRTF)
 ├─ Volumen / Masterización
 │    Normalización loudness (−14 LUFS) · info limitador true-peak
 ├─ Mejorar calidad baja   (declip + regen agudos)
 └─ Visualizador de espectro
```

- Crear `ui/screens/settings/SoundSettings.kt`; **mover** ahí los ítems de audio que hoy viven en `PlayerSettings` (no duplicar: cortar de PlayerSettings, dejar en PlayerSettings solo lo de reproducción: calidad descarga, crossfade, skip silence, etc.).
- Añadir entrada "Sonido" en `SettingsScreen` con icono.
- Agrupar con encabezados/`Card` por subsección (más ordenado, como pidió el usuario).
- Sin cambios de lógica DSP (ya hecha) — solo reorganización UI + navegación.

**Archivos:** nuevo `SoundSettings.kt`, `PlayerSettings.kt` (recortar), `SettingsScreen.kt` (entrada + ruta), `strings.xml`/`es`.

---

## 3. Auto-EQ online (repo AutoEq, 5000+ modelos)

Buscar el auricular → descargar su perfil ParametricEQ del repo AutoEq → parsear → aplicar al EQ.

- **Índice**: usar el árbol del repo `jaakkopasanen/AutoEq` (carpeta `results/`) vía GitHub API/raw. Cachear el índice de nombres localmente (refresco diario).
- **Descarga**: al elegir modelo, bajar `<model> ParametricEQ.txt` (formato: `Preamp: -6.0 dB` + líneas `Filter N: ON PK Fc <hz> Hz Gain <db> dB Q <q>`).
- **Parser puro** `AutoEqParser` (preamp + lista de bandas PK) → mapear a las bandas/preamp del motor EQ (`EqConstants`/`BiquadFilter`/`AxionEqViewModel`). Unit-test con un ejemplo real.
- **UI** en la sección Sonido → "Auto-EQ": buscador con autocompletar (índice cacheado), botón Aplicar (escribe preset en el EQ), opción "quitar". Requiere red solo al buscar/aplicar; el perfil aplicado queda guardado offline.
- Mapeo: AutoEq da PEQ paramétrico (Fc/Gain/Q arbitrarios); el motor Axion usa bandas ISO fijas. Estrategia: si el motor soporta bandas paramétricas, aplicarlas directas; si son ISO fijas, **proyectar** (interpolar ganancia a las frecuencias ISO más cercanas) + preamp. Decidir al leer `AxionEqViewModel`.

**Archivos:** nuevo paquete `eq/autoeq/` (`AutoEqRepository.kt`, `AutoEqParser.kt` + test), UI en `SoundSettings`/pantalla nueva, red vía el cliente HTTP existente.

---

## 4. Android TV — usable con control (D-pad)

El manifest ya tiene `leanback` (no-required), `LEANBACK_LAUNCHER`, banner → instala y abre en TV. Falta navegación con control:

- **Foco D-pad**: revisar pantallas clave (Home, Search, Library, Player, Sound) → `Modifier.focusable()`, `focusRequester`, orden de foco, indicador de foco visible (borde/escala) en items de lista y botones.
- **Reproducción**: player a pantalla completa controlable con remoto (play/pause/seek con D-pad, botones de media del control).
- **Sin teclado táctil**: búsqueda usable con control (campo enfocable + teclado en pantalla del sistema TV).
- **Layout**: verificar overscan/márgenes en pantalla grande; ocultar gestos táctiles donde no apliquen.
- No se crea UI separada (decisión del usuario) — se adapta la actual.

**Archivos:** pantallas Compose principales (focus modifiers), `MainActivity` (detectar `uiMode` TV para ajustes menores), manifest (confirmar `tv_banner`/categorías).

---

## 5. Integración + verificación
- Build incremental verde tras cada frente (`assembleUniversalFossDebug` + `testUniversalFossDebugUnitTest`).
- Tests nuevos: `BackupGate` (versión), `AutoEqParser`.
- Prueba manual: restaurar un backup de la versión actual (debe funcionar y traer novedades); sección Sonido ordenada; Auto-EQ aplica un perfil; navegación TV con control (emulador TV o `uiMode`).

## Orden de entrega
1) Fix backup → 2) Sección Sonido → 3) Auto-EQ online → 4) Android TV → 5) verificación final.

## Fuera de alcance (a propósito)
UI leanback dedicada de TV (el usuario eligió "usable con control"). Lossless (fuente YouTube). Tocar firma/keystore/slug Gumroad.
