# Aura Hi-Res Player 0.6.116 — Letras sincronizadas primero + nuevo proveedor Unison

## 🎤 Ahora prioriza las letras sincronizadas
- Antes la app mostraba lo primero que llegara, sincronizado o no.
- Ahora **prefiere las letras sincronizadas** (las que se resaltan al ritmo de la canción). Si ninguna fuente las tiene, cae a texto plano.
- Sin volverse lenta: si la primera fuente ya trae sincronizadas, se muestran al instante; y si solo hay texto plano, aparece rápido (no se queda esperando).

## ➕ Nuevo proveedor de letras: Unison
- Añadido **Unison** (unison.boidu.dev) — ya tenías los otros 8, ahora están los 9.
- Se puede activar/desactivar en Ajustes, como los demás.
- Es un proveedor **colaborativo** y su base aún es pequeña, así que hoy encuentra pocas canciones — por eso va de **último recurso**: no frena a los proveedores fiables, solo se consulta si ninguno tiene la letra. Irá encontrando más a medida que crezca.

## 🔧 Por dentro
- Al añadir Unison de último, evité que el proveedor Paxsenix volviera a consultarse en cada canción (su servidor bloquea a menudo). Ahora los dos últimos proveedores solo se consultan si de verdad hace falta.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — necesito tu registro en esa calidad.
- **Volumen Seguro** sube máximo +3 dB.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
