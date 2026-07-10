# Aura Hi-Res Player 0.6.91 — Cristal en los botones, me gusta más claro y menos "cuelgues" de canciones

## 🫧 Botones flotantes con Liquid Glass
- Los **botones flotantes** (mezclar, micrófono y crear) ahora usan el efecto **Liquid Glass** cuando está activo — a juego con el mini-reproductor y la barra de navegación. En dispositivos donde el cristal no aplica, se ven igual que antes (sin cambios).

## 👍👎 Me gusta / No me gusta más claro
- Los pulgares del reproductor ahora empiezan **sin relleno** (contorno). Solo se **rellena el que eliges** — igual que YouTube Music hoy. Ya no parece que "no me gusta" estuviera activado por defecto.

## 📡 Menos canciones "colgadas"
- **Canciones que se quedaban como si no hubiera internet (y nunca cargaban):** ahora se distingue una canción **imposible de reproducir** (bloqueada por región, solo premium, restringida por edad…) de una **caída de red real**.
  - Si la canción no se puede servir: **falla rápido una vez, avisa el motivo y pasa a la siguiente** — se acabó el bucle infinito.
  - Si de verdad no hay internet: espera y reintenta con cordura, como antes.
- Se añadió un **límite de tiempo** a la resolución de YouTube (30 s) para que nunca se quede pensando para siempre, y la **cadena de respaldo** ya no se aborta si el primer cliente falla (más canciones se resuelven al primer intento).
- **Invitados (sin sesión):** las canciones con restricción de edad ahora intentan una vía sin login en vez de fallar seguro.

## 🤖 IA sin clave más robusta
- Las **playlists con IA** ahora funcionan de forma fiable aunque el servidor propio esté caído: si no responde, cae al instante al motor de respaldo (con reintentos) en vez de tardar y fallar.

## ✅ Como siempre
- Suscripción, licencia, demo, crossfade de 9s y motor de audio: intactos. Calor y batería vigilados.
