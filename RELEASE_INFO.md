# Aura Hi-Res Player 0.6.112 — Recuperación de errores más lista: menos re-descargas y menos canciones saltadas

## 🔁 Ya no se re-descarga la canción entera por cualquier tropiezo
- Ante **cualquier** error —incluso uno pasajero de red— la app borraba el audio ya descargado de la canción **antes siquiera de saber qué error era**, y tenía que empezar de cero.
- Ahora solo se borra cuando de verdad hace falta. Menos datos, menos espera y menos trabajo en el hilo principal.

## 🎵 Menos canciones saltadas por una URL caducada
- Cuando el enlace de una canción moría, la app **reintentaba tres veces con el mismo enlace muerto** y luego se saltaba la canción — cuando pedir uno nuevo la habría reproducido sin problema.
- Ahora pide enlace nuevo en el reintento. **El audio ya descargado se conserva**: un enlace viejo no dice nada del audio que ya tienes.

## 🔧 Y una canción con datos dañados vuelve a curarse sola
- Si los datos guardados de una canción se corrompían, se reintentaba **sobre los mismos datos malos** hasta saltarla, en cada reproducción.
- Ahora se detecta y se descarta ese audio dañado para bajarlo limpio.

## ⚠️ Todavía no arreglado
- **Cortes de milisegundos que adelantan la canción.** Probé una hipótesis y **era falsa** — lo verifiqué contra el código interno de Android y no era la causa. Sigue abierto, con un candidato nuevo y mejor identificado.
- **Volumen Seguro** sube como máximo +3 dB. El nivelado completo necesita medir el pico de cada canción.
- **Plegables:** cuatro mejoras hechas y compiladas, pero **sin probar en un plegable real**.
