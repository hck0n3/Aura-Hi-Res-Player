# Aura Hi-Res Player 0.6.50 — 3 arreglos: letras, reproducción infinita y "similar no suena"

## Letras: animación suave de nuevo 🎤
La animación palabra-por-palabra (estilo karaoke) se había vuelto a saltos. **Causa:** en la auditoría bajé el ritmo del contador de posición del reproductor (de 10 a 2 veces/seg para gastar menos CPU), y las letras se sincronizaban con ESE contador → saltaban cada 0,5 s. **Arreglado:** las letras ahora usan su propio reloj de alta frecuencia (la posición real, ~8 ms) → el resaltado vuelve a barrer **suave y continuo**. (El reproductor sigue gastando menos CPU; lo mejor de ambos.)

## Reproducción infinita predictiva — SIEMPRE activa ♾️
Cuando una cola terminaba de verdad (último tema), la música a veces **se detenía**. **Causa:** la continuación solo se intentaba al *cambiar* de pista, pero el final real de la cola no dispara ese evento (el reproductor pasa a "terminado" sin transición). **Arreglado:** ahora hay una **red de seguridad** que, al terminar **cualquier** cola finita (álbum, lista, artista, una sola canción), arranca una **radio de temas similares y reanuda sola** — **siempre activa**, sin depender de ningún ajuste. Si justo empezaste otra cosa, no te interrumpe.

## "Contenido similar" que no sonaba en otros dispositivos 🌎
En algunas regiones/cuentas (donde YouTube entrega audio con doblaje automático), las canciones de la radio/similares **no reproducían** ("no se encontró formato"). **Causa:** el selector de audio exigía la pista "original" sin etiqueta, que en esas regiones no existe. **Arreglado:** ahora hay un **plan B** (original → no-doblada → cualquiera) para que **siempre** encuentre audio y suene. En tu dispositivo no cambia nada (sigue eligiendo el original de máxima calidad).

## Calidad
Diagnóstico de causa raíz por agentes + revisión adversarial (letras y formato verificados correctos; se cazó y corrigió una fuga menor del flag de reanudación).

## 👉 Para probar
- Letras de una canción → el resaltado debe fluir suave.
- Deja terminar un álbum/lista/una canción → debe seguir solo con similares.
