# Aura Hi-Res Player 0.6.66 — Auto-EQ y tu ecualizador AHORA se APILAN (no se reemplazan)

## 🎚️ Auto-EQ como filtro aparte + tu EQ ENCIMA
Antes, aplicar un perfil de Auto-EQ **bloqueaba** tu ecualizador gráfico/paramétrico. Ya no.

Ahora el Auto-EQ corre como su **propia etapa de corrección**, y **tu EQ se suma encima** sin borrar lo que hizo el Auto-EQ:
- Aplicas el perfil de tus auriculares (corrección) → y además ajustas **tu** EQ (gráfico o paramétrico) **sobre** esa corrección.
- Tus sliders **siguen activos** con Auto-EQ puesto (ya no se ponen grises).
- Un chip **"Auto-EQ activo — tu EQ se suma encima"** + botón **"Quitar Auto-EQ"** para sacar solo la corrección cuando quieras.

## 🔒 Sin distorsión al apilar
Las dos etapas suman ganancia donde coinciden, así que el headroom ahora **reserva el pico apilado real por frecuencia** → el limitador no se satura aunque subas mucho en ambas. Revisión adversarial multi-agente (se cazó y corrigió justo eso antes de subir).

Compatibilidad: tus perfiles guardados/exportados antiguos siguen cargando igual.

## 👉 Para probar
- EQ → aplica un perfil Auto-EQ → ahora mueve **tu** EQ encima: debe sumarse a la corrección, no reemplazarla, y sin distorsionar.

> Siguiente (tu prioridad): el fix de raíz del volumen — que toda canción entre ya nivelada y nada lo mueva a mitad (ni like, ni cerca del final).
