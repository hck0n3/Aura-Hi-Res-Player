# Aura Hi-Res Player v5.7.113

## La reproducción empieza más rápido
- Si tenías sesión iniciada, cada canción generaba un "poToken" en un WebView (lento) solo para los datos de seguimiento/normalización — y eso retrasaba el inicio varios segundos por pista.
- Estudié cómo lo hace tu reproductor de escritorio: usa clientes que NO necesitan ese token. Tu app ya saca el audio con un cliente así (ANDROID_VR); el retraso estaba en los datos extra.
- Ahora esa metadata ya no genera el token (no lo necesita) y, además, tiene un límite de tiempo para que NUNCA bloquee el inicio. El audio sale igual de fuerte (la normalización usa el cliente principal si hace falta).

Resultado esperado: el play empieza notablemente antes, sobre todo al cambiar de canción con sesión iniciada.
