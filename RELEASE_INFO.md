# Aura Hi-Res Player 0.6.139 — Auditoría de calor y batería: los quemadores reales, sellados

Reporte del dueño: "siento que mi cel está calentando con esa app". Auditoría completa en 3 frentes (motor de audio, UI/widgets, red) con costos derivados del código — y arreglo de todo lo que de verdad quema.

## 🔥 Los tres calentadores principales
1. **Sondeo fantasma**: abrir el menú ⋯ de cualquier canción UNA vez dejaba una consulta a la base de datos corriendo **cada segundo para siempre** (~86.000 consultas/día), incluso con la pantalla apagada y la app en segundo plano. Ahora se aparca en cero trabajo cuando nada lo está mirando.
2. **Reescritura de la cola a disco cada 10 s**: con colas grandes de radio eran ~1 MB de escritura seis veces por minuto, en el hilo principal. Ahora solo se guarda la posición; la cola completa únicamente cuando su contenido cambió de verdad (con guardia de coherencia para que una restauración nunca caiga en la canción equivocada).
3. **Widget de playlists**: reconstruía 5 consultas + ~9 mapas de bits + 69 vistas **por segundo**. Ahora el tick manda solo el progreso; todo lo demás solo cuando algo cambió.

## 🔧 Más ahorros reales
- El **segundo reproductor** del crossfade ya no se destruye y reconstruye 2-4 veces por canción cuando nada cambió (con validación estricta por versión de cola, para que jamás pueda colar una cola desactualizada).
- El barajado en colas de 5.000 canciones ya no recalcula tus gustos **en cada avance** (caché por canción: de 20-60 ms por avance a unos pocos).
- El **widget principal re-descargaba cada portada** por usar una caché separada de la del resto de la app. Unificado.
- El prewarm de ~2 MB del arranque ahora respeta el **Ahorro de datos**.
- El scrobbling sin conexión ya no genera 2 reportes de telemetría **por canción**.
- La carátula del widget ya no se reintenta por red cada segundo cuando falla una carga.

## 📖 Bienvenida y Acerca de: todo lo real, sin placebos
Ambas pantallas actualizadas con TODO lo que la app ofrece hasta hoy — cada afirmación **verificada contra el código** antes de escribirse: transiciones con memoria de silencios, Aleatorio Mejorado con separación de artistas, discografías completas, 3 widgets, Auto/TV/Cast, cierres del sistema, y más. Corregidos dos datos desfasados (SponsorBlock ya viene activo; Liquid Glass se enciende solo en gama alta).

## Recordatorio
- Si notas calor: dime si pasa con **pantalla apagada o abierta**, si tienes **widget** puesto, y si vas por **datos o WiFi** — con eso lo atribuyo fino.
- **Cierres del sistema** (Ajustes ▸ Registros): si la app se cierra sin error, ahí está la causa real.
