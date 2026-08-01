# Aura Hi-Res Player 0.6.140-beta9 — Reconocedor: LA causa real (BETA PRIVADA)

> ⚠️ Build de PRUEBA, solo para el dueño. Prerelease: el actualizador NO la ofrece a nadie más.

## 🎤 Reconocer canción — encontrada la causa de verdad

Reportaste tres veces que reconoce bien pero reproduce **otra canción sin relación**. Mis dos intentos anteriores fallaron porque los dos atacaron **qué canción se elige**… y el fallo nunca estuvo ahí.

**La causa real**: la reproducción desde el reconocedor era **la única ruta de canción única de toda la app** que no fijaba la canción antes de pedirle la radio a YouTube. Resultado: sonaba lo que viniera en cierta posición de **una lista traída de la red**, sin comprobar nunca que fuese la canción pedida.

Y el detonante que lo hacía casi seguro: con **Ahorro de datos** activo (o el filtro de explícitas/vídeos), la canción se **borraba de esa lista** pero la posición seguía apuntando al mismo sitio → sonaba **la siguiente canción de la radio**. De ahí el "no tiene nada que ver".

**Arreglado en dos niveles**:
1. El reconocedor ahora fija la canción exacta antes de nada (igual que búsqueda, listas y el menú de canción, que siempre funcionaron). La radio sigue después, como debe.
2. De fondo: al filtrar la cola, la posición ahora se **re-ancla** a la canción correcta. Esto corrige la misma clase de fallo para el resto de la app, no solo para el reconocedor.

## Lo demás de beta8 sigue igual
Tidal (0 coincidencias), discografías completas, Qobuz con tu suscripción, letra sincronizada, enlaces de YouTube, buscar/sincronizar playlists, fluidez por gamas.

---
Compila en ambos sabores, 225/225 pruebas.
