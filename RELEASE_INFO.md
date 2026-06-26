# Aura Hi-Res Player 0.6.51 — EQ paramétrico AHORA visual: arrastra los puntos 🎚️

El modo **Paramétrico (PEQ)** era para puristas (escribir Hz/Q/dB a mano) y resultaba difícil. Ahora es **visual e interactivo**, fácil para cualquiera:

## Arrastra puntos en la curva 👆
- Una **gráfica de respuesta en vivo** con la curva de tu sonido.
- Cada banda es un **punto que arrastras**:
  - **↔ izquierda/derecha** = frecuencia,
  - **↕ arriba/abajo** = subir/bajar (ganancia).
- **Toca un punto** y abajo aparece su panel: **slider de "Ancho (Q)"**, tipo de filtro (pico/shelf) y un botón para **quitar** la banda.
- Botón **"Añadir banda"** (5 a 8 bandas).
- La curva **y el sonido** cambian **mientras arrastras** → inmediato e intuitivo.

## Para el purista 🎯
Los **valores exactos** (p. ej. "120 Hz · Q 1.4 · +3.5 dB") siguen visibles al tocar un punto, y el slider de Q deja afinar fino. Así el novato "dibuja" su sonido y el experto mantiene el control preciso.

## Bajo el capó
La curva dibujada es la **magnitud real del filtro biquad** (la misma matemática RBJ que procesa el audio), así que **lo que ves es lo que oyes** — incluso para perfiles importados con filtros paso-alto/paso-bajo.

> Diseño + construcción multi-agente con **revisión adversarial** (gestos, estado y matemática verificados; se añadió el dibujo correcto de los filtros paso-alto/paso-bajo de perfiles cargados).

## 👉 Para probar
- EQ → **Paramétrico** → arrastra los puntos, toca uno y mueve el **Ancho (Q)**, añade/quita bandas.
