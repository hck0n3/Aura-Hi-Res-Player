# Aura Hi-Res Player 0.6.114 — Recuperación de sesión de verdad cuando YouTube pide "inicia sesión"

## 🔑 Cuando tu sesión caduca, la recuperación ahora funciona de verdad
- En la versión anterior, si YouTube rechazaba tu sesión, la app **intentaba recuperarse pero fallaba igual**: el reintento seguía llevando tu cuenta a medias, y YouTube lo rechazaba otra vez.
- Ahora el reintento es **realmente anónimo** (como un invitado limpio) y además **refresca la sesión de invitado** antes de reintentar, por si YouTube tenía marcada la anterior.
- **Tu cuenta no se toca** en ningún momento: sigues con sesión iniciada para tu biblioteca.

## ⚠️ Con honestidad sobre el Xiaomi Redmi Note 13 Pro
- Esta versión arregla dos casos: **cookie caducada** y **sesión de invitado marcada por YouTube**.
- **Pero si YouTube ha bloqueado tu cuenta o tu conexión directamente**, ninguna app puede saltárselo. En ese caso la cura es **cerrar sesión y volver a entrar** en ese teléfono, o **probar con otra red** (datos en vez de WiFi, o al revés).
- **Prueba esto primero en el Redmi:** cierra sesión de YouTube y vuelve a entrar. Si era cookie muerta, se arregla al instante.
- Y si sigue fallando, mándame el registro de ese intento (Ajustes → Registros): las líneas nuevas me dicen exactamente si es recuperable o es un bloqueo de YouTube que no depende de la app.

## Recordatorio de lo abierto
- **Cortes a mitad de canción** (solo en Lossless/Saavn) — necesito tu registro en esa calidad para tocarlo con seguridad.
- **Volumen Seguro** sube máximo +3 dB.
- **Plegables:** mejoras hechas, sin probar en un plegable real.
