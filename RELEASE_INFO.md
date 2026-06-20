# Aura Hi-Res Player v5.7.62

## Arreglo: las sugerencias/álbumes no cargaban con sesión iniciada
- Cuando estabas conectado a Google (y Spotify), las pestañas **"Sugerencias"** y **"Álbum"** del buscador a veces salían vacías.
- **Causa:** el descubrimiento (sugerencias, novedades, álbumes) se pedía a YouTube Music **con tu cuenta**, y en ese modo YouTube suele devolver un catálogo vacío o limitado.
- **Solución:** ahora el descubrimiento se carga en modo **invitado** (fiable y completo). Tu biblioteca, "Me gusta", playlists y artistas siguen usando tu cuenta normal — no cambia nada de eso.
- Si lo prefieres al revés, sigue estando el interruptor en Ajustes ("Usar inicio de sesión para explorar").
