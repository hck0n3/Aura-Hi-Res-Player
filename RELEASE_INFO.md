# Aura Hi-Res Player 0.6.222

Causa encontrada con evidencia real: al cliente que casi siempre resuelve tus canciones nunca se le configuró mandar el token anti-bot.

---

## Novedades y Correcciones

- **Reproducción de canciones nuevas**: un diagnóstico en tu propio dispositivo (0.6.221) mostró que el "token" anti-bot que YouTube exige para descargar el audio (`pot=`) nunca se estaba mandando en las peticiones que terminaban ganando (WEB_CREATOR) — era el único de los clientes usados que no tenía esto activado en el código, a diferencia de los demás. Ya se activó. No puedo garantizar al 100% que esto sea todo, pero es la primera causa respaldada por datos reales de tu teléfono, no una suposición.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
