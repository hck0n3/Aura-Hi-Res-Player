# Aura Hi-Res Player 0.6.56 — adiós voz rasposa: arreglada la "Firma Aura"

## 🎙️ Sin distorsión ni voz rasposa
Auditamos el normalizador a fondo con varios agentes. Resultado:
- **El normalizador de volumen y el limitador true-peak están limpios** — el signo, la ganancia y el limitador multibanda (que separa los graves de la voz) trabajan bien. No eran el problema.
- **El culpable era la "Firma Aura"** (el realce sutil de graves/aire que viene **activado por defecto**): en masters ya "calientes" empujaba los picos por encima del máximo y chocaba con un recorte interno demasiado abrupto → **armónicos = voz rasposa**, antes del limitador bueno.

**Arreglado:**
1. La Firma ahora lleva un **margen de −1 dB** para que **nunca** sobrepase el pico de entrada (el dB se recupera después en el normalizador). Conserva su "color", sin distorsión.
2. El recorte interno de seguridad se hizo **más suave** (genera muchos menos armónicos si cualquier efecto se pasa).

Revisado con auditoría adversarial: el arreglo elimina el sobrepaso que causaba la distorsión, sin perder loudness ni cambiar el carácter del sonido.

## 👉 Para probar
- Una canción con voz fuerte / master alto → la voz debe sonar **limpia**, sin raspeo.
- (Si quieres comparar: Ajustes de sonido → Firma Aura on/off; ahora ambos suenan limpios.)
