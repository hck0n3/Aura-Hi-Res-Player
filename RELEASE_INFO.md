# Aura Hi-Res Player 0.6.62 — Firma Aura con más cuerpo (sin distorsionar) + "Nivelar volumen"

## 🔊 Firma Aura: ahora suena más rica, sin distorsión
La firma Aura era casi imperceptible (a propósito, porque en el motor viejo subir más distorsionaba). Como **ahora la cadena es 32-bit flotante**, el limitador absorbe los picos limpiamente, así que le subimos el cuerpo:
- **Cuerpo** +2 dB en graves/medios-graves @120 Hz (ahí vive el "cuerpo"/calidez).
- **Aire** +0.8 dB @12 kHz.
- Quitado el recorte −1 dB que ya no hacía falta.

→ Más **cuerpo y calidez**, y suena **rico incluso a todo volumen, sin distorsionar**.

## 🏷️ "Normalización del audio" ahora se llama "Nivelar volumen"
Nombre más honesto y claro: lleva todas las canciones al mismo volumen.

## 🔜 Lo siguiente (el grande)
La próxima trae el fix de raíz para que **TODAS** las canciones suenen al mismo nivel: las que no traen info de volumen (locales, etc.) se **medirán al reproducirse** y se nivelarán igual que las demás.

## 👉 Para probar
- Activa la firma Aura → más cuerpo/calidez; sube el volumen al máximo → debe sonar rico, **sin distorsionar**.
