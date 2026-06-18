# Aura Hi-Res Player v5.4.4

## Arreglo: se reproducía la canción equivocada
- En álbumes (p. ej. una canción "Dirt" de Lecrae) a veces sonaba **otra canción de otro artista**. Causa: cuando usabas calidad **Lossless/Saavn**, la app buscaba la pista por **título** en esos servicios y, para títulos comunes, traía la de otro artista.
- Ahora **exige que el artista coincida**: si no encuentra la misma canción del mismo artista en Lossless/Saavn, reproduce la **original de YouTube** (la correcta) en vez de sustituirla por otra.

## Incluye lo anterior
- v5.4.3: fix de online muerto tras restaurar (proxy/token). v5.4.2: graves/agudos limpios, widget anterior/siguiente.
