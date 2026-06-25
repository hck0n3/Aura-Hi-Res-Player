# Aura Hi-Res Player 0.6.35

## Modo video: pantalla más limpia y video mejor centrado 🎬
- **Al tocar para ocultar los controles, ahora también se oculta el título** — así el video queda **a pantalla completa sin nada encima**. Tocas otra vez y vuelven título + controles.
- **El video baja un poco** (estaba un pelín alto) para que quede mejor centrado entre el título y los botones.

> Nota: si tu **giro automático está activado**, al poner el teléfono de lado el video pasa a **horizontal real** (pantalla completa, sin título, barras del sistema ocultas). Si lo tienes desactivado, sigues viendo la vista vertical de lado — y ahí aplican estos cambios.

## Técnico
- `Player.kt` (rama inmersiva vertical): el título superior se gatea con `inPip || ptControls` (se oculta junto con los controles). El video se alinea con `BiasAlignment(0f, 0.12f)` (un poco por debajo del centro de su región).
