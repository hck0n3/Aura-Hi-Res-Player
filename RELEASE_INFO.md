# Aura Hi-Res Player 0.6.43 — Auditoría de código: estabilidad y rendimiento (parte 7 del plan)

Esta versión no trae funciones nuevas: aplica las correcciones **confirmadas** de una auditoría completa del código (8 subsistemas, con verificación adversarial). Se corrigieron **10 fallos de severidad alta**. No hubo ningún fallo crítico.

## Lo que mejora (invisible pero importante)
- **Menos cuelgues/tirones al reproducir:** la resolución del stream ya no bloquea el hilo principal, y el precargado de la siguiente canción ya no hace lecturas de disco en el hilo de cambio de pista (relacionado con "el audio se traba").
- **EQ sin estallidos:** los coeficientes del ecualizador se publican de forma atómica, así que mover bandas durante la reproducción ya no puede provocar un pico de ruido fuerte.
- **Sincronización correcta:** las consultas de "álbumes subidos" filtraban por el campo de *favoritos* y **corrompían tus álbumes likeados** — corregido. Y "borrar lo sincronizado" ya no carga toda la biblioteca en memoria (riesgo de cierre en equipos con poca RAM).
- **Pantallas que ya no se quedan en blanco:** los parsers de **álbum** e **historial** ya no fallan por completo si YouTube cambia un campo; saltan solo la fila problemática.
- **Inicio más fluido con biblioteca grande:** ya no se carga toda la tabla de reproducciones para leer una sola canción.
- **Cola sin duplicados:** el auto-mix ya no se inserta durante la composición de la UI.
- **Sin fugas:** el servicio cancela sus corrutinas al destruirse.

## Pendiente (para revisar contigo)
- 1 alto estructural (refactor de transacciones) + 7 medios + 18 bajos quedan documentados; los aplico según vayas decidiendo.
