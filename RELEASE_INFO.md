# Aura Hi-Res Player 0.6.140-beta2 — Migración con Tidal por login (BETA PRIVADA)

> ⚠️ **Build de PRUEBA, solo para el dueño.** Sale como *prerelease*: el actualizador NO la ofrece a nadie más. Instálala a mano. Todo lo de 0.6.139 sigue igual.

## 🆕 Novedad de esta beta: Tidal con inicio de sesión
Ya no está en "próximamente". Biblioteca ▸ Importar ▸ Migrar playlist ▸ **Tidal**:
1. Pega tu **Client ID** (el que registraste en developer.tidal.com). Se guarda cifrado en el móvil.
2. Toca **Iniciar sesión en Tidal** → se abre el login de Tidal en el navegador → autorizas.
3. Vuelves a la app: verás **tus playlists de Tidal** (incluidas las privadas). Toca una para migrarla, o pega la URL de una playlist concreta.
4. Sigue el mismo flujo de progreso / resultados / revisión de ambiguas y aparece en tu Biblioteca.

**Importante — primera prueba real**: los endpoints de Tidal los confirmé por documentación, pero **nadie ha hecho un login de verdad todavía**. Si el login falla en la página de Tidal (no en la app), casi seguro es que el **Redirect URI** que registraste no coincide exactamente con `echomusic://tidal-callback` — revísalo en developer.tidal.com.

## Recordatorio de lo que ya funciona (de beta1)
- **Archivo (CSV/M3U/JSPF)** ✅
- **Deezer (URL pública)** ✅
- **Apple Music** 📄 guía a la transferencia nativa

## Sin secretos
Tu Client ID vive **solo cifrado en tu móvil**, nunca en el código ni en el APK. Sin Client Secret (usamos PKCE). Los tokens de sesión, cifrados en el llavero de Android — nunca se registran ni salen del dispositivo.

## Qué reportar
- ¿El login de Tidal abre y vuelve bien? ¿Salen tus playlists?
- ¿Las canciones migradas son la versión correcta (no remix/directo)?
- ¿La playlist aparece en Biblioteca y se reproduce?
- Cualquier fallo → **Ajustes ▸ Registros**.
