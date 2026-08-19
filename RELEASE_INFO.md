# Aura Hi-Res Player 0.6.225

El recolector de errores que pediste: ahora un solo reporte muestra el destino completo de los 11 clientes, no solo el fragmento final.

---

## Novedades y Correcciones

- **Reporte de diagnóstico mucho más completo**: hasta ahora, cada reporte que mandabas solo alcanzaba a mostrar el final de la última canción que falló — si fallaban varias seguidas, se perdía el rastro de las anteriores. Se amplió la ventana del reporte (de ~80 a 400 líneas) para que quede el rastro completo de una sesión, no solo el último fragmento. También se cerró un hueco real: cuando la comprobación de un enlace fallaba, quedaba invisible en todos los reportes anteriores — ahora se guarda con el código HTTP real. Y se dejó registrado, por primera vez, cuándo un enlace se usa SIN pasar esa comprobación (que es justo lo que le pasa siempre a WEB_CREATOR). El próximo reporte que mandes va a mostrar de una vez el destino de cada cliente para cada canción, en vez de necesitar varias rondas para armar el cuadro completo.
- **Reproducción de canciones nuevas (0.6.224, incluida aquí)**: la comprobación que decide si un enlace de audio realmente funciona mandaba las cabeceras equivocadas para casi todos los clientes — eso hacía que se descartaran en silencio y el sistema siempre terminara en el único cliente que no pasa por esa comprobación (el que ya sabemos que falla al reproducir de verdad). Ya corregido.

## Cómo actualizar

- Avatar ▸ Actualizaciones.
