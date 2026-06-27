# Aura Hi-Res Player 0.6.61 — toda la cadena de audio ahora en 32-bit flotante (de verdad Hi-Res)

## 🎚️ El gran cambio: procesamiento interno en 32-bit flotante
Hasta ahora, cada etapa de audio (mejora, normalización, EQ, DSP, limitador) **bajaba la señal a 16 bits entre una y otra** — hasta 5 veces seguidas, acumulando ruido de cuantización. **Eso se acabó.**

Ahora la señal se convierte a **32-bit flotante una sola vez al inicio**, **toda la cadena calcula en flotante** (EQ, compresor, limitador), y **solo se vuelve a 16 bits UNA vez al final** (con dither), porque es lo que entrega la salida del teléfono/Bluetooth.

**Resultado:** sin las re-cuantizaciones intermedias → **menos ruido, EQ y limitador más limpios, voces más naturales**, de punta a punta. Es la base para sonar a nivel TIDAL limpio (subir el nivel viene en un paso aparte, ya con la base flotante).

## 🔒 Seguro por diseño
- La salida al altavoz/DAC **sigue siendo igual** (16-bit con dither) → ningún dispositivo deja de sonar.
- Si un formato no encaja (5.1, etc.), las etapas **se omiten con elegancia** (suena sin DSP, no falla).
- Revisión adversarial multi-agente: **0 problemas** (matemática de buffers, paths verificados).

## 👉 Para probar (importante — dime si algo suena raro)
- Escucha varias canciones (un master fuerte, una acústica suave, una con mucho bajo) → debe sonar **igual o más limpio**, sin estática.
- Prueba el **EQ** (sube bandas), los **efectos** (firma/JR DSP), **normalización** on/off, y **mejorar calidad** on/off → todo debe funcionar.
- Una balada con **fundido/silencio** → cola limpia.
- Si oyes **estática/ruido**, EQ que no hace nada, o un salto raro → **avísame y lo revierto al instante**.
