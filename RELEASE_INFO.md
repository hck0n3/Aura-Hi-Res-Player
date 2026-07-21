# Aura Hi-Res Player 0.6.117 — Aleatorio Mejorado (memoria que nunca repite)

## 🔀 Nuevo: Aleatorio Mejorado
El modo aleatorio ahora tiene **memoria de verdad**, por lista:

- **Nunca repite** una canción hasta que suenen todas las de esa lista.
- **Recuerda entre días:** si vuelves a una playlist (o a toda tu biblioteca) mañana o la semana que viene, sigue por donde ibas y no te repite lo que ya oíste ahí.
- **Recuerda al apagar y encender:** si desactivas el aleatorio y lo vuelves a activar, no olvida qué canciones ya sonaron.
- **Revuelve de verdad en cada toque:** cada vez que pulsas el botón, vuelve a barajar lo que aún NO ha sonado y continúa desde ahí — no una y otra vez el mismo orden.
- **Al terminar la lista, la música sigue sola:** cuando ya no queda nada nuevo que barajar, la reproducción continúa con la **radio infinita inteligente** (ya no aleatoria) — como cuando se acaba una cola normal.

Funciona por **cada playlist**, por las listas automáticas (Me gusta, Descargadas, Subidas, Exportadas) y por **toda la biblioteca**. Cada una lleva su propia memoria por separado.

**Viene activado por defecto.** Puedes apagarlo cuando quieras en **Ajustes ▸ Reproductor ▸ "Aleatorio mejorado"** para volver al aleatorio de siempre.

## 🔧 Por dentro
- La memoria se guarda en la base de datos de la app (migración sin pérdida; tu biblioteca queda intacta).
- El traspaso a la radio infinita ocurre justo cuando se agota lo no-oído, para que nunca se repita ni se quede en silencio.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — necesito tu registro en esa calidad.
- **Volumen Seguro** sube máximo +3 dB.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
