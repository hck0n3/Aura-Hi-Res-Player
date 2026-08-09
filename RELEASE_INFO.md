# Aura Hi-Res Player 0.6.160

Aura ya lee la portada embebida de los MP3 locales/exportados (antes prefería una miniatura vacía de MediaStore).

---

## 🖼 Portadas MP3 / música local

- Al mostrar carátulas de archivos locales, Aura lee primero el **APIC/ID3** del propio audio; `loadThumbnail` de MediaStore solo es respaldo (antes devolvía éxito con un glifo vacío y nunca llegaba al cover real).
- Tras exportar a MP3, la fila de la canción apunta al archivo exportado para que **Exportadas** / el reproductor usen esa portada.
- Una limpieza one-shot de la caché de imágenes de Coil evita seguir viendo miniaturas en blanco cacheadas de versiones anteriores.

## 📦 Del 0.6.159

- Artista sin “Tu biblioteca” duplicada, lupa in-sheet, FFT estable, volumen de descargas/MP3 más parejo, export con JPEG + loudnorm.
