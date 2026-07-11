# Aura Hi-Res Player 0.6.99 — El inicio de sesión de Last.fm (Scrobbling) ya funciona

## 🎧 Last.fm / Scrobbling arreglado
- Corregido el error **"invalid parameters"** al iniciar sesión en **Last.fm** desde Ajustes ▸ Scrobbling. La clave de Last.fm de la app viajaba **vacía** en las versiones publicadas (un fallo en cómo se empaquetaba la clave), así que el inicio de sesión fallaba para todos. Ahora la clave se incluye correctamente y **puedes iniciar sesión con tu usuario y contraseña de Last.fm**.

### Cómo activarlo
1. Crea una cuenta en **last.fm/join** (si no tienes) y confirma el correo.
2. En Aura: **Ajustes ▸ Scrobbling ▸ Iniciar sesión**.
3. Escribe tu **usuario de Last.fm** (el usuario, no el correo) y tu contraseña.
4. Activa **Activar scrobbling**.

## ✅ Como siempre
- Suscripción, licencia, demo, crossfade de 9s y motor de audio: intactos. Verificado contra el registro de fallos: sin regresiones.
