# Aura Hi-Res Player v5.7.103

## Integrado del proyecto base (Echo-Music) + arranque más rápido

### Token de Apple Music dinámico (del upstream)
- Tu token de Apple Music estaba CADUCADO (afectaba a la info de álbum y a los canvas de Apple). Ahora la app obtiene un **token fresco automáticamente** desde la web de Apple Music (con caché), tal como hace el proyecto base. Esto mejora los **canvas** y la información de álbumes de Apple.

### Arranque de canciones más rápido (#8)
- Bajado el búfer de arranque de ~2.5s a ~1s: cada canción empieza a sonar **~1.5s antes** al darle play o al saltar. (En redes muy malas podría haber alguna micro-pausa breve al inicio; es reversible.)
