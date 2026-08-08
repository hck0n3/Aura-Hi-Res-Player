# Aura Hi-Res Player 0.6.150

La interfaz nueva Aura llega a todos: activada de fábrica, reversible en un toque. Incluye el trabajo de las betas 0.6.148 y 0.6.149, más Inicio tipado (vídeos 16:9), playlists fijadas, cristal en diálogos, y mitigaciones reales contra cortes en Android Auto.

---

## 🎛️ Interfaz nueva — ON, y siempre con salida

- **Viene activada** en instalaciones nuevas y en actualizaciones donde nunca la tocaste.
- Si la apagaste a mano, **se respeta**: no te la volvemos a encender.
- **Ajustes ▸ abajo ▸ «Interfaz nueva»** (también en Ajustes de la interfaz nueva). Apágala y vuelves a la clásica al instante.
- El interruptor **está en las dos interfaces**, así que nunca te quedas atrapado.
- Apagarla **no toca** tu biblioteca, ajustes, cola ni reproducción.
- **Con la interfaz nueva la app es solo oscura.**

## 🏠 Inicio, listas y vídeos

- **Fijar playlist al inicio** sustituye la antigua marcación rápida: tus listas ancladas aparecen en Inicio.
- Carruseles y resultados distinguen **canción / álbum / EP / playlist / vídeo 16:9 / artista** — no un solo tamaño genérico.
- Estantes mezclados (p. ej. Similares) se **agrupan por tipo** para evitar huecos enormes entre tarjetas.
- Listas de vídeo: badge claro y miniaturas 16:9.
- Se ve **cuáles canciones ya se reprodujeron** (marca de estado).
- «Recomendado para ti (IA)» solo aparece si lo tienes **activado** en Ajustes.

## ▶️ Reproductor y menús

- Fila inferior del reproductor: **Más / Ajustes** abre el menú completo (no solo los tres puntos de arriba).
- **Cast** sigue arriba a la derecha.
- Mini-reproductor por defecto: **Brillo animado**.
- Temporizador de apagado: **eliminado** (botón y motor).
- Diálogos, hojas y menús ⋮ con placa **cristal esmerilado** (se ve lo de atrás).
- Cola: la canción actual solo en **SONANDO** (sin duplicarla debajo).
- Indicador de reproducción: barras animadas; glifo play mientras bufferiza.

## 👨‍🎨 Artista, álbum y playlists

- En la página de artista de YouTube: sección **Tu biblioteca** visible.
- Tocar una canción de biblioteca o top encola el resto de esa lista.
- Grids de álbumes favoritos / «ver todos» con piel Aura.
- En playlists largas: **Buscar** y **Más** en la fila inferior; Play/Aleatorio arriba.

## 🚗 Android Auto y reproducción en segundo plano

- Con **Android Auto conectado** o **Ahorro de batería del sistema ON**, el fundido (crossfade) se desactiva solo: evita silencios de ~4 s al cambiar de canción.
- Bajo ahorro de batería se sigue precargando la **URL** de la siguiente canción (sin extras pesados).
- Keep-alive no se suelta en pausas cortas de Auto/Bluetooth.
- Si el Ahorro de batería está encendido al reproducir, el aviso te lo dice claro: **apágalo mientras escuchas en el coche**.
- Exención de batería + inicio automático siguen siendo necesarios en Xiaomi/HyperOS.
- **No prometemos cero cortes** en todos los móviles: HyperOS puede matar el proceso; esto ataca las causas que ya vimos en registros reales.

## 💥 Fallos graves ya cerrados (desde 0.6.148 / 0.6.149)

- La app **se cerraba al entrar en Buscar** (claves duplicadas en «Estados de ánimo»).
- La pestaña Biblioteca **no mostraba nada y abría Ajustes** (pila de navegación envenenada); también se cerraron puertas gemelas, incluida una que rompía Ajustes — justo donde vive el interruptor para volver atrás.
- Pantallas y diálogos en **blanco chillón** con la interfaz nueva: ahora manda su paleta oscura al tema de la app.
- Tus **colores de acento** llegan a la interfaz nueva (antes un turquesa fijo).
- Estilos de fondo del reproductor / mini / AMOLED / radio de esquina / tamaño de celda honrados en el diseño nuevo.
- **Desenfoque** ya es distinto de Predeterminado; Liquid Glass no desaparece de la lista.

## 📱 Pantallas Aura y detalle

- Rehechas o ampliadas: Inicio, Biblioteca, Buscar, Estadísticas, álbum, artista, listas (online/local), auto-listas, letras, Ajustes, reproductor, cola y menú.
- Forma ancha (horizontal / tablet / TV / coche) propia; rotar ya no destruye la pantalla.
- Inicio de sesión YTM: WebView **dentro** de la app; al terminar no reinicia el proceso.
- Música local: filtro de carpetas = **incluir** las elegidas (vacío = todo el dispositivo).

## ⚠️ Lo que conviene saber

- Algunas rutas secundarias siguen con disposición clásica (oscuras y coherentes).
- La interfaz nueva es **solo oscura**.
- En Android 8–11 el desenfoque del sistema no existe: algunos estilos de fondo pierden la portada.
- En el coche: apaga el **Ahorro de batería del sistema**, deja Aura sin restricciones e iníciola automática; bloquea Aura en recientes.
