# Aura Hi-Res Player v5.7.101

## Canvas: se acabó la carga infinita
- Antes, si un proveedor de canvas se quedaba colgado, el álbum/canción se quedaba "cargando" para siempre (había que entrar y salir varias veces).
- Ahora la búsqueda de canvas tiene un **límite de tiempo (8s)**: si no encuentra nada, deja de cargar limpiamente y, además, ahora alcanza de forma fiable el proveedor de respaldo (Tidal) en vez de quedarse atascado en uno que falla.
- Aplica tanto a los canvas de álbum como a los de canción.
