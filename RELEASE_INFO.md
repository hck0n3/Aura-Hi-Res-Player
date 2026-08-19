# Aura Hi-Res Player 0.6.216

Arreglo real del error 403 — esta vez ataca la causa raíz, no el síntoma.

---

## Novedades y Correcciones

- **Reproducción de canciones nuevas, arreglo definitivo**: se descubrió que el último cliente de respaldo confía en su enlace SIN revisarlo (por velocidad). Las dos versiones anteriores (0.6.214 y 0.6.215) modificaban ese enlace con una fórmula, pero como nunca se revisaba, un error en esa fórmula llegaba directo al teléfono sin que nadie lo detectara — causando el error del servidor (`IO_BAD_HTTP_STATUS`, código 2004) que varios usuarios reportaron. Ahora, si la app modifica ese enlace, lo revisa antes de confiar en él; si la revisión falla, usa un método de respaldo adicional en vez de entregar un enlace roto.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
