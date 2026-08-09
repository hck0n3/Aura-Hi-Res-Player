# Aura Hi-Res Player 0.6.163

Hotfix: ya no se cierra la app al pasar de canción con el fundido (crossfade) encendido.

---

## 🛠️ Estabilidad

- Corregido un cierre al reproducir playlists con **crossfade ON**: el player saliente ya no muta su cola a mitad del fundido (eso chocaba con media3 y lanzaba un `IllegalStateException` sin mensaje). Ahora se estaciona al final del tema sin tocar la lista.
- También se quitaron mutaciones equivalentes en el swap rápido de vídeo.

## 📦 Del 0.6.162

- Abrir audio local desde otras apps, mejores etiquetas/portadas ID3 y streaming de vídeo más fiable.
