# Aura Hi-Res Player 0.6.164

Portadas de MP3 locales restauradas y recuperación cuando un stream/vídeo llega como archivo inválido.

---

## 📂 Música local

- Las portadas embebidas (APIC/ID3) y las de carpeta (`cover.jpg`, etc.) vuelven a mostrarse en la app, notificación y Android Auto.
- Corregido el fallo `localaudioart:…` que el sistema trataba como ContentProvider inexistente.
- Tras actualizar: **vuelve a escanear** tu música local si alguna portada o etiqueta sigue en blanco.

## 🎬 Vídeo / streaming

- Si el stream llega vacío o no es un contenedor válido (error 3003 / “Source error”), se descarta la URL mala y se vuelve a resolver en lugar de quedarse sin audio.

## 📦 Del 0.6.163

- Hotfix del cierre con crossfade al pasar de canción en playlists.
