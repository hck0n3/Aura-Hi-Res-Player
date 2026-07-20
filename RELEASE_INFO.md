# Aura Hi-Res Player 0.6.111 — Ya no hay que cerrar sesión, y tus datos dejan de salir en el registro

## 🔑 Ya no tienes que cerrar sesión a cada rato
- Cuando tu sesión de YouTube caducaba, la app **no sabía recuperarse sola**: cada canción se quedaba esperando hasta 30 segundos y luego decía "no disponible". La única salida era cerrar sesión y volver a entrar a mano.
- Ahora, si detecta que el problema es la sesión, **reintenta esa canción sin ella** y sigue sonando. **Tu cuenta no se toca en ningún momento.**

## 🔒 Tus datos ya no salen en el registro que compartes
- La app escribía en su archivo de registro —el que puedes abrir y compartir desde Ajustes— **títulos de canciones, nombres de artistas, IDs de vídeo y respuestas completas del servidor**.
- Eran **42 líneas de depuración que se colaban en la versión pública**. Todas eliminadas.
- A partir de ahora, **si compartes un registro para reportar un fallo, tu biblioteca no va dentro**.

## 💾 Protegida tu biblioteca de subidas
- Si YouTube devolvía una respuesta que la app no sabía leer, podía interpretarlo como **"no tienes nada subido"** y **quitarle la marca de subido a toda tu biblioteca**. Bastaba con abrir la pantalla de Subidas.
- Ahora, ante una respuesta que no entiende, **no toca nada**. Y aunque llegara vacía, se niega a desmarcar si tú sí tienes canciones.

## 🔔 La música ya no suena "a escondidas"
- Cuando Android rechazaba arrancar el servicio, la música empezaba **sin notificación ni controles** y el sistema la mataba poco después.
- Ahora **pausa**, que es algo que puedes ver y decidir. Incluidos los reproductores del fundido entre canciones.

## 🎤 Letras: menos peticiones inútiles
- Un proveedor que bloquea a Aura se reintentaba **3 veces por canción, para siempre**. Ahora se detecta y se deja en paz 6 horas.
- **Las letras nunca dejaron de funcionar** (hay 7 proveedores más), pero se gastaban peticiones y se llenaba el registro de errores.

## ⚠️ Todavía no arreglado
- **Cortes de milisegundos** que adelantan la canción: causa probable identificada, sin confirmar.
- **Volumen Seguro** sube como máximo +3 dB. El nivelado completo necesita medir el pico de cada canción.
