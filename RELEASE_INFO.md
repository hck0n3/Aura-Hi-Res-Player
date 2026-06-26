# Aura Hi-Res Player 0.6.45 — Pulido del motor de audio (auditoría, parte 9)

Arreglos finos en el **motor de audio en tiempo real** (revisados adversarialmente, sin cambiar el sonido normal):

## Más estable en gama baja ⚙️
- **Anti-denormales:** los estados internos de los efectos DSP (reverb/room, graves, exciter, HRTF, limitador multibanda) ya **descartan los valores subnormales** que en muchos CPU ARM son 10-100× más lentos y causaban micro-glitches y gasto de CPU en pasajes silenciosos. Inaudible, solo más eficiente.

## Ecualizador sin recortes duros 🎚️
- Un pico muy resonante en una banda de Q alto ya **no se recorta en seco** a la salida del EQ: ahora se redondea suavemente (soft-clip) antes del limitador. El nivel normal no cambia.

## Preparado para el crossfade
- Los procesadores de audio ahora **pueden** llevar valores de normalización por reproductor (capacidad lista, sin efecto todavía) — base para eliminar el leve "pumping" de volumen durante el crossfade en una próxima versión.

## Decisiones de la auditoría que NO apliqué (a propósito)
- **No** capo el bitrate del audio con datos móviles: esto es un **Hi-Res Player**, el audio va siempre a máxima calidad (solo el video baja con datos, como pediste).
- **No** quito los pequeños retardos de la sincronización: throttlean las llamadas de red y evitan el corte por rate-limit que viste en el Redmi.

## Pendiente (próxima versión, con prueba de audio/sync en tu teléfono)
- Cablear la normalización **por reproductor** en el crossfade (M3) y el refactor de transacciones de BD (#6) — son cambios estructurales con alcance en todo el audio/sync, los haré con cuidado y revisión.
