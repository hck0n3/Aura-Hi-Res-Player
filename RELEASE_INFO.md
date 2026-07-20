# Aura Hi-Res Player 0.6.113 — Menos cortes al empezar canción en Lossless, y tres fallos de audio blindados

## 🎵 Menos cortes al empezar canción (en Lossless / Saavn)
- Cuando pedías **Lossless** y esa calidad no estaba disponible, la app cambiaba de formato y **provocaba un corte a propósito** para reiniciar el reproductor.
- Ahora, si el corte cae **al empezar la canción**, ya no ocurre.
- **En Opus esto nunca pasaba** — por eso al cambiar tú a Opus deberían desaparecer.

## 🛡️ Tres puntos donde el audio podía romperse: blindados
- Si un formato venía sin la etiqueta interna de códec, la app podía **quedarse sin reproducir** esa canción. Corregido en los tres sitios donde pasaba (reproducción, descarga y precarga).
- Y de paso se cerró un caso en el que un dato incompleto podía hacer que la app **re-descargara la canción en curso una y otra vez**.

## ⚠️ Todavía no arreglado — con honestidad
- **Los cortes a MITAD de canción siguen abiertos.** Este arreglo solo cubre los del arranque. El de mitad de canción está entendido y documentado, pero **no lo toco hasta poder confirmarlo con un registro real** — llevo dos intentos fallidos con este bug y no voy a arriesgarme a un tercero a ciegas.
  - Si vuelves a Lossless y te pasa: mándame el registro (Ajustes → Registros) y lo confirmo o lo descarto en el acto.
- **Volumen Seguro** sube como máximo +3 dB.
- **Plegables:** cuatro mejoras hechas pero sin probar en un plegable real.
