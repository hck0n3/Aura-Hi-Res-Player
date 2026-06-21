# Aura Hi-Res Player v5.7.114

## Reproducción aún más rápida al elegir una canción
- Al tocar una canción nueva, la app hacía DOS peticiones de red una tras otra (el audio y luego los datos). Ahora van EN PARALELO, así que el inicio tarda menos.
- (La precarga de la SIGUIENTE canción ya existía: al dejar que avance sola, la siguiente arranca casi al instante. Lo que se aceleró ahora es cuando TÚ eliges una canción manualmente.)

## Lo que se hizo (técnico, como lo haría un reproductor optimizado)
- Petición principal (audio, cliente ANDROID_VR sin poToken) + petición de metadata: ahora simultáneas.
- La metadata sigue con límite de tiempo para no bloquear nunca el inicio.
