# Aura Hi-Res Player 0.6.140-beta13 — El aleatorio, de verdad + 125 MB menos (BETA PRIVADA)

> ⚠️ Build de PRUEBA, solo para el dueño. Prerelease: el actualizador NO la ofrece a nadie más.

## 🔴 La repetición que oíste en beta12: era culpa mía

En beta12 moví una pieza para cerrar otro fallo, y abrí uno peor: **la canción con la que arrancas una lista era la única de toda la sesión que no quedaba registrada**. Las siguientes sí. Al día siguiente activabas el aleatorio y **esa** volvía a sonar.

Y como es siempre la canción **que tú eliges** para empezar, las que se repetían eran justo tus favoritas. Por eso lo notaste enseguida.

Arreglado en dos frentes: ahora se registra en la lista correcta, y esa misma transición ya no puede anotar la canción nueva **en el bucket de la lista anterior** (ensuciando una lista que ni estabas oyendo).

## 🔀 Tu regla, aplicada

- Lo que ya sonó **no se repite** — lo hayas escuchado en aleatorio o en orden normal.
- El conteo **solo se reinicia** cuando la lista se ha terminado **y** tú vuelves a activar el aleatorio.
- La app **termina la lista** antes de continuar con la cola inteligente.
- **Android Auto**: corregido un fallo que podía borrar el conteo **entero** de una lista (por ejemplo, todo *Me gusta*) — pasaba justo en tu rutina: el móvil mata la app de noche y por la mañana entras al coche.

## 🔍 Y para que la próxima vez no haya dudas

Cada canción que arranca deja una línea en el registro con: si **ya estaba marcada como escuchada**, en qué **modo**, de qué **lista**, cuántas llevas, y si la **radio inteligente** ya había entrado. Si vuelves a oír una repetida, me mandas el registro y se ve en un renglón.

## 💿 Discografías: tres causas, no una

- **Al hacer scroll se borraban los álbumes recuperados.** La paginación guardaba la lista antes de pedir la página siguiente y la reescribía encima. Reaparecían al salir y entrar, y se perdían otra vez al siguiente scroll.
- **Veías el álbum completo y la playlist truncada a la vez** para el mismo disco. Tu frase *"la playlist que mete el disco está incompleta"*: no estaba incompleta — **sobraba**.
- **Una discografía cargada con mala conexión se quedaba así para siempre.** Ahora se repara sola en segundo plano, y solo publica el resultado si es **mejor** que lo que ya tenías: un reintento peor no puede quitarte nada.

## 📥 Migrar tu biblioteca completa (no solo playlists)

**Tidal** y **Deezer** ahora traen tu biblioteca entera: **canciones favoritas**, **álbumes guardados** y **artistas seguidos**, además de tus playlists. Y van a su sitio: las favoritas se marcan como **Me gusta**, álbumes y artistas van a **Biblioteca**.

En Deezer se hace pegando la URL de tu perfil (debe ser **público**), porque Deezer cerró el registro de aplicaciones y no hay inicio de sesión posible.

## 🔊 Volumen seguro y preamp

Al **desactivar** el Volumen Seguro, el preamp del ecualizador vuelve a **0.0 dB**. El preamp se aplica **después** del limitador: mientras el Volumen Seguro está activo, el limitador recoge lo que empuje de más; al apagarlo esa red desaparece y el refuerzo se queda, así que el siguiente máster fuerte distorsionaba.

Solo ocurre en el momento en que lo apagas, no cada vez que abres la app, y **no toca tu preset guardado**.

## 📦 125,4 MB menos

La app empaquetaba **Python, ffmpeg y aria2c** para las cuatro arquitecturas — una librería de descarga que **ninguna línea del código usa**. Al ser binarios, el optimizador que borra código muerto no los tocaba: viajaban enteros en cada instalación.

También fuera: un **descargador de actualizaciones de repuesto que no verificaba la firma** del APK (el que funciona de verdad sí la verifica y aborta si no cuadra), una pantalla completa que no era accesible desde ningún sitio, y un procesador de audio vacío que suplantaba por nombre al real.

---

## Lo que NO va en esta build (a propósito)
- La reorganización de los ajustes. Mover cosas de sitio exige actualizar el índice del buscador en el mismo cambio, y no quiero mezclarlo con arreglos de reproducción.
- El bloqueo anti-arranque-fantasma sigue **desactivado** a la espera de que lo pruebes en el coche.

---
Compila en ambos sabores.
