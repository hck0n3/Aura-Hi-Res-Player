# Aura Hi-Res Player 0.6.154

Transiciones sin bajones de volumen, un solo nivel de referencia, y cola inteligente más asertiva al gusto.

---

## 🎚️ Transiciones y volumen

- Corregido: Safe Volume del tema actual **ya no pisa** el nivel de la siguiente canción precargada (eso provocaba cortes o bajones al cruzar).
- El crossfade usa **un solo volumen** (el de la app), no el nivel accidental del ExoPlayer.
- Si la transición no puede mezclar a tiempo: se libera el secundario y se restaura el volumen; precarga 3 s antes.

## 🎯 Predicción / cola inteligente

- Más peso al gusto (artistas/géneros ancla) frente a lo genérico de YouTube.
- Menos exploración aleatoria (~1 de cada 15 en vez de ~1 de 10).
- Anti-deriva de género más fuerte en el contexto de la lista que acabas de terminar.
- Inicio: menos “ruido” al ordenar por gusto.

## 📦 Del 0.6.153

- Actualizaciones visibles + aviso y salto a la pantalla de update.
- Carruseles más fluidos, portadas en búsqueda, letras más suaves, Tu biblioteca con Me gusta.
