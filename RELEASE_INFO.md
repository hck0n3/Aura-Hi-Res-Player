# Aura Hi-Res Player 0.6.140-beta11 — El aleatorio: cinco causas reales (BETA PRIVADA)

> ⚠️ Build de PRUEBA, solo para el dueño. Prerelease: el actualizador NO la ofrece a nadie más.

Reportaste que el aleatorio **sigue repitiendo**. Tenías razón, y no era una sola cosa: una auditoría de cuatro frentes encontró **cinco caminos distintos** por los que una canción ya escuchada volvía a sonar. Cuatro de ellos son puntos que las correcciones anteriores nunca tocaron.

## 🔴 1. Al reabrir la app, el aleatorio arrancaba SIN memoria

El más grave, y explica por qué te pasaba tanto: tu móvil mata la app constantemente.

Al restaurar lo que estabas escuchando, la app le decía al reproductor "activa el aleatorio"… **cuando ya estaba activado**. Android no avisa de un cambio que no ocurre, así que la rutina que carga tu historial **nunca llegaba a ejecutarse**. Tu historial estaba intacto en la base de datos; simplemente nadie lo leía. Resultado: barajado al azar puro, con todo tu catálogo disponible para repetirse.

## 🔴 2. La última canción sin escuchar se iba al fondo

Al final de cada ciclo, cuando quedaba **una sola** canción sin oír, el sistema la colocaba la primera… y el paso siguiente, el que fija la canción actual al frente, **la mandaba al fondo del montón**. La siguiente en sonar era una ya escuchada, justo antes de completar la vuelta.

## 🟠 3. Las canciones de la radio no contaban como escuchadas

Con "reproducir primero la playlist" activado: al acabarse la playlist y entrar la radio infinita, sus canciones **se rebarajaban todas por igual** cada vez que avanzabas de tema. La promesa de no repetir se acababa en el borde de la playlist.

## 🟠 4. "···" → Aleatorio en álbumes y artistas ni encendía el aleatorio

Solo desordenaba la lista una vez, con el modo **apagado**. Orden congelado, sin sistema anti-repetición: volver a tocarlo mezclaba de nuevo desde cero y podía repetir lo recién oído. Esto ya se había arreglado para el menú de playlists — los de **álbum, álbum de YouTube y artista** se quedaron fuera.

## 🎤 5. Reconocedor: un cuarto camino

La comprobación de título aceptaba que un título **contuviera** al otro. `"Ella Baila Sola"` contiene `"Sola"`, así que si te reconocía *"Sola"* y el mismo artista tiene la otra, **pasaba el filtro**: veías una portada y sonaba otra canción. Cerrado con el emparejador por palabras.

---

## Lo que sigue de beta10
Los dos códigos de firma de YouTube (cruzados — causa de los 403 y de los cortes a mitad de canción), el arranque en procesos auxiliares, el volumen de Cast al reanudar, y la comprobación de título de JioSaavn en cualquier idioma.

## Lo que queda pendiente (te lo digo, no lo escondo)
- **Android Auto**: el "Aleatorio" del coche sigue sin encender el modo, y lo que suene ahí se apunta en el contexto equivocado.
- **"Mi Top" y "Caché"**: no tienen memoria entre días (solo dentro de la sesión).
- **Álbum y artista**: ya no repiten dentro de la sesión, pero aún no recuerdan entre días.
- **"Me gusta"**: se guarda en dos sitios distintos según entres por Biblioteca o por la tarjeta.

---
Compila en ambos sabores, 251/251 pruebas.
