# Aura Hi-Res Player 0.6.159

Artista sin “Tu biblioteca” duplicada, lupa in-sheet, FFT que no muere al cambiar de pista, volumen de descargas/MP3 más parejo al stream, y portadas MP3.

---

## 🎨 Artista / Inicio

- Página de artista (New UI): una sola **Tu biblioteca** arriba; se omite la shelf YTM “De tu biblioteca / From your library” y títulos de sección repetidos. Espaciado de secciones unificado vía cabeceras Aura.
- Título **Para ti**: mismo color que el resto de secciones (OnGround), sin Teal forzado.

## 📱 Reproductor

- Lupa: artista / álbum / playlist se exploran **dentro** de la hoja frost (pila local); ya no colapsa el player.
- FFT (Axion): reengancha al cambiar de canción + watchdog; semáforo según preamp + headroom de bandas (no solo el slider). Un Visualizer compartido con el ritmo del suelo.

## 🔊 Audio / archivos

- Descargas: conservan el loudnessDb del stream (no lo pisa el fetch de descarga).
- Export MP3: portada JPEG real + `loudnorm` para acercar el nivel al stream nivelado; reintento sin cover/loudnorm si FFmpeg falla; MediaScanner.

## 🎚 EQ

- Presets audiófilos: rejilla ordenada por longitud de nombre (selección sigue el enum).

## 📦 Del 0.6.158

- Personalizar roles de color, chrome por portada, mini Liquid Glass clásico, scroll Copia/Spotify.
