# Aura Hi-Res Player 0.6.140-beta10 — Firma de YouTube corregida + arranque limpio (BETA PRIVADA)

> ⚠️ Build de PRUEBA, solo para el dueño. Prerelease: el actualizador NO la ofrece a nadie más.

Esta build salió de revisar los cambios de Echo 5.2.82–5.2.85 que me mandaste. Su changelog volvió a ser humo en su mayoría, **pero al comparar su código con el nuestro aparecieron tres fallos reales en Aura** que no venían de ellos.

## 🔴 Los dos códigos de firma de YouTube iban cruzados

Aura genera dos credenciales para hablar con YouTube: una va ligada a **la canción** y otra a **tu sesión**. Estaban asignadas **al revés**, así que cada petición iba firmada con la que no era.

Es la causa clásica de los errores 403 y de que una URL de reproducción **deje de valer a mitad de canción**. Puede tener que ver con los cortes que llevamos arrastrando en lossless.

Los dos valores son texto, así que ni el compilador ni ninguna prueba podían detectarlo: solo se ve leyendo qué significa cada uno.

## 🔴 La app hacía todo su arranque también en los procesos auxiliares

Aura tiene dos procesos aparte: uno para la pantalla de error y otro para reiniciarse. **Todo el arranque pesado corría también en ellos**, incluida la apertura de la caché de música — o sea, dos procesos escribiendo el mismo índice de caché justo cuando la app ya estaba fallando. Eso puede corromper descargas y canciones guardadas.

Y de paso gastaba batería y calentaba por nada en procesos que viven dos segundos.

## 🟠 Cast: el volumen no hacía nada al reanudar

Si la sesión se reanudaba (volvías a la app, o el proceso se reiniciaba mientras emitías), **el deslizador se movía y el altavoz seguía igual**. Sin aviso, sin error. Ahora funciona siempre y además arranca en el volumen real del dispositivo.

## 🎵 Alta calidad por JioSaavn: que no suene otra canción

Añadí una comprobación de título para que no suene **una canción distinta** cuando el servidor devuelve algo que no es lo pedido (antes bastaba con que cuadraran duración y artista).

Lo importante: funciona con **cualquier idioma** — español con acentos, hindi, japonés, coreano, ruso, turco. Mi primera versión de esta comprobación **borraba todos los caracteres no latinos** y habría dejado sin alta calidad a medio catálogo; lo cazó la revisión, no la prueba. Cuando no puede juzgar (el título en un alfabeto y la respuesta en otro), se aparta en vez de rechazar.

## Lo demás

- Chromecast más robusto al elegir dispositivo (fallo en Xiaomi).
- Auto-reparación de reproducción: de 5 a **390** configuraciones.
- Playlists sincronizadas: ya no se cuelan "Sugerencias".
- Corregido un texto que nombraba a otra app en la pantalla de importar de Spotify.

## Lo de beta9 sigue igual
Reconocedor, Tidal, discografías, Qobuz, letra sincronizada, enlaces de YouTube, buscar/sincronizar playlists, fluidez por gamas.

---
Compila en ambos sabores, 251/251 pruebas.
