# Aura Hi-Res Player 0.6.58 — dar "Me gusta" YA NO sube ni baja el volumen (de raíz)

## ❤️🔊 Por fin: el corazón no toca el volumen
Dar "Me gusta" subía el volumen y quitarlo lo bajaba un poco. Lo intenté antes pero faltaba **un camino**: la **descarga automática** (que se dispara al dar like) tenía su **propio** código que re-guardaba el formato de la canción **sin conservar** la medida de volumen — así que traía una medida distinta y la app re-nivelaba a mitad de canción.

**Arreglado de raíz (doble candado):**
1. La **descarga ahora conserva** la medida de volumen que ya tenía la canción → una descarga **nunca** cambia el nivel de un tema que ya está sonando.
2. Para temas que empiezan sin medida, el ajuste de nivel solo ocurre **en los primeros segundos**, **nunca a mitad** por un like.

→ Dar o quitar "Me gusta" **no mueve el volumen**. Punto.

Revisado con auditoría adversarial (0 problemas).

## 👉 Para probar
- A mitad de una canción, da y quita "Me gusta" varias veces → el volumen NO debe moverse.
