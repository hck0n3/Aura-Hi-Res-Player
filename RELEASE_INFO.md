# Aura Hi-Res Player 0.6.226

Restaurado el código exacto de la 0.6.213 — la última versión que confirmaste que sí reproduce, hoy mismo, en tu teléfono.

---

## Novedades y Correcciones

- **Reproducción de canciones**: confirmaste que la beta de ayer (0.6.213, publicada como a las 6:41pm) sigue reproduciendo música hoy con tu misma cuenta, en WiFi y en datos — mientras que todas las versiones después de esa (incluida la de hace un rato) fallan igual, incluso con otra cuenta y otra red. Eso descartó de raíz mis teorías de "cabeceras" y "bloqueo de YouTube a la cuenta": el problema estaba en un cambio de código entre esa versión y las siguientes. En vez de seguir adivinando cuál línea exacta era la culpable, se restauró el código de resolución de audio (YTPlayerUtils, MusicService, la lista de clientes de YouTube y la configuración de cifrado) exactamente a como estaba en la 0.6.213 — el mismo código que ahora mismo está funcionando en tu teléfono. No se tocó ninguna otra función de la app.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
