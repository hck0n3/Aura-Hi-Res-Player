# Aura Hi-Res Player v5.7.24

## Arreglo: cierre al descargar la actualización (Android 14+)
- En la versión anterior, al iniciar la descarga de una actualización la app **se cerraba** en algunos equipos nuevos (Android 14/15/16). Causa: el servicio de descarga en primer plano no tenía declarado su tipo ("dataSync") en el sistema. Ya está declarado → la descarga ya **no cierra la app** y corre en segundo plano sin problemas.

## Incluye lo anterior
- v5.7.23: los podcasts recuerdan dónde te quedaste. v5.7.21: descarga reanudable en segundo plano.
