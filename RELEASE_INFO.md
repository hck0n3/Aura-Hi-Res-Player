# Aura Hi-Res Player 0.6.174

Avisos en la cuenta, Auto-EQ dentro del ecualizador, menú Más más claro, Inicio que se rellena al reproducir o suscribirte, y arreglos de vídeos exportados/streaming.

---

## Avisos y comunicados

- En la ventana de cuenta (avatar / engranaje) hay acceso directo a **Avisos**.
- Si hay avisos sin leer, verás un **punto rojo** en el avatar/engranaje y en la fila de Avisos.
- Para avisar a todos sin sacar APK: edita `announcements.json` en la rama `main` del repo. La app lo descarga y muestra en la bandeja.

## Ecualizador y menú Más

- **Auto-EQ (por auricular)** está dentro del ecualizador Axion (ya no en Sonido).
- En el menú Más del reproductor quedan visibles: Ver artista, Ver álbum, Ecualizador, Detalles y Configuración; el resto va en secciones contraídas.

## Inicio

- Tras la primera reproducción (o al suscribirte a un artista), Inicio se rellena con recomendaciones reales.
- Si el último tema era local, se usan seeds de YouTube del historial para no dejar las estanterías vacías.

## Vídeos exportados y streaming

- Menú ⋮ de un vídeo exportado: **Eliminar del dispositivo** (lo quita de Aura y borra el archivo cuando Android lo permite).
- Menos trabón al empezar vídeo en streaming (más buffer y espera de colchón A/V).

## Del 0.6.173

- Vídeos exportados: modo vídeo offline con el MP4 local.
