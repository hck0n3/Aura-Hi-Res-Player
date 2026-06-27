# Aura Hi-Res Player 0.6.70 — transición a 10 s + tus me gusta aparecen en la búsqueda

## 🔁 Transición (crossfade) a 10 segundos
La duración de la transición suave ahora es **10 segundos** (la pediste así). Mantengo la curva **pareja (equal-power)** que elegiste: **volumen constante durante toda la transición, sin el bajón en el medio**. Se aplica a todos (migración automática).

## ❤️ Tus me gusta ya aparecen al buscar
La búsqueda local ahora muestra una canción si le diste **me gusta**, aunque no la hayas agregado a la biblioteca. Antes solo salían las de la biblioteca, así que un tema con corazón podía no aparecer.

> Nota: sigo investigando el caso de likes que se pierden de forma intermitente (cuando la canción viene de la radio y su id no coincide con el del catálogo de búsqueda) — eso lo arreglo aparte.

## 👉 Para probar
- Deja terminar una canción → la transición debe durar 10 s, pareja, sin bajón.
- Dale ❤️ a algo y búscalo por nombre → debe aparecer.
