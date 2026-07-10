# Aura Hi-Res Player 0.6.92 — La portada vuelve de verdad + Modo Ambiente, scrobbling y más

## 🖼️ Portada y carrusel del reproductor — arreglado de raíz
- **La portada ya no desaparece.** Se encontró la causa real: la portada (y el carrusel para deslizar entre canciones) se dibujaba dentro de un contenedor que colapsaba a tamaño 0 en reproducción normal, por eso desaparecía en muchos dispositivos (a veces sí, a veces no). Ahora se muestra siempre, con cualquier estilo de fondo.

## 🎠 Carrusel de Inicio ("Para ti") — ya no parpadea vacío
- El carrusel de "Para ti" a veces salía en blanco porque la lista cambiaba de tamaño justo tras cargar. Ahora se reconstruye limpio y aparece a la primera. *(Si eres usuario nuevo sin historial, "Para ti" tarda en llenarse: se genera a partir de lo que escuchas.)*

## 🎛️ Botones flotantes
- Se quitó el botón **aleatorio** de los botones flotantes. Queda solo el **micrófono** (reconocimiento de canciones) con estilo Liquid Glass.

## 🌌 Modo Ambiente (nuevo)
- Nueva vista a **pantalla completa** horizontal: portada grande con un **resplandor animado** de fondo, letra, y pantalla siempre encendida. Ideal para dejar el teléfono a la vista mientras suena la música. Ábrelo desde el menú del reproductor.

## 🎧 Scrobbling: Last.fm + ListenBrainz (nuevo, opcional)
- Ahora puedes **registrar lo que escuchas** en **Last.fm** y **ListenBrainz**. Es totalmente **opcional**: apagado por defecto y sin actividad alguna hasta que conectas tu cuenta. Actívalo en Ajustes ▸ Scrobbling.

## 🔧 Descifrado de YouTube (nuevo, para soporte)
- Nueva pantalla en **Ajustes ▸ Reproductor ▸ Avanzado**: muestra cuándo se actualizó el descifrado de YouTube y un botón para **forzar la actualización** si alguna vez la reproducción deja de funcionar. (Aura ya se auto-repara solo; esto es por si acaso.)

## 📊 Resumen de escucha (nuevo)
- En **Estadísticas**, un nuevo resumen: **tiempo total escuchado** del periodo y de siempre, y cuántas canciones, artistas y álbumes distintos.

## 🎤 Letra estilo Metro
- El estilo **Metro** ahora respeta tu **tamaño de letra e interlineado** (antes los ignoraba).

## ✅ Como siempre
- Suscripción, licencia, demo, crossfade de 9s y motor de audio: intactos. Calor y batería vigilados (el scrobbling no hace nada si no lo activas).
