# Aura Hi-Res Player 0.6.68 — adiós voz rasposa a alto volumen + salida con más aire

## 🎤 Voz rasposa / distorsión pasada la mitad del volumen — ARREGLADO
Causa encontrada (diagnóstico multi-agente): la **firma Aura** (los realces de cuerpo+aire que vienen activos) había perdido su recorte de seguridad de -1 dB en la migración a 32-bit float. Sin ese recorte, sobrepasaba hacia un **soft-clip interno** de JrDsp **antes** del limitador transparente → metía armónicos en la señal → las voces sonaban rasposas, y se notaba justo al subir el volumen del teléfono pasada la mitad.

Arreglado:
- **Firma Aura recorta -1 dB de nuevo** → ya no sobrepasa el soft-clip interno (la causa directa de la voz rasposa). El cuerpo y el aire se mantienen, solo se quita el pico de más.
- **Salida menos caliente.** El realce máximo de volumen bajó de +12 dB a **+9 dB** → los temas más fuertes ya no aplastan el limitador, queda **headroom** real, y subir el teléfono pasada la mitad ya **no** distorsiona. (La nivelación entre canciones no cambia — sigue todo al mismo volumen.)
- **Limitador final más limpio** (knee 0.88→0.90, ceiling 0.92→0.95) → menos modelado de onda en programa normal, solo actúa en picos reales.

## 👉 Para probar
- Sube el volumen del teléfono **pasada la mitad** con la firma Aura activa y EQ plano → las voces deben sonar **limpias**, sin raspeo.
- Un máster moderno muy fuerte + un tema con mucho bajo → sin distorsión al subir volumen.
- Si algún tema viejo muy bajo te queda algo flojo, avísame (puedo subir el tope a +10 dB).
