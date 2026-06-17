# Aura Glass — rediseño translúcido (Fase 1)

Date: 2026-06-17 · Branch: feat/jr-dsp-and-android-improvements

## Objetivo
El tema "Aura Glass" debe ser **cristal translúcido real**, no solo un color oscuro: paneles esmerilados que dejan ver un **fondo difuminado** derivado de la **carátula de la canción actual**, con **acento de color dinámico** del propio arte. Aprobado por el usuario sobre el mockup `.aura-mockup/aura-glass-concept.png`.

Color = **dinámico del arte**. Alcance = **por fases**; esta es la Fase 1 (Inicio + Player + navegación + mini-player, vía cambios globales de tema). Todo bajo el toggle existente `AuraThemeEnabledKey` (apagado = app intacta).

## Enfoque técnico (sin dependencia nueva, fiable)
Para entrega autónoma sin riesgo de romper el build, NO se añade la librería Haze. El look glass se logra con:
- **Fondo difuminado**: la carátula actual dibujada a pantalla completa con `Modifier.blur` (desenfoque nativo Compose, API 31+; en API menor degrada a imagen suave) + velo oscuro para legibilidad. Una sola ubicación: el root `BoxWithConstraints` de MainActivity.
- **Superficies translúcidas**: en `echomusicTheme`, cuando `auraEnabled`, se copia el `ColorScheme` con `surface`/`surfaceContainer*`/`background` translúcidos (alpha ~0.7, `background` transparente). Así TODAS las tarjetas, barra de navegación, sheets y paneles Material quedan esmerilados sobre el fondo difuminado, sin tocar cada pantalla.
- **Acento dinámico**: se extiende la extracción de color del arte ya existente (`enableDynamicTheme`) para que también aplique cuando `auraEnabled` → el seed del tema = color dominante de la portada que suena. Botones = iconos reales (ya lo son).

## Cambios
1. `MainActivity`: condición dinámica `enableDynamicTheme || auraThemeEnabled` en los dos `LaunchedEffect` de color; capturar `auraArtUrl` (thumbnail actual); en el root `BoxWithConstraints`, si `auraThemeEnabled`, dibujar `AsyncImage(auraArtUrl).blur()` + scrim detrás del contenido; fondo base negro cuando aura.
2. `Theme.kt`: `auraEnabled` → `ColorScheme` con superficies translúcidas + background transparente + shapes glass (ya existen).
3. Player ya fuerza fondo BLUR con Aura (v5.1.97) — se mantiene.

## Riesgos / mitigación
- Translucidez global puede afectar contraste en algunas pantallas → alpha moderado (0.7), `onSurface` claro, scrim del fondo. Opt-in (toggle).
- `Modifier.blur` solo API 31+ → en API menor el fondo se ve menos difuminado pero el look translúcido se mantiene.

## Fases siguientes (fuera de Fase 1)
Pulido específico de tarjetas Home/Biblioteca/Búsqueda, bordes glass por componente, animaciones.

## Verificación
Build `assembleUniversalFossDebug` verde; prueba manual con el toggle Aura Glass ON sobre una canción con carátula; publicar versión y comprobar auto-update.
