# Aura Hi-Res Player v5.7.26

## Arreglo: la app se cerraba al iniciar sesión
- Después de iniciar sesión en Google, la app **debía reiniciarse** para aplicar la sesión, pero en algunos equipos (Xiaomi/ahorro de batería) **solo se cerraba** y no volvía a abrir. Ahora usa un reinicio confiable → vuelve a abrir ya con la sesión aplicada.
- Esto además arregla que, ya con sesión iniciada, **no cargaran las sugerencias ni el apartado "Álbumes"** (era porque la sesión quedaba aplicada a medias al no reiniciar bien).

## Incluye lo anterior
- v5.7.25: volumen más fuerte. v5.7.24: arreglo de cierre al descargar la actualización.
