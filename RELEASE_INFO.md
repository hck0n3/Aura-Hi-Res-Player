# Aura Hi-Res Player 0.6.137 — Playlists que no vuelven, discografías completas y por fin sabremos por qué se cierra

## 🗑️ Las playlists de YouTube borradas ya no reaparecen
Al quitar una playlist sincronizada, su ficha **no desaparecía del todo** pero la sincronización solo miraba las playlists guardadas: no la encontraba, concluía "esta no la tengo" y **la volvía a crear**, duplicada. Tu decisión se deshacía sola en cada sincronización.
- La sincronización ahora mira **todas** las fichas y **respeta lo que quitaste**.
- Borrarla deja constancia (necesario: en tu cuenta de YouTube sigue existiendo, y sin ese rastro no hay forma de distinguir "la borré" de "nunca la tuve"). Sus canciones se conservan, así que si la vuelves a guardar desde YouTube regresa **completa**.
- Los duplicados que ya tenías se limpian solos: existía una función para eso que **nadie llamaba nunca**, y además usaba la misma consulta defectuosa. Ahora conserva la copia **con las canciones**.
- Si eliges "Eliminar también de YouTube", el borrado es definitivo en ambos lados.

⚠️ La primera vez tras actualizar quizá tengas que borrar una vez más las que ya se te duplicaron: el arreglo impide que vuelva a pasar, pero no puede adivinar cuáles querías fuera.

## 💿 Discografías completas otra vez
Dos causas distintas, las dos corregidas:
1. **Artistas que abrían en modo local**: si su ficha se creó internamente (pasa cuando una canción llega sin datos de canal), la pantalla mostraba como mucho 6 álbumes de tu biblioteca y **no había forma de volver a la vista de YouTube**. Ahora la app **busca el canal real por nombre**, exige coincidencia exacta, lo guarda para no repetir la búsqueda, y muestra la discografía completa.
2. **El motor que completa la discografía** (el que rescata álbumes vía otras fuentes, incluidas listas de la comunidad) tenía dos fallos: si YouTube listaba una versión **"En Vivo"**, el álbum de estudio se daba por presente y **nunca se buscaba**; y cuando sus comprobaciones de red fallaban por saturación, gastaba su presupuesto de búsquedas en álbumes que **ya tenías**. Además el filtro de "álbum completo" usaba el número de pistas de la **edición más larga**, tumbando ediciones estándar legítimas.

## 🔍 Cierres sin explicación: ahora sabremos la causa
Si la app se cierra sin mostrar ningún error, **Android guarda el motivo real** y hasta ahora no lo leíamos.
- Nuevo apartado **Ajustes ▸ Registros ▸ "Cierres del sistema"**: motivo (falta de memoria, ANR, fallo nativo…), memoria en uso al morir y hora. **Incluye los cierres que Android ya tenía guardados**, así que un cierre reciente aparece en cuanto abras esta versión.
- El informe de fallos ahora **escribe una cabecera mínima primero**: si el problema era falta de memoria, el propio informe se quedaba sin memoria al generarse y **no dejaba ningún rastro**.

## 🧠 Menos memoria (tres fugas reales)
- La portada se decodificaba **a tamaño completo (~6 MB por canción)** solo para sacar el color del tema; ahora se pide en miniatura.
- La app **ignoraba los avisos de memoria crítica** del sistema en móviles capaces — el paso siguiente del sistema es cerrar la app.
- El widget creaba un mapa de bits nuevo **cada segundo** sin reutilizarlo (~21 MB por minuto).

## Recordatorio
- Cortes a mitad de canción (Lossless) o transiciones raras: **Ajustes ▸ Registros** → compartir → mandar.
