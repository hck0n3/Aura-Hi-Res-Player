# Aura Hi-Res Player 0.6.161

Vídeo más estable (sin trabones por rebinds ni `prepare` en rebuffers) y Cast oculto cuando las letras están abiertas.

---

## 📱 Reproductor

- Con **letras** activas, el botón **Cast** de la esquina superior derecha se oculta (clásico y New UI).

## 🎬 Vídeo / streaming

- La superficie de vídeo ya no llama `setVideoTextureView` en cada recomposición (solo al cambiar de TextureView o de URL) — eso congelaba el cuadro mientras el audio seguía.
- La recuperación “vídeo atascado” solo actúa en **IDLE** (pipeline muerto). Un BUFFERING normal de varios segundos ya no dispara `prepare()` a los 3 s (que reiniciaba el stream y se veía como traba → continúa).

## 📦 Del 0.6.160

- Portadas MP3: lectura APIC/ID3 real + caché Coil + Exportadas apuntan al archivo.
