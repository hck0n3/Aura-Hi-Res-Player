# Aura Hi-Res Player 0.6.82 — Auditoría completa: estabilidad, rendimiento y TV

## 🛡️ Estabilidad (auditoría completa del código)
- Se corrigieron **decenas de bugs, crashes, fugas de memoria y condiciones de carrera** encontrados auditando toda la app.
- **Crashes arreglados:** al salir reproduciendo, cola con canciones repetidas, restaurar copia de seguridad, importar playlists, ecualizador cuando falla la librería nativa.
- **Datos correctos:** importación de Spotify ya no pierde playlists/artistas; ringtone recorta de verdad; add-to-playlist ya no se atora.
- **Reporte de crashes** activado para arreglar más rápido lo que aparezca.

## ⚡ Rendimiento
- Menos tirones: trabajo pesado movido fuera del hilo principal (búsquedas, backup, imágenes, listas).
- Arranque de reproducción más fluido y base de datos con índice (estadísticas más rápidas).

## 📺 TV
- **El foco ya no se pierde** al moverte entre elementos con el control.

## ▶️ Video y datos
- **Cambio a video casi instantáneo** (se prepara por adelantado) en TV y celular.
- **La portada animada del álbum ya no se re-descarga** por cada canción → ahorro de datos.

## 🔒 Seguridad
- Datos sensibles (licencia, sesión) **excluidos de la copia de seguridad** del sistema.

## ✅ Actualización segura
- La base de datos **se actualiza sola** al abrir (sin perder historial, playlists ni descargas). Suscripción, licencia y demo intactos.
