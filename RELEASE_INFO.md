# Aura Hi-Res Player 0.6.53 — "Me gusta" arreglado, letras sin lag, radio infinita de verdad

## ❤️ "Me gusta" por fin funciona
Dar like a una canción que aún no estaba en tu biblioteca (búsqueda, exploración, radio) **no guardaba nada** y el corazón volvía atrás. **Dos causas:** (1) usaba una escritura que solo actualiza filas existentes → 0 filas → like perdido; ahora **inserta-o-actualiza** (upsert). (2) La descarga automática al dar like lanzaba un error en segundo plano que **abortaba el guardado del like** → ahora va protegida y nunca rompe el like.

## 🎤 Letras: el resaltado ya no va con retraso
El relleno palabra-por-palabra pasaba por una animación de 150 ms **encima** de la posición que ya se actualiza cada ~8 ms → arrastraba ~150 ms detrás de la música. **Quitado:** ahora el resaltado sigue el audio **al instante**, suave (con borde suavizado).

## ♾️ Radio infinita — de verdad y basada en lo último que escuchaste
- **Ya no tiene límite:** la vía de "relacionados" añadía un lote finito y dejaba morir la paginación; ahora reengancha una **radio que sigue cargando sola** sin fin.
- **Se basa en la última canción:** antes re-ordenaba toda la radio por tu gusto general → se sentía desconectada. Ahora **manda la relación con la última canción** (orden de YouTube) y el gusto solo **empuja** unos puestos → la continuación suena como radio de lo que venías escuchando.

## ✅ Verificado
Los controles de **Tono (Graves/Agudos)** que se quitaron están **100% fuera** (0 referencias). Diagnóstico multi-agente + revisión adversarial; la paginación de la radio se cazó y completó antes de subir.

## 👉 Para probar
- Da "Me gusta" a una canción de búsqueda/radio → debe quedar marcada y persistir.
- Letras → resaltado suave y **sincronizado**.
- Deja terminar la cola → sigue infinito y **relacionado** con lo último.
