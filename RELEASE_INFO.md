# Aura Hi-Res Player 0.6.47 — Ecualizador más limpio y coherente (reestructura audiófila, parte 1)

Primer paso de la reestructura del EQ que pediste (evaluada por 7 agentes + revisada antes de subir):

## Fuera el "Tono" (Graves/Agudos) 🧹
Los controles de Tono eran redundantes con el ecualizador de 24 bandas. Los quité para una señal más limpia. *(Nota: la "Sonoridad" es un realce fijo, no ajustable, así que el ajuste fino de graves/agudos se hace ahora con el EQ gráfico o el nuevo modo paramétrico que viene.)*

## Bandas en 0 dB = bypass real ⚡
Una banda gráfica que dejes en 0 dB ya **no procesa nada**: se salta el filtro por completo (ahorra CPU y deja pasar el audio puro, sin tocarlo). Sin clicks al cruzar el 0 dB, y los filtros pasa-altos/pasa-bajos siguen funcionando aunque estén a 0 dB (esos sí filtran de verdad).

## Auto-EQ y EQ manual ya no se pelean 🔒
Al aplicar un **Auto-EQ** de auriculares, el ecualizador gráfico se **bloquea** (se grisea) para no corromper la corrección perfecta. Si quieres editar a mano, pulsa **"Cambiar a manual"** en el aviso y vuelves al control total (las bandas conservan la curva del Auto-EQ como punto de partida). Cargar un perfil **guardado tuyo** no bloquea nada (es manual).

## Lo que viene
- Modo **Gráfico / Paramétrico (PEQ)** para ajustes quirúrgicos.
- Layout de **dos columnas** al desplegar pantallas plegables (Z Fold).

## 👉 Para probar
- Mueve una banda lentamente por el 0 dB con música → sin clicks; con todas a 0 → audio idéntico a EQ apagado.
- Aplica un Auto-EQ → el gráfico se bloquea; "Cambiar a manual" lo desbloquea.
