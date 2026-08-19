# Aura Hi-Res Player 0.6.229

⚠️ Cambio de identidad de la app: nuevo paquete, instalación aparte, no es una actualización de la anterior.

---

## Novedades y Correcciones

- **Reproducción de canciones — cambio de identidad**: con la 0.6.228 (ofuscación apagada) confirmado que R8 no era la causa, la única variable que quedaba sin probar era el identificador del paquete (`iad1tya.echo.music`) — visible para Google en cada solicitud, distinto de tu beta de prueba (`iad1tya.echo.music.debug`), que nunca dejó de funcionar. Se renombró a `iad1tya.aura.music`. Esto instala como una app APARTE junto a la que ya tienes — no la reemplaza ni la actualiza automáticamente.
- **Menos "disfraces" por canción**: además del cambio de paquete, se recortó la lista de 11 clientes de YouTube que la app probaba en cascada por cada canción fallida a solo 6 — se quitaron los 3 que nunca funcionaron en ningún log de esta investigación (siempre fallaban igual) más 2 duplicados redundantes. Menos "disfraces" simultáneos = menos parecido a un patrón automatizado.
- **Firebase desactivado temporalmente**: Analytics/Crashlytics no van a reportar datos con este paquete nuevo hasta que se registre en la consola de Firebase — eso no lo puedo hacer yo, necesita tu acceso.

## Cómo actualizar

- Esta NO llega por el actualizador de la app vieja — hay que instalarla por separado, como una app nueva.
