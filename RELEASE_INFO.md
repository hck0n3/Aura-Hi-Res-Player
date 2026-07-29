# Aura Hi-Res Player 0.6.140-beta1 — Migrar playlists a YouTube Music (BETA PRIVADA)

> ⚠️ **Build de PRUEBA, solo para el dueño.** Sale como *prerelease*: el actualizador de la app NO la ofrece a nadie más. Instálala a mano desde esta página. Todo lo de 0.6.139 sigue igual.

## 🎵 Nueva función: migrar playlists de otros servicios a YouTube Music
Biblioteca ▸ botón "Importar" ▸ **Migrar playlist**. Elige la fuente:

- **Archivo (CSV / M3U / JSPF)** ✅ funcional — exporta tu playlist de cualquier servicio (TuneMyMusic, Soundiiz, export GDPR de Spotify/Apple, o un reproductor local) e impórtala. Sin login, sin trámites.
- **Deezer (URL pública)** ✅ funcional — pega el enlace de una playlist pública. (Deezer cerró el registro de apps, así que solo públicas.)
- **Tidal** ⏳ preparado, login "próximamente" — necesita que registres un client-id en developer.tidal.com (10 min, tu cuenta). La pantalla lo explica y guarda el id cifrado; el inicio de sesión se activa cuando confirmes los endpoints de Tidal.
- **Apple Music** 📄 guía — te lleva a la transferencia nativa de Apple (lo más fiable para Apple).

### Cómo funciona la coincidencia
Cada canción se busca en YouTube Music y se puntúa por **duración + artista + título + álbum + versión**. Regla de oro: **nunca inserta una coincidencia dudosa en silencio** — si no está claro, va a "revisar" y decides tú. La duración es el discriminante fuerte (una versión en vivo dura distinto que la de estudio), y las etiquetas Remix/Live/Acoustic penalizan fuerte para no traer la grabación equivocada.

- **Progreso en vivo** (X de Y, canción actual), freno de 120 ms por pista para no calentar.
- **Pantalla de resultados**: cuántas encontradas, cuántas ambiguas, cuántas no encontradas.
- **Revisión de ambiguas**: eliges el candidato correcto o la saltas; tu elección se guarda como verdad permanente (una corrección manual nunca la pisa un resultado automático).
- La playlist creada **aparece en tu Biblioteca** y se sincroniza con tu cuenta de YouTube.
- Aviso antes de importar más de 100 canciones o con el Ahorro de datos activo.

## Qué probar y reportar
1. **Archivo**: exporta una playlist a CSV y prueba. ¿Coincidencias correctas? ¿Alguna versión equivocada (remix/directo)?
2. **Deezer**: una playlist pública. ¿Nombre y número correctos? ¿Importa?
3. La playlist nueva, ¿aparece en Biblioteca y se puede reproducir?
4. Cualquier fallo → **Ajustes ▸ Registros**.

## Nota técnica
El motor de coincidencias venía con falsos positivos (artista equivocado, remixes colados); recalibrado y con 14 pruebas propias en verde. Todo verificado por auditoría adversarial antes de esta beta — se cazó y arregló un fallo que dejaba Deezer inservible.
