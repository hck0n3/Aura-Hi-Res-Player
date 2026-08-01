# Aura Hi-Res Player 0.6.140-beta8 — Reconocedor, Tidal, discografías y Qobuz (BETA PRIVADA)

> ⚠️ **Build de PRUEBA, solo para el dueño.** Sale como *prerelease*: el actualizador NO la ofrece a nadie más. Instálala a mano.

## 🎤 Reconocer canción — por fin reproduce la correcta
Reportaste que reconocía perfecto pero al darle play sonaba **otra canción sin relación**. Eran **dos** fallos encadenados (el primer intento en beta7 solo tapó uno):
1. Usaba un id frágil que devuelve Shazam (a menudo el video musical u otra versión).
2. Al resolver la canción, aceptaba la primera con **título parecido aunque fuera de otro artista** — y como sí devolvía algo, la protección nunca saltaba.

Ahora exige que coincidan **título Y artista**. Si no hay coincidencia fiable, **avisa y no reproduce nada** en vez de sonar cualquier cosa.

## 🎵 Migrar desde Tidal — se acabaron las "0 encontradas"
Tu captura mostraba *0 encontradas / 8 por revisar / 7 no encontradas*. Causa: al pedir las canciones no se pedían los **artistas**, así que **todas** llegaban sin artista al emparejador y ninguna podía alcanzar el mínimo. Corregido (y comprobado: una canción real pasa de 50 a 115 puntos).
- Si aun así todo sale "ambiguo", la lista **se crea al resolver la primera** — ya no aparece "no hay dónde añadir las revisadas".
- Playlists de más de 100 canciones: corregido un fallo que podía cortar la importación o truncarla en silencio.

## 💿 Discografías completas
Cuando un álbum que falta se completa a través de una lista de la comunidad, ahora se trae el **álbum entero**, no solo las pistas que había en esa lista. Además no confunde la edición "En Vivo" con la de estudio, y nunca publica un álbum vacío o recortado.

## 🎧 Qobuz con TU suscripción (nuevo)
Ajustes ▸ Cuentas ▸ **Qobuz**: vincula tu cuenta pegando tu token o con correo y contraseña. Con la cuenta vinculada, las canciones sin pérdida se resuelven contra **tu suscripción** en FLAC hi-res, negociando la mejor calidad disponible (24/192 → 24/96 → 16/44) y mostrando la que realmente llegó.
- **Requiere plan Studio o Sublime** para 24 bits. Con plan básico/gratuito la app lo detecta y **no lo activa**, en vez de fingir hi-res.
- Garantía: un MP3 **nunca** se acepta como hi-res (eso además podía provocar cortes a mitad de canción).
- Credenciales cifradas en el móvil, enviadas solo a qobuz.com. Desactivado por defecto: si no vinculas cuenta, nada cambia.

---
**Calidad**: dos rondas de auditoría adversarial sobre esta tanda encontraron **23 defectos**, todos corregidos antes de publicar — varios de ellos en los propios arreglos, incluidos tres que habrían empeorado justo lo que reportaste. Compila en ambos sabores, 225/225 pruebas (5 nuevas para el emparejador).
