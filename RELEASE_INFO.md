# Aura Hi-Res Player 0.6.60 — el volumen ya no sube solo con la pantalla apagada

## 🔅 Arreglado: el volumen que "subía de la nada" (sobre todo con la pantalla apagada)
Con la música en segundo plano y la pantalla apagada, el volumen podía **subir solo**. **Causa:** al apagarse la pantalla (o un microcorte de buffer), Android **reabre la sesión de audio** y los procesadores se reinician; el código volvía a calcular la normalización pero, para la canción que ya sonaba, **se saltaba sin re-afirmar la ganancia** → la cadena podía quedar a **nivel crudo (más fuerte)**.

**Arreglado:** ahora, cada vez que se reabre la sesión, la app **re-afirma la ganancia que ya estaba aplicada** a esa canción (sin recalcular → sin saltos). Así el nivel con la pantalla apagada es **idéntico** al de la pantalla encendida. El volumen ya no sube solo.

## 👉 Para probar
- Pon música, apaga la pantalla, déjala sonar un rato → el volumen debe mantenerse estable.

> En camino (con cuidado): toda la cadena de audio procesada en **32-bit flotante** + subir el nivel a referencia TIDAL sin distorsionar.
