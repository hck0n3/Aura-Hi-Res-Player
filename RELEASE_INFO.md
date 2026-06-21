# Aura Hi-Res Player v5.7.86

## Arreglo de cierre inesperado (crash)
- Corregido el cierre repentino que ocurría al ver un artista cuando la sección "Aparece en" mezclaba álbumes y singles/feats.
- Causa: la lista reutilizaba el espacio de una canción para un álbum (estructuras distintas) y Compose se rompía. Ahora cada tipo se reutiliza solo con su mismo tipo, y la sección de canciones nunca intenta tratar un álbum como canción.
