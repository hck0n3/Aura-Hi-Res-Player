# Aura Hi-Res Player 0.6.214

Corrección crítica: canciones nunca antes reproducidas fallaban con error del servidor (403).

---

## Novedades y Correcciones

- **Reproducción de canciones nuevas corregida**: cuando el sistema que descifra el enlace de audio no encontraba la fórmula para la versión actual del reproductor de YouTube, la app usaba el enlace sin descifrar y el servidor lo rechazaba (error `IO_BAD_HTTP_STATUS`, código 2004). Ahora, si eso pasa, se le da automáticamente una segunda oportunidad al sistema de respaldo antes de intentar reproducir — así una fórmula ya guardada y verificada no se desperdicia.
- **Auto-reparación real**: el bot que detecta cuando YouTube cambia su reproductor y publica la fórmula nueva sin que tengas que actualizar la app llevaba fallando en silencio. Ya está corregido y probado en vivo.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
