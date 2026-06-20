# Aura Hi-Res Player v5.7.68

## Seguridad y robustez (auditoría)
- La **sesión de inicio** ya no se incluye en las copias de seguridad de Android (no se puede extraer por backup).
- **Tráfico en claro desactivado** en toda la app (todo va por HTTPS/WSS).
- El **actualizador verifica la firma** del APK antes de instalar (rechaza un archivo manipulado).
- Menos micro-tirones: se quitaron lecturas de disco bloqueantes en la reproducción.
- Mejor diagnóstico: varios errores que se tragaban ahora se registran.

## Compartir registro de log
- Al **compartir el log** ahora se envía como **archivo de texto adjunto** (antes algunas apps pegaban todo el texto en el mensaje).

## Pantalla completa
- El interruptor **"ocultar la barra de estado en pantalla completa"** viene **activado por defecto**.
