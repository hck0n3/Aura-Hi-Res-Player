# Aura Hi-Res Player v5.4.3

## Arreglo definitivo: online muerto tras restaurar respaldo
- Al restaurar un respaldo se restauraba TODO, y si el respaldo traía un **proxy activado** (con dirección ya muerta), TODA la conexión a YouTube quedaba bloqueada → búsqueda, sugerencias y álbumes fallaban como si no hubiera internet.
- Ahora, al restaurar, se **desactiva cualquier proxy restaurado** y se **renueva el token de sesión** de YouTube, así lo online vuelve a funcionar.

## Importante
- Si ya restauraste un respaldo y sigue fallando: actualiza a esta versión y **vuelve a restaurar el respaldo una vez** (la limpieza se aplica), o entra a Ajustes y revisa que el **proxy esté desactivado**.

## Incluye lo anterior
- v5.4.2: graves/agudos limpios, widget anterior/siguiente, login reinicia fiable.
