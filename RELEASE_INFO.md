# Aura Hi-Res Player 0.6.9

## Video: buscar en AMBAS listas de formatos 🎥
`findFormat` para video ahora revisa **a la vez** los formatos adaptativos (`adaptiveFormats`) **y** los combinados/muxed (`formats`). Así, si el cliente entrega el video por cualquiera de las dos vías (p. ej. TVHTML5 con itag 18/22 muxed), lo encuentra y arranca el video de inmediato — en vez de fallar con "no disponible".

Se mantiene el **toast diagnóstico**: si aún fallara, mostrará el motivo exacto para afinarlo.

### Cómo probarlo
Canción **con videoclip** → botón **video** → debe verse el video (sin portada/canvas). Si sale "Video falló — …", mándame ese texto.

## Técnico
- `findFormat(preferVideo)`: `allFormats = adaptiveFormats + formats`, filtra video (no av01, avc1 ≤720p preferido). Cubre tanto el merge (video-only adaptativo) como muxed directo.
- Sigue activo: cliente de video TVHTML5, recreación del media source por URI `#video`, portada/canvas ocultos en modo video, y `videoStreamUrlDiag` con motivo en el toast.
