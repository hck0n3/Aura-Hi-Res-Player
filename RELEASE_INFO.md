# Aura Hi-Res Player 0.6.215

Corrección real del error 403 en canciones nuevas — la de 0.6.214 no era suficiente.

---

## Novedades y Correcciones

- **Reproducción de canciones nuevas corregida de verdad**: la fórmula guardada para descifrar el enlace de audio del reproductor actual de YouTube tenía un símbolo que no existe en el archivo real de YouTube, así que fallaba en silencio y la app intentaba reproducir con el enlace sin descifrar (error `IO_BAD_HTTP_STATUS`, código 2004). El arreglo de la versión anterior no lo resolvía porque su plan de respaldo dependía de esa misma fórmula rota. Esta vez se corrigió el símbolo y se comprobó ejecutando el archivo real de YouTube para confirmar que el descifrado funciona.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
