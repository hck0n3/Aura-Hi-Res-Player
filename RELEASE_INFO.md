# Aura Hi-Res Player 0.6.224

Encontrada la razón por la que los clientes que sí consiguen enlace nunca llegaban a probarse de verdad.

---

## Novedades y Correcciones

- **Reproducción de canciones nuevas**: revisando por qué WEB_REMIX e IOS decían "respuesta OK" y aun así nunca ganaban, encontré que el paso que comprueba si un enlace realmente funciona (antes de confiarle la canción a ese cliente) siempre mandaba las cabeceras de un navegador web, sin importar si el cliente real era otro (como IOS) — y nunca mandaba el Origen/Referer que sí necesitan los clientes web. Ese error se descartaba en silencio, así que el sistema siempre terminaba cayendo en WEB_CREATOR, el único cliente que se salta esa comprobación — y que ya sabemos que falla al reproducir de verdad. Corregido para que esa comprobación use las cabeceras correctas según el cliente. También se agregó al sistema de autocuración un identificador del reproductor de YouTube que faltaba.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
