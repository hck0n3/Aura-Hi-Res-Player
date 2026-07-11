# Aura Hi-Res Player 0.6.97 — Restaurar copias ya no cierra la app

## 🛡️ Restaurar una copia de seguridad es a prueba de crash
- Corregido un **cierre inesperado** (`connection is closed`) que podía ocurrir **al restaurar una copia de seguridad**. Al restaurar, la app cierra la base de datos un instante antes de reiniciarse; en ese momento otra parte de la app (el reproductor) podía consultarla y provocar el fallo. Ahora ese caso se maneja como lo que es —benigno, la app se está reiniciando— y **la restauración termina limpia, sin cerrarse**.
- Recordatorio: las copias guardan **solo tu biblioteca** y el restore **ignora el inicio de sesión** (también en copias viejas), así que restaurar siempre es funcional.

## ✅ Como siempre
- Suscripción, licencia, demo, crossfade de 9s y motor de audio: intactos. Calor y batería vigilados. Verificado contra el registro de fallos: sin regresiones.
