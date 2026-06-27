# Aura Hi-Res Player 0.6.59 — sonido más limpio (dither), sin crashear con 5.1, y el EQ siempre suena

Tomé un informe técnico de audio, **verifiqué cada punto contra el código** (varias afirmaciones del informe eran falsas) y apliqué solo lo confirmado y seguro:

## 🎧 Sonido más limpio en silencios y colas (dither TPDF)
La cadena de audio termina en 16 bits. Antes, esa última conversión **truncaba** (distorsión audible en fundidos, silencios y colas de reverb). Ahora aplica **dither TPDF** en la etapa final → la distorsión de truncado se convierte en un ruido **inaudible** (~−93 dBFS). Es la parte audible de "procesar en flotante", sin el riesgo de reescribir todo el motor. **Más limpio, sobre todo en pasajes suaves.**

## 🔇 El ecualizador ya NO se queda mudo con "offload"
El ahorro de batería por *offload* del sistema **se salta toda la cadena DSP**. Antes solo se desactivaba con crossfade/normalización; ahora se desactiva **siempre que haya cualquier efecto activo** (EQ, firma Aura, JR DSP). Así tu EQ/efectos **siempre suenan**.

## 🔊 Archivos 5.1 / multicanal ya no rompen la reproducción
Un FLAC 5.1 (o multicanal) hacía **fallar la reproducción** con el DSP activo. Ahora los procesadores **se omiten con elegancia** para >2 canales → la canción suena (sin DSP) en vez de fallar.

## Calidad
Verificación multi-agente del informe + **revisión adversarial** del cambio (0 problemas; stereo intacto).

> Pendiente (gran premio, más adelante con cuidado): cadena de audio **end-to-end en 32-bit float** y modo **bit-perfect a DAC USB**.

## 👉 Para probar
- Una balada con fundidos/silencios → cola más limpia.
- EQ + (si lo tenías) offload → el EQ ahora sí se nota.
