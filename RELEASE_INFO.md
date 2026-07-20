# Aura Hi-Res Player 0.6.110 — El aleatorio ya se queda encendido, y la música vuelve a sonar

## 🔴 Lo más importante: por qué muchas canciones decían "no disponible"
- **YouTube cambió su reproductor** y Aura ya no sabía descifrar los enlaces. Sin eso, **ninguna canción resuelve**.
- Los tres mensajes que veías —"no está disponible", "tardó demasiado en resolverse" e "inicia sesión para confirmar que no eres un bot"— **eran el mismo fallo**, no tres problemas distintos.
- **Ya está corregido y no hacía falta actualizar la app**: Aura se auto-repara descargando la nueva configuración. Si la música volvió a sonar antes de instalar esta versión, fue por eso.

## 🔀 El aleatorio ya se queda encendido
- Lo que fallaba **no era el azar**, sino que el modo aleatorio **no podía mantenerse activo**: se apagaba solo al empezar cualquier lista, **y también cada vez que abrías la app**.
- Peor: ese apagado automático **borraba tu preferencia guardada**, así que "recordar aleatorio" no servía absolutamente de nada.
- Ahora el aleatorio se recuerda **por cola**: si estabas escuchando una lista en orden, vuelve en orden; si la tenías en aleatorio, vuelve en aleatorio.
- **Ojo:** el botón de aleatorio está en la **pantalla de la cola** (desliza hacia arriba desde el reproductor), no en el reproductor principal.

## 💾 Ya no se borran canciones de la cola
- Empezar una radio a mitad de una cola en aleatorio **podía borrar decenas de canciones que aún no habías escuchado**. Corregido.

## 🔋 Menos consumo con el aleatorio activo
- Con el aleatorio encendido, la app cargaba más canciones **antes de tiempo y repetidamente** porque medía mal cuánto quedaba por sonar. Ahora lo mide en el orden real de reproducción.

## 🧹 Arranque más limpio
- Al abrir la app fallaban tres tareas en segundo plano (novedades semanales, Last.fm y buscar actualizaciones) en procesos secundarios. Ya no.

## ⚠️ Todavía no arreglado
- **Si tu sesión de YouTube caduca**, la app no sabe recuperarse sola: hay que cerrar sesión y volver a entrar. El arreglo está identificado pero toca el núcleo de reproducción y **prefiero probarlo antes que arriesgarme**.
- **Letras:** uno de los proveedores está bloqueando a Aura. Las letras siguen funcionando (hay 7 proveedores más), pero se reintenta más de lo necesario.
- **Cortes de milisegundos** que adelantan la canción: causa probable identificada, sin confirmar.
