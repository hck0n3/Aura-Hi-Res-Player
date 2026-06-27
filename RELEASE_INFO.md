# Aura Hi-Res Player 0.6.64 — transición pareja (sin baja-y-sube) a 12s + skip-silence usable

## 🔀 El crossfade ya no baja y sube de golpe
La transición suave **bajaba el volumen a mitad** y luego lo **subía de golpe** al final. **Dos causas:**
- La curva por defecto era **lineal** → dos canciones mezcladas suman menos en el medio = hundimiento de volumen. **Arreglado:** ahora es **potencia constante** (las dos llevan la misma energía durante la mezcla) → loudness **parejo**, sin hundimiento.
- Al terminar, el margen de seguridad se quitaba **de golpe** (el "pum"). **Arreglado:** la canción entrante **sube su nivel suavemente** hasta el 100% justo al terminar la mezcla → **sin salto**.

**Duración a 12 segundos** (como pediste) — se aplica a tu instalación automáticamente.

## 🔇 "Saltar silencios al instante" ya se puede activar
Estaba gris porque depende de "Saltar silencios". **Ahora:** al tocarlo, **activa solo** también el principal → usable de una.

## 👉 Para probar
- Deja que pase un crossfade entre dos canciones → el volumen debe mantenerse **parejo** (sin bajar ni dar el "pum" al final), y durar ~12 s.
- Ajustes → Reproductor → "Saltar silencios al instante" → debe poder activarse directo.

> En camino: Auto-EQ (duplicados, scroll, favoritos, apilar con tu EQ) + botón Compartir.
