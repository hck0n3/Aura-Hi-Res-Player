# Aura Hi-Res Player 0.4.0

## Migración selectiva (copia por partes) 🧳
- En **Ajustes ▸ Copia de seguridad ▸ "Migración selectiva (Aura)"** ahora eliges exactamente qué exportar a un archivo:
  - **Playlists**: marca las que quieras (seleccionable).
  - **Todos los artistas** seguidos, de una vez.
  - **Todos los presets de EQ**, de una vez.
- Para traerlo: **Ajustes ▸ Importar ▸ "Importar migración selectiva (Aura)"**.
- La importación es **estrictamente aditiva**: solo añade, **nunca borra** nada de tu biblioteca (en el peor caso, un duplicado — jamás pérdida de datos). Las playlists se reconstruyen con el importador probado.

## Técnico
- `SelectiveBackup` (JSON): playlists en formato `JrPlaylistFile` (reusa `JrPlaylistImporter`), artistas (`ArtistRec`) y presets (`SavedEQProfile`).
- BackupRestoreViewModel: `exportSelective` (solo lectura de la biblioteca) e `importSelective` (aditivo: JrPlaylistImporter por playlist, insert de artistas con bookmarked, `saveProfile` de presets con id nuevo).
- UI: diálogo de selección en la pantalla de Copia + entrada en Importar.
