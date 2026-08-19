# Aura Hi-Res Player 0.6.220

Segunda pieza del arreglo real: faltaba el origen (Origin/Referer), no solo el User-Agent.

---

## Novedades y Correcciones

- **Reproducción de canciones nuevas — arreglo completo**: la versión anterior agregó el User-Agent que faltaba, pero un diagnóstico en vivo mostró que YouTube también exige la cabecera "Origin" para el tipo de conexión que usa la app — y esa tampoco se estaba mandando. Este mismo arreglo ya existía, funcionando, en el reproductor de video y en las vistas previas de canciones; ahora también se aplica al audio normal, que es lo que usa el 99% de las reproducciones.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
