# Aura Hi-Res Player 0.6.155

Letras que coinciden con la canción, sincronización estable y animaciones que no se adelantan en el crossfade.

---

## 🎤 Letras y sincronización

- Corregido: letras con cabeceras tipo `[Verse]` / `[Chorus]` ya no se tratan como LRC sincronizado (antes dejaban el panel vacío o desfasado).
- El parser LRC acepta más formatos reales (`[0:13.42]`, `[00:13]`, fracciones de 1–3 dígitos).
- La detección de «letra sincronizada» es la misma en la descarga y en la pantalla — ya no pueden discrepar.
- Estilo Metro: la animación karaoke usa el reloj de la canción audible durante el crossfade (antes seguía la pista entrante).
- Al cambiar de canción rápido, no se guarda una letra pedida para la pista anterior.

## 📦 Del 0.6.154

- Transiciones sin bajones de volumen; un solo nivel de referencia en el crossfade.
- Cola inteligente más asertiva al gusto.
