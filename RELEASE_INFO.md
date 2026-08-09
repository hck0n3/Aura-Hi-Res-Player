# Aura Hi-Res Player 0.6.162

Abrir archivos de audio locales desde otras apps, mejores etiquetas/portadas de MP3 y streaming de vídeo más fiable.

---

## 📂 Música local

- **Abrir con Aura**: desde el gestor de archivos, Descargas u otra app puedes abrir un MP3/FLAC/OGG y suena al instante.
- Si MediaStore no trae título/artista/álbum (archivos recién bajados), se leen las etiquetas ID3 del propio archivo.
- Portadas: además del arte embebido, se busca `cover.jpg` / `folder.jpg` / `album.jpg` en la carpeta del tema.

## 🎬 Vídeo / streaming

- En modo vídeo se usa el cliente de vídeo correcto al resolver el stream (antes podía quedar en el de solo audio).
- Si la URL trae el parámetro `n=`, se aplica el n-transform aunque el cliente no lo pidiera explícitamente — evita 403 en algunos streams.
- Peticiones con cliente TV mandan el User-Agent de TVHTML5 (antes podían ir mal firmadas).

## 📦 Del 0.6.161

- Vídeo más estable (sin trabones por rebinds ni `prepare` en rebuffers) y Cast oculto con letras abiertas.
