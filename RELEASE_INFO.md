# Aura Hi-Res Player 0.6.140

Una actualización grande. Lo importante: el **modo aleatorio ya no repite**, las **discografías salen completas**, puedes **migrar tu biblioteca entera** desde Tidal y Deezer, y la app pesa **125 MB menos**.

---

## 🔀 Modo aleatorio: ahora sí, no repite

Era el fallo más reportado y tenía **varias causas distintas**, no una:

- **La canción con la que arrancas una lista no quedaba registrada.** Era la única de toda la sesión que se perdía: las siguientes sí se anotaban. Y como es la canción que tú eliges, las que volvían a sonar eran justo tus favoritas.
- **La última canción sin escuchar se iba al fondo.** Al cerrar cada vuelta, cuando quedaba una sola por oír, el sistema la colocaba primera y el paso siguiente la enterraba — y sonaban repetidas hasta terminar el ciclo.
- **Al reabrir la app, el aleatorio arrancaba sin memoria.** Tu historial seguía guardado; simplemente no se leía.
- **"⋯ → Aleatorio" en álbumes y artistas no encendía el aleatorio**: solo desordenaba la lista una vez, sin sistema anti-repetición.
- **Las canciones de la radio infinita no contaban** como escuchadas.

**Cómo funciona ahora**: lo que ya sonó no se repite —lo hayas escuchado en aleatorio o en orden normal—, y el conteo **solo se reinicia** cuando terminas la lista **y** vuelves a activar el aleatorio. La app termina la lista antes de continuar con la cola inteligente.

**Memoria entre días donde antes no había ninguna**: "Mi Top" (separada por periodo), "Caché", **álbumes** y **artistas**. Y "Me gusta", "Descargadas", "Subidas" y "Exportadas" ya no se guardan en dos sitios distintos según entres por Biblioteca o por la tarjeta.

**Android Auto**: lo que reproduces en el coche ya se apunta en la lista correcta —antes se anotaba en la última lista abierta en el móvil— y su botón "Aleatorio" activa el sistema completo.

## 💿 Discografías completas

- **Al hacer scroll se borraban los álbumes recuperados**: la paginación reescribía encima la lista anterior.
- **Aparecían el álbum completo y una versión truncada del mismo disco a la vez.**
- **Una discografía cargada con mala conexión se quedaba incompleta para siempre.** Ahora se repara sola en segundo plano, y solo se queda con el resultado si es **mejor** que el anterior: un reintento peor no puede quitarte nada.

## 📥 Migrar tu biblioteca completa

**Tidal** y **Deezer** ahora traen tu biblioteca entera: **canciones favoritas**, **álbumes guardados** y **artistas seguidos**, además de tus playlists. Cada cosa a su sitio: las favoritas se marcan como **Me gusta**, y álbumes y artistas van a **Biblioteca**.

En Tidal se inicia sesión normal. En Deezer se pega la URL de tu perfil, que debe ser **público** (Deezer cerró el registro de aplicaciones, así que no existe inicio de sesión posible).

## 🎵 Reproducción

- **Firma de las peticiones a YouTube corregida**: las dos credenciales de reproducción iban **intercambiadas**, cada petición firmada con la de la otra. Es la causa típica de los errores 403 y de que una canción se corte a la mitad.
- **Menos cierres inesperados**: al arrancar, la app hacía todo su trabajo pesado también en sus procesos auxiliares, incluida la apertura de la caché de música — dos procesos escribiendo el mismo índice.
- **Reconocer canción**: ahora reproduce **exactamente** la canción que te muestra. Antes podía sonar otra sin relación.
- **Alta calidad por JioSaavn más fiable**, con comprobación de título en cualquier idioma y escritura.
- **Auto-reparación de reproducción** ampliada de 5 a 390 configuraciones.

## 🎤 Letras

- **Sincronización corregida**: se había perdido el margen de anticipación de la línea, así que la letra cambiaba justo en el instante en que empezaba a cantarse, sin tiempo de lectura.
- Corregido un desfase extra que afectaba **solo a un estilo** de letras.
- Ya no pueden mostrarse las letras de **otra versión** de la misma canción.

## 🔊 Audio

- Al **desactivar** el Volumen Seguro, el preamp del ecualizador vuelve a **0.0 dB**. El preamp se aplica **después** del limitador: al apagar el limitador y mantener el refuerzo, el siguiente tema fuerte distorsionaba.
- **Cast**: el volumen que cambies en el altavoz o con el mando de la tele se refleja en la app, y ya funciona al reanudar una sesión (antes el deslizador se movía sin efecto).

## 📱 Interfaz

- **Buscar entre tus playlists** en la pestaña de Playlists.
- **Sincronizar una playlist a mano** con tu cuenta de YouTube, desde cada lista.
- Aura abre **más tipos de enlace** de YouTube y YouTube Music.
- Los fondos animados **no se dibujan con el reproductor minimizado**, y respetan el freno térmico.
- **Piso por hardware**: en gama baja los efectos pesados nunca se activan.
- La cola colapsada ya no tapa la parte baja del reproductor en horizontal y tablet.
- La marcación rápida viene **desactivada** por defecto.

## 📦 125 MB menos

La app empaquetaba **Python, ffmpeg y aria2c** para cuatro arquitecturas, de una librería de descarga que **ninguna línea del código usaba**. Al ser binarios, el optimizador que elimina código muerto no los tocaba.

También fuera: un descargador de actualizaciones de repuesto que **no verificaba la firma** del APK (el que se usa de verdad sí la verifica), una pantalla inaccesible y un procesador de audio vacío que suplantaba por nombre al real.

---

Gracias por usar Aura.
