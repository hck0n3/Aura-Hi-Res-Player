# Aura Hi-Res Player 0.6.72 — el EQ no "se activa" al entrar + back de una sola vez

## 🎚️ El sonido ya no cambia al abrir el ecualizador
Al abrir la pantalla del EQ, el sonido cambiaba "como si el ecualizador se activara". Causa: el EQ se **re-aplicaba** al abrir la pantalla (re-emitía el mismo perfil → un pequeño blip). Tu EQ ya se aplica al **arrancar la app** (el perfil queda persistido), así que ese re-apply sobraba. **Quitado** → abrir el EQ ya no toca el sonido.

## ↩️ Back del ecualizador de una sola vez
En el EQ, al dar "atrás" volvías a la **misma pantalla** y necesitabas un segundo back para salir. Causa: el EQ se metía **dos veces** en el historial de navegación. Ahora con `launchSingleTop` entra una sola vez → **un back y salís**.

## 👉 Para probar
- Reproduce algo, abre el ecualizador → el sonido NO debe cambiar al entrar.
- En el EQ, dale atrás una vez → debe salir directo (no quedarse en la misma pantalla).
