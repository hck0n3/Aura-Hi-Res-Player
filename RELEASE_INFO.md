# Aura Hi-Res Player 0.6.96 — Discografías completas, playlists de YouTube que sí sincronizan y Spotify a prueba de fallos

## 💿 Discografías completas (iTunes/Apple → YouTube Music)
- La sección **"Ver todo"** de un artista vuelve a **completar la discografía** usando iTunes/Apple Music como referencia de qué lanzamientos existen y resolviéndolos en YouTube Music (como álbum o como lista donde estén publicados). Se había roto: se excluían los **EP y sencillos**, y como iTunes devuelve la mayoría del catálogo así, no se añadía casi nada. Ahora se completa el **catálogo entero**.

## 🔁 Sincronización de YouTube Music — las playlists sí aparecen
- Arreglada la **"Sincronización total"** que **nunca traía las playlists**. Iban de últimas en la cola y las tareas previas (que volvían a descargar cada álbum y cientos de imágenes de artista) agotaban el tiempo del proceso antes de llegar a ellas. Ahora:
  - Las **playlists se sincronizan primero** (no se quedan sin tiempo).
  - Ya **no se vuelve a descargar cada álbum** que ya tienes (mucho más rápido).
  - "Sincronizar todo" también lanza las playlists en su propia tarea, por seguridad.

## 🟢 Importación de Spotify — que nada tumbe todo
- Si **una fuente falla** al importar de Spotify (una playlist borrada, un límite temporal, etc.), ya **no se cae toda la importación**: esa fuente se marca como fallida y **el resto se importa igual**.

## 🧭 Sin regresiones
- Verificado contra el **registro de fallos**: ningún arreglo anterior se rompió en esta versión.

## ✅ Como siempre
- Suscripción, licencia, demo, crossfade de 9s y motor de audio: intactos. Calor y batería vigilados.
