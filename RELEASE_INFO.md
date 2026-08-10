# Aura Hi-Res Player 0.6.169

Exportar vídeo fiable, descarga offline que no pelea con el vídeo en vivo, export MP3/vídeo más rápido, y menos trabones al ver vídeo.

---

## 🎬 Vídeo: descargar vs exportar

- **Exportar → Como vídeo** sondea el stream real (no solo el flag “es vídeo”). Si no hay, mensaje claro.
- El MP4 exportado entra en **Vídeos exportados** (Descargar no llena esa lista).
- **Descargar** mientras ves el vídeo: se aplaza audio y vídeo hasta salir del modo vídeo (antes el audio seguía bajando y provocaba trabones/403).
- En modo vídeo se **pausan** las descargas offline de Exo; al salir se reanudan.
- Textos + empty state del tile aclaran Descargar vs Exportar.

## ⚡ Export más rápido

- OkHttp con buffer grande y keep-alive; portada MP3 en paralelo con el audio.
- Export vídeo: baja audio+vídeo a la vez; intenta mux con `-c:v copy` (sin re-encode) y si falla usa `ultrafast`.

## 📺 Menos trabones al ver

- Tras un stall, el player espera más buffer antes de reanudar (5 s / 3,5 s en modo rendimiento) — el merge audio+vídeo necesita más margen.
- Sin descargas offline compitiendo con el stream en vivo.

## 📦 Del 0.6.168

- Biblioteca reorganizada, Para ti / IA que rotan, CSV Spotify a disco.
