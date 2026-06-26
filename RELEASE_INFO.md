# Aura Hi-Res Player 0.6.48 — Ecualizador paramétrico (PEQ) para puristas (reestructura, parte 2)

## Nuevo: modo Gráfico ↔ Paramétrico 🎛️
En el ecualizador ahora hay un selector **Gráfico / Paramétrico**:
- **Gráfico:** el EQ visual de 24 bandas de siempre (ideal para perfiles rápidos).
- **Paramétrico (PEQ):** **5 a 8 bandas totalmente libres** — escribes la **frecuencia exacta (Hz)**, la **Q** y la **ganancia (dB)**, y eliges el tipo (pico / shelf). Ajustes quirúrgicos al estilo audiófilo.

Cada modo **guarda su propia curva**: cambiar de uno a otro no pierde lo que tenías. Y los perfiles que guardes recuerdan su modo.

## Hecho con cuidado (7 agentes evaluaron + revisión adversarial)
La revisión encontró y corrigió **8 detalles** antes de subir, entre ellos:
- Los campos de número ahora son **escribibles de verdad** (también en teclados con coma decimal: "0,7" o "0.7" valen) y no se "auto-corrigen" mientras escribes — solo al terminar.
- En modo paramétrico se **ocultan** la curva y los presets de 24 bandas (para no confundir ni dejar botones que no suenan).
- Guardar/cargar un perfil paramétrico **conserva** sus bandas y vuelve a su modo.
- Editar ya **no escribe en disco en cada tecla** (solo al soltar).

*(Pendiente conocido y de nicho: importar un archivo paramétrico EXTERNO de exactamente 24 bandas se trataría como gráfico — el flujo de crear/guardar PEQ dentro de la app funciona perfecto.)*

## Lo que viene
- Layout de **dos columnas** al desplegar pantallas plegables (Z Fold), con los efectos DSP junto al EQ.

## 👉 Para probar
- Cambia a **Paramétrico**, escribe frecuencias/Q/ganancia, añade/quita bandas (5-8), guarda y recarga.
