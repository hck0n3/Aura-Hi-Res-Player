# Aura Hi-Res Player 0.6.115 — Corregido un cierre de la app y el mensaje de error engañoso

## 🛑 Ya no se cierra la app al tocar con la cola vacía
- Si todas las canciones fallaban al cargar y la cola se quedaba vacía, tocar la pantalla **cerraba la app** de golpe.
- Era un fallo que introduje yo en una mejora anterior. Corregido.

## 💬 Mensaje de error honesto (ya no dice "restricción de edad" sin serlo)
- Cuando YouTube rechazaba una canción, la app decía **"no se pueden reproducir canciones con restricción de edad"** — aunque la canción no tuviera nada de eso.
- Eso mandaba a buscar un ajuste de edad que no existe, cuando el problema real era otro (sesión o conexión).
- Ahora el mensaje es **preciso**: si YouTube rechaza la petición, te dice que revises la conexión y, si sigue, cierres sesión y vuelvas a entrar.

## ✅ Confirmado: el descifrado de YouTube está bien
- El bloqueo de reproducción que hubo estos días **no era el descifrado** (lo verifiqué ejecutando el código real de YouTube).
- Cuando a alguien no le suena nada aunque cargue el resto, la causa suele ser **una sesión local dañada**: la cura es **reinstalar la app e iniciar sesión de nuevo**.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — necesito tu registro en esa calidad.
- **Volumen Seguro** sube máximo +3 dB.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
