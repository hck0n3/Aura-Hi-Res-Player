# Aura Hi-Res Player 0.6.21

## Picture-in-Picture 📺
- Si sales de la app (botón de inicio) **mientras se reproduce un video**, ahora el video **flota en una ventana** Picture-in-Picture y sigue viéndose mientras usas otras apps.
- En la ventana PiP se muestra **solo el video**; tócala para volver a la app. El audio nunca se corta (lo lleva el servicio en primer plano).

> Es la primera versión de PiP — pruébala en tu cel. Si ves algo raro (parpadeo al encoger/agrandar, o que no entra en PiP en tu Android), dime y lo afino (la revisión interna solo detectó un posible parpadeo de 1 frame durante la animación, sin errores).

## Técnico
- `MainActivity`: `supportsPictureInPicture=true`; `onUserLeaveHint()` → `enterPipModeIfVideo()` (PiP solo si `videoMode` + reproduciendo) con aspect ratio de `player.videoSize` (clamp 0.45..2.3, API≥O, `runCatching`); `onPictureInPictureModeChanged` → estado `inPipMode`; en PiP, `echomusicApp` renderiza solo `PlayerVideoSurface` a pantalla completa.
