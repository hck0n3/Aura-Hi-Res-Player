# Aura Hi-Res Player 0.6.71 — EQ más limpio (calidad tipo Poweramp) + firma Aura mejorada

## 🎚️ EQ limpio y transparente, sin distorsión a ningún volumen
Auditoría DSP multi-agente: el sonido se "ensuciaba" porque **tres recortadores suaves (tanh)** en cadena (en el EQ, dentro de JrDsp y en el limitador final) entraban a actuar dentro del rango normal de la música → metían armónicos en la señal. Ahora los **corrí fuera del rango normal**:
- **EQ**: transparente en todo el rango normal (solo redondea picos reales, ya no modela transitorios de bandas con Q alto).
- **JrDsp**: su recortador interno solo actúa en sobre-picos genuinos (knee 0.88 → 0.97).
- **Limitador final**: knee 0.90 → 0.94 (solo el último medio dB).

Resultado: la cadena suena con la **transparencia de un EQ tipo Poweramp**, y el limitador true-peak sigue **garantizando que NO recorta a ningún volumen** (matemáticamente no puede pasar su techo).

## ✨ Firma Aura mejorada (sigue activa)
Re-afiné la firma para que sea **más limpia y correcta**, manteniendo el cuerpo:
- Cuerpo grave intacto, **aire más fino**, y se **corrigió a la mitad el bajón de medios** que tenía de antes (sonaba un pelín hundida en el medio).
- Ahora corre **sin armónicos sucios** (gracias a los cambios de arriba) — cuerpo rico, voz limpia.

> Nada de esto toca las transiciones (crossfade) ni la nivelación de volumen entre canciones — solo la limpieza del sonido.

## 👉 Para probar
- Sube el volumen del teléfono al máximo con un máster fuerte y mucho bajo → debe sonar **limpio**, sin raspeo ni distorsión.
- Compara el EQ plano con otro reproductor (Poweramp) → debe sonar transparente; la firma Aura le agrega cuerpo sin ensuciar.
