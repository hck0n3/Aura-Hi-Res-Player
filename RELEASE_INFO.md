# Aura Hi-Res Player 0.6.63 — TODAS las canciones al mismo volumen (incluso las que no traen info)

## 🎚️ Por fin: volumen parejo en toda la biblioteca
El nivelado de volumen solo funcionaba con canciones que **traen** info de loudness (parte de YouTube). Las que NO la traen (archivos locales, Saavn, otras) usaban un valor fijo → unas sonaban más fuerte que otras.

**Ahora:** la app **mide el volumen real** de esas canciones mientras suenan (los primeros ~12 s), lo **guarda**, y lo aplica al **mismo nivel de referencia (−14 LUFS)** que las demás. Resultado: **toda la biblioteca a un solo volumen**, fuerte pero sin distorsionar (el limitador caza los picos).

- **Cero cambios de volumen a mitad de canción:** la medición se **guarda** y se aplica desde el **segundo 0 de la siguiente reproducción** (la primera vez se mide en silencio, sin tocar el volumen mientras suena).
- Se **cachea por canción** → solo se mide una vez; después es instantáneo y consistente.
- La medición usa energía por canal (estilo BS.1770) para no fallar con estéreo amplio.

## Bajo el capó
Diseño + implementación multi-agente con **revisión adversarial** (se cazaron y corrigieron 3 cosas antes de subir: que un re-escaneo no borre la medición de archivos locales, la calibración del nivel, y un detalle de CPU en silencios). Migración de base de datos automática (sin perder tus datos).

## 👉 Para probar
- Pon canciones de distinto origen seguidas (YouTube + locales) → **dales una reproducción a cada una**; a partir de la 2ª vez deben sonar **todas al mismo volumen**.

> En camino: crossfade parejo (sin que baje y suba) a 12 s · más cosas de Auto-EQ · botón Compartir.
