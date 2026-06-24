# Aura Hi-Res Player 0.6.0

## Sonido 🔊
- **Subir una banda del EQ ya NO baja el volumen general**: se restaura el headroom completo (la banda sube, el resto se mantiene; el multibanda atrapa los picos).
- **Más fuerte en altavoces de celular** (sin distorsionar): subí el nivel de salida (el limitador multibanda evita el clipping digital).
- **Dar "me gusta" ya no cambia el volumen** (ni sube ni baja): la normalización nunca se re-aplica a la pista en curso; un valor más preciso se usa la próxima vez que suene.

## Imágenes 🖼️
- **Portadas de video más nítidas** (sddefault 640×480 en vez de hqdefault), sin el problema de portada negra.

## Recomendaciones / artistas 🎯
- La **home** ahora también se nutre de tus **artistas seguidos/sincronizados** (no solo los reproducidos), para que los que traes de YouTube/Spotify influyan en tu inicio.
- La **importación de Spotify** ahora deja **seguidos a todos los artistas importados** (no solo los que seguías en Spotify), para que el algoritmo los detecte. (En YouTube ya pasaba.)
- Los artistas que se siguen son solo **musicales** (solo se marcan los que tienen canciones/álbumes en tu biblioteca).

## Pendiente (necesito tu ayuda para verificar)
- "Sincronizar todo" de YouTube que no trae nada en el primer inicio, y la latencia de ~4s en otros dispositivos: necesito reproducir el caso con tu cuenta/logs.
