# Aura Hi-Res Player 0.6.219

Causa real encontrada: el audio nunca mandaba el User-Agent que YouTube exige.

---

## Novedades y Correcciones

- **Reproducción de canciones nuevas, arreglo real esta vez**: la app resolvía el enlace de audio correctamente, pero al pedir el audio real, nunca mandaba el "User-Agent" (identificación del cliente) que YouTube exige para no rechazar la conexión — por eso siempre daba error de servidor (403 / IO_BAD_HTTP_STATUS). Esto no dependía de la versión ni de la canción: le pasaba a todo el que actualizara. El reproductor de video de la app ya tenía este arreglo desde antes; ahora también lo tiene el audio, que es lo que usa el 99% de las reproducciones.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
