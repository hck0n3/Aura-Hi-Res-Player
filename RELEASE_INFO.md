# Aura Hi-Res Player v5.7.79

## Discografía del artista: bug real encontrado y corregido
- Verifiqué con la base de datos real: iTunes SÍ tiene los álbumes (ej. "Privé - EP" de Juan Luis Guerra), pero la app **no los emparejaba** porque:
  1. No quitaba el sufijo "- EP" / "- Single" al comparar títulos (comparaba "privé ep" contra "privé").
  2. El emparejado con playlists no era bidireccional.
- Corregido. Además ahora consulta iTunes **EE.UU. + tu país** (más completo) y se queda con los **lanzamientos propios** del artista (no donde solo es invitado).
