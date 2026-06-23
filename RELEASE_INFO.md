# Aura Hi-Res Player 0.5.0

## Paga sin salir de la app 💳
- El botón **"Suscribirme por $3.74/mes"** ahora abre el checkout de Gumroad **dentro de la app** (Chrome Custom Tab), sin mandarte a otra aplicación.
- Al terminar el pago, copias la clave de licencia que Gumroad muestra en el recibo; al volver a la app **se detecta sola desde el portapapeles y se activa automáticamente** — sin pegar a mano.
- Sigue funcionando todo lo de antes: pegado manual de la clave, la **llave maestra** y la verificación de suscripción intactas.

## Técnico
- `androidx.browser` (Custom Tabs); `openGumroad` usa `CustomTabsIntent` con fallback a navegador externo.
- `LicenseScreens.SubscriptionEntryScreen`: observador `ON_RESUME` que lee el portapapeles, valida el formato de licencia (XXXXXXXX-…×4) y activa vía `LicenseManager.activateSubscription`. Todo aditivo.
