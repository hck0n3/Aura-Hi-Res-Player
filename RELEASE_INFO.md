# Aura Hi-Res Player 0.6.52 — reproducción infinita a prueba de balas + PEQ que se arrastra bien

## ♾️ La cola ahora es DE VERDAD infinita
Antes podía pararse al terminar la última canción. Rastreé todas las causas y las cerré:
- **Si la radio de similares sale vacía o falla la red** justo al acabar: ahora prueba "relacionados", **espera y reintenta**, y como **último recurso re-reproduce tu cola** — nunca te deja en silencio.
- **Modo aleatorio (shuffle):** corregido — antes podía no continuar al final de una cola mezclada.
- **Carrera del "head-start":** si la siguiente tanda aún se descargaba cuando terminó la canción, ahora **reanuda igual**.
- Sigue siendo **siempre activa** (no depende de ningún ajuste) y **predictiva** (radio de la última canción + re-ordenada por tu gusto).

## 🎚️ EQ paramétrico: arrastrar puntos por fin va fino
- **Arreglado el toque:** los nodos costaba moverlos y no dejaba cambiar de uno a otro. Era un conflicto entre dos detectores de gestos. Ahora es **un solo gesto**: tocas un punto y **lo agarras al instante** (sin "zona muerta"), y puedes saltar de un nodo a otro sin pelear con la pantalla. Área de toque un poco más grande.
- **Botón "Restablecer":** vuelve el paramétrico a su curva plana (6 bandas) de un toque.

## Calidad
Cambios del motor de reproducción con **revisión adversarial** (se cazó y corrigió una regresión real en modo aleatorio antes de subir). El arreglo del toque del EQ pasó revisión con **0 problemas**.

## 👉 Para probar
- Deja terminar un álbum/lista/una sola canción (también en **aleatorio**) → debe seguir solo, siempre.
- EQ → **Paramétrico** → agarra y mueve los puntos, salta entre ellos, y prueba **Restablecer**.
