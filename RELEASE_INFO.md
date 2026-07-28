# Aura Hi-Res Player 0.6.134 — Crash del cliente cazado + reportes que se explican solos

## 🔴 El crash reportado por un cliente (0.6.133): cazado y blindado
Con herramientas nuevas de desofuscación rastreamos el cierre inesperado hasta su origen real: una **carrera interna del motor de audio** cuando la app tocaba la lista de un reproductor moribundo justo en el instante en que su canción terminaba (podía pasar en cualquier cruce — "escuchando música normal"). Blindado por dos lados:
- El desmontaje del cruce ya no hace la operación redundante que competía con el motor.
- Un aviso interno del generador de tokens que podía tumbar la app quedó protegido.

## 🛡️ Reportes de crash que se explican solos (para siempre)
- Cada reporte ahora incluye el **mensaje del error, su cadena de causas, el hilo, y las últimas 25 líneas de lo que la app estaba haciendo** (transiciones, resoluciones) — un crash futuro llega con su propia explicación.
- El mapa de desofuscación de CADA versión queda guardado — ningún crash volverá a ser ilegible.
- Nuevo libro permanente de crashes (`docs/CRASH_REPORTS.md`): cada reporte queda registrado con causa y estado — los errores no se repiten.

## Recordatorio
- Si un cruce no suena perfecto: **Ajustes ▸ Registros** → compartir → mandar. Cada transición trae su veredicto.
- **Cortes a mitad de canción** (Lossless/Saavn): mismo registro.
