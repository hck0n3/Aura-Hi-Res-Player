# Aura Hi-Res Player 0.6.54 — ARREGLO URGENTE de audio: sin saltos de volumen ni distorsión al dar "Me gusta"

## 🔊 El audio vuelve a sonar limpio
En 0.6.53, dar "Me gusta" (o quitarlo) **subía el volumen de golpe** y metía **saturación / voz rasposa**. 

**Causa:** al arreglar el "Me gusta", la **descarga automática al dar like** volvió a funcionar — y esa descarga re-guardaba el formato de la canción, lo que **re-disparaba la normalización de volumen a mitad de canción**. Eso reaplicaba la ganancia de golpe (salto de volumen) y empujaba el limitador a tope (saturación/voz rasposa). Es exactamente el artefacto que ya se conocía de la "medición de loudness por descarga".

**Arreglado (doble candado):**
1. La normalización **solo se recalcula cuando cambia la loudness real** de la canción — ya no en cada re-guardado del formato. Dar/quitar like no la vuelve a tocar.
2. La corrección de loudness real **solo puede aplicarse en los primeros segundos** de la pista — nunca a mitad de canción.

→ Dar "Me gusta" ya **no toca el volumen ni el sonido**. El audio se mantiene limpio y al nivel correcto.

## 👉 Para probar
- Reproduce una canción, espera a mitad, da **"Me gusta"** y **quítalo** varias veces → el volumen NO debe moverse y NO debe distorsionar.
- El volumen ya no debe subir "de la nada".
