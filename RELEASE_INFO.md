# Aura Hi-Res Player 0.6.168

Biblioteca reorganizada (Vídeos exportados en el hub, Descargados/Caché en Local, sin Subidas), CSV de Spotify a tu almacenamiento, búsqueda sin título que tapa, vídeos más estables, y **Para ti / Recomendado IA que ya no se congelan**.

---

## 🎯 Para ti / recomendaciones

- **Para ti** (Inicio): ya no se queda en las mismas 20 canciones locales — mezcla del día + relacionadas reales de YouTube (antes el enriquecimiento solo aceptaba canciones ya en tu BD = cero descubrimiento).
- El snapshot de Inicio caduca al cambiar de día (si el proceso sigue vivo varios días, vuelve a cargar solo).
- **Recomendado para ti (IA)**: no re-publica el lote anterior; si la IA falla hay fallback por radio/relacionados; “Refrescar ahora” ya no se queda bloqueado; al abrir la app, si lleva >20 h sin actualizar se dispara un refresco.

## 📚 Biblioteca (Interfaz nueva)

- Nuevo tile **Vídeos exportados** junto a Me gusta / Exportado / Álbumes favoritos.
- **Descargados** y **En caché** viven dentro de **Local** (ya no en el hub).
- **Subidas** eliminado del hub, Apariencia y filtros Canciones/Álbumes.
- Al buscar en una lista: solo barra y resultados — **sin** título sticky del nombre encima.

## 📥 Spotify

- Exportar fallos: **Guardar CSV en el dispositivo** (elige carpeta) o **Compartir** (Migrar).

## 🎬 Vídeos

- Al tocar un vídeo (incl. exportados) el reproductor se abre en modo vídeo para verlo.
- Menos trabones buffering↔play al cambiar a vídeo (ya no se reinicia el pipeline en BUFFERING).
- Dar **Me gusta** (o Descargar) mientras ves un vídeo ya no lo tumba a “no disponible” / solo audio.

## 🛡️ Pantalla bloqueada / logs

- Tu diagnóstico 0.6.167 solo muestra salidas por **instalación de updates** (`PACKAGE_UPDATED`), no kills OEM. El keep-alive al apagar pantalla sigue activo; si un microcorte vuelve, envía un log nuevo con la hora exacta.

## 📦 Del 0.6.167

- Import Spotify con CSV/reintento, loudnorm de export, share de artista, multi-artista, lupa sin tirón, refresh de biblioteca, publicar solo con CI verde.
