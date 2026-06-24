# Aura Hi-Res Player 0.6.25

## Calidad de video según tu conexión 📶
- Con **WiFi** (o conexión sin medir): el video carga en **720p** (máxima calidad muxed).
- Con **datos móviles** (o red marcada como "medida"): baja a **360p** para gastar menos datos y cargar más rápido.
- El cambio es automático; no tienes que tocar nada.

> Nota técnica: el video integrado usa streams *muxed* (video+audio en uno, lo que permite el audio en segundo plano sin doble audio). YouTube solo ofrece muxed en 360p y 720p — no existe un 480p muxed real, así que la elección por conexión es entre esas dos.

## Técnico
- `YTPlayerUtils.findFormat(preferVideo)`: el itag muxed se elige por `connectivityManager.isActiveNetworkMetered` — no medido → 22 (720p) primero; medido → 18 (360p) primero; con fallback al otro y luego a cualquier muxed.
