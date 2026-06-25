# Aura Hi-Res Player 0.6.40 — Sincronización de YouTube Music programada (parte 4 del plan)

## Sincronización automática de YouTube Music ⏱️
Ahora puedes **programar** la sincronización de YouTube Music (igual que Spotify): en **Ajustes ▸ Importar ▸ Sincronizar desde YouTube Music** elige **Cada día** o **Cada semana** y se mantendrá al día en segundo plano (me gusta, álbumes, artistas, suscripciones, playlists y biblioteca).

## Verás que de verdad corre (no placebo) ✅
Debajo aparece **"Última sincronización: hace X"** (o "nunca"), que se actualiza sola. Así sabes si la sincronización programada realmente se está ejecutando — y si se queda vieja, es señal de que algo (p. ej. la sesión) necesita atención.

## Bajo el capó (revisado antes de subir)
- Nuevo `YtmAutoSyncWorker` periódico (espejo del de Spotify; WorkManager, reintenta con red).
- El timestamp de "última sincronización" se sella **solo cuando una sincronización completa de verdad termina** (no si estás sin sesión), para que no muestre un falso "hace un momento".
- El tiempo relativo se actualiza en vivo mientras la pantalla está abierta.

## Próximamente (parte siguiente de sincronización)
- Sincronizar **una playlist concreta** (y "Me gusta") con un toque.
- Sincronización **diferencial** (solo lo nuevo/cambiado).
