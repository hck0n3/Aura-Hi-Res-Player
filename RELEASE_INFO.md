# Aura Hi-Res Player v5.4.1

## Arreglo: búsqueda muerta tras restaurar respaldo
- Después de **restaurar un respaldo local**, la búsqueda, las sugerencias y los álbumes dejaban de cargar (como si no hubiera internet). Causa: el respaldo traía un **token de sesión viejo de YouTube** que se restauraba y rompía las consultas. Ahora, al restaurar, ese token se **renueva automáticamente** y todo vuelve a funcionar.

## Incluye lo anterior
- v5.4.0: sección de artistas seguidos, miniaturas más pequeñas, búsqueda solo de música, "Me gusta" de Spotify a tus favoritos, +0.5 dB.
