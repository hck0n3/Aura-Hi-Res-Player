# Aura Hi-Res Player 0.6.86 — Reconocimiento reparado, letras en sincronía y Agregar música pulido

## 🎙️ Reconocer canción — REPARADO
- **Vuelve a funcionar el reconocimiento de canciones** (llevaba roto desde 0.6.82 por una actualización interna del motor de reproducción): identifica lo que suena a tu alrededor desde la app, el widget o el mosaico de Ajustes Rápidos.
- **Cancelar de verdad cancela**, reintentar funciona al instante, y desde el widget/mosaico el reconocimiento **arranca solo** (antes había que tocar dos veces).
- Sigue escuchando aunque bloquees la pantalla o cambies de app (servicio de micrófono en primer plano).

## 🎤 Letras — en sincronía
- **Corregidas las letras desincronizadas**: el estilo por defecto ya no "inventa" el barrido palabra por palabra cuando la letra solo trae tiempos por línea — ahora ilumina la línea exacta al momento. Cuando la letra sí trae tiempos reales de palabras, el karaoke sigue idéntico.
- El desenfoque estilo Apple Music en letras ahora viene activado por defecto.

## ➕ Agregar música (estreno pulido)
- Las secciones de la ventana "Agregar música" ahora se deslizan **en horizontal, por páginas, estilo Apple Music**.
- **Canciones sugeridas de verdad basadas en tu playlist**: usa hasta 8 canciones de tu lista como semillas (locales + algoritmo de YouTube), rankea por afinidad y limita 2 por artista.
- **Sugerencias infinitas**: cada toque a actualizar trae 5 canciones nuevas sin repetirse (expande semillas automáticamente).
- Tocar la **portada** = preview; botón **`+`** al final de cada fila = se agrega al instante (y se marca ✓).

## 🤖 Listas con IA — ya sin API key
- **Crear playlists con una frase ahora funciona sin configurar nada**: la app usa la IA integrada de Aura (con respaldo gratuito automático). Tu propia key sigue siendo opcional para usuarios avanzados.

## 📺 Botón de transmitir (Cast)
- Siempre visible **arriba a la derecha** del reproductor, en todos los modos (letras, canvas, video, horizontal, TV). Y ahora solo busca dispositivos cuando abres el selector (menos batería).

## ✅ Honestidad y legal
- **"Acerca de" ahora dice exactamente lo que la app hace**: auditamos las 123 funciones anunciadas contra el código y corregimos cada texto (y arreglamos varias de verdad: bypass real de bandas del EQ a 0 dB, el radar reproduce estrenos, el tono de llamada se aplica de verdad).
- **Términos y condiciones**: se muestran al primer uso (aceptación requerida) y quedan siempre disponibles en Acerca de ▸ Información legal.

## 🔧 También
- Estrenos del radar con botón de reproducción directa.
- Menos consumo de batería y calor vigilado en todo lo nuevo.
- Suscripción, licencia, demo, crossfade de 9s y motor de audio: intactos.
