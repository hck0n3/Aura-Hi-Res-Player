# Aura Hi-Res Player 0.6.109 — Qué cambia

*(En palabras normales. Al final: lo que aún no está arreglado y lo que necesito de ti.)*

---

## 🎬 El vídeo ya no se congela

**Tu reporte:** *"siempre el vídeo se friza de la nada y solo sigue el audio; si me salgo de la ventana y vuelvo, se friza y no vuelve."*

**Qué pasaba:** el vídeo se pinta sobre un "lienzo". Cada vez que la pantalla se redibujaba, la app le decía al reproductor *"vuelve a engancharte al lienzo"*, creyendo que era inofensivo. **No lo era.** Si en ese instante el lienzo no estaba listo, esa orden **borraba la imagen**. Como el sonido no necesita lienzo, el audio seguía tan tranquilo sobre una imagen congelada.

Y al salir de la app y volver, la app se reconstruía por dentro, lo que caía justo en ese instante malo — por eso no volvía nunca.

**Ahora:** solo se re-engancha cuando el lienzo está realmente listo, y volver a la app ya no reconstruye nada si el reproductor sigue vivo.

## ❤️ El "Me gusta" en Android Auto ya se actualiza

El "me gusta" **siempre se guardó bien** — lo que fallaba es que la pantalla del coche no se enteraba, porque el cambio se anunciaba por un canal que Android Auto no escucha. Ahora se anuncia también por el que sí lee.

## 🚗 La miniatura en el carro llega a tiempo

Para un iconito pequeño, la app descargaba la **portada gigante** (unas 10 veces más grande de lo necesario) y sin límite de tiempo. Con datos lentos eso tarda una eternidad, y mientras tanto se ve la carátula anterior. Ahora se pide directamente el tamaño pequeño, con tope de tiempo.

## 🔁 La cola infinita ya no se muere sola

**Tu reporte:** *"a veces funciona, a veces no."*

**Qué pasaba:** un contador de fallos que **nunca se reiniciaba**. Tres fallos de red en toda la sesión —ni siquiera seguidos— y la cola quedaba marcada como "no hay más canciones" **para siempre**. Un ascensor, un túnel o un cambio de WiFi a datos mataba la reproducción automática **en silencio, sin ningún aviso**, y seguía muerta al volver la señal.

**Ahora:** un fallo de red ya no se confunde con "se acabó la lista". Se reintenta con pausas y la cola sobrevive al corte.

## 🔋 Menos batería y menos calor

- Los widgets se refrescaban **una vez por segundo, tuvieras widgets o no** — con varias consultas a la base de datos cada vez, incluso con la pantalla apagada. Ahora comprueba si tienes alguno y, si no, se salta todo.
- La lista de reproducción se guardaba entera en disco **cada 30 segundos aunque no hubiera cambiado nada**. Ahora solo cuando cambia de verdad.
- Tres fugas de memoria en las transiciones entre canciones: cerradas.

## 📱 El Xiaomi ya no se queda pegado

**El arreglo más grande de todos.** Cada vez que se redibujaba cualquier pantalla, la app leía ajustes del disco **bloqueando el hilo principal**. Con unos 560 sitios que lo hacen, un solo fotograma podía apilar decenas de lecturas bloqueantes. Eso es, literalmente, la definición de "la app no responde". Corregido de raíz.

## 📖 Plegables

- **La interfaz ya no desaparece al girar.** Una rama del código secuestraba la pantalla completa dejando solo el fondo animado. Ahora esa vista inmersiva es **opcional** (apagada por defecto), así la interfaz se mantiene en cualquier ángulo.
- **Letra centrada** con el móvil abierto.
- **Vista dividida automática** al desplegar, estilo Spotify.
- **La portada ya no se pone gigante** — tiene tope en pantallas grandes.
- **El ecualizador se expande** con la pantalla. *(Solo el diseño: bandas, frecuencias, filtros y presets **sin tocar**.)*

## 🔒 Privacidad: los documentos ya dicen la verdad

Tu política de privacidad **decía que la app recoge analíticas y las comparte con Google**. Es **falso** y jugaba en tu contra. Verificado: no hay analíticas activas y no se envía ningún informe de fallos (comprobadas 100 compilaciones). Ahora los documentos describen exactamente qué sale del móvil y qué no.

## 🧹 Limpieza

- Fuera dos enlaces heredados al proyecto del que nació Aura. Uno de ellos mandaba a los usuarios **al APK de otro proyecto** si alguien encendía esa ruta.
- Borrado un reproductor de vídeo que **no se usaba desde ningún sitio**.
- Los 3 tests del ecualizador que fallaban describían un diseño de hace 3 rediseños. Corregidos **los tests**; el ecualizador no se tocó.

---

# ⚠️ Lo que NO está arreglado

| Reporte | Estado |
|---|---|
| **Redmi 14C: cuesta arrancar y dice "no disponible"** | **Causa encontrada, sin arreglar.** Hay un tope de 30 segundos para resolver una canción; al agotarse muestra "no disponible". **"Tarda mucho" y "no disponible" son el mismo problema.** En tu móvil no pasa porque es más rápido. |
| **Se adelantan las canciones / cortes de milisegundos** | Causa probable identificada, sin confirmar. |
| **Volumen Seguro** | **Funciona a medias.** Verificado hasta el código nativo: bajar los temas fuertes y el limitador **sí funcionan**. Pero **subir los flojos está muerto**, y la medición en vivo también. Solo baja, nunca sube. Arreglarlo obliga a tocar el ecualizador → **necesito tu permiso**. |
| **Botones de abajo: animación trabada** | **Probablemente ya resuelto** por el arreglo del Xiaomi (misma causa). Hay que probarlo antes de tocar más. |

---

# 🙋 Lo que necesito de ti

1. **Probar el APK.** Todo esto está compilado y auditado, pero **nada está probado en un dispositivo real**. Lo más sensible: el vídeo, la cola infinita y los plegables.

2. **Responder 3 preguntas** que deciden los arreglos que faltan:
   - Los del arranque lento, **¿qué calidad de audio tienen puesta?** (Ajustes → calidad de audio)
   - El de los saltos, **¿tiene SponsorBlock activado?**
   - **¿Autorizas tocar el procesador del ecualizador** para arreglar la mitad muerta del Volumen Seguro? (Solo esa parte; bandas y filtros seguirían intactos.)

3. **Desplegar la ruta `/ai` en Cloudflare** (~10 minutos, sin publicar nada). Tu IA depende hoy de **un solo** modelo gratis. Descubrí que la "cascada de 4 modelos de respaldo" era mentira: **3 de los 4 no existen**. Ya está corregido, pero la única redundancia real es tu Worker.

4. **Una línea en `gradle.properties`** para que las compilaciones dejen de romperse:
   ```
   org.gradle.java.installations.paths=C:\\Program Files\\Amazon Corretto\\jdk21.0.11_10
   ```
   Gradle está cogiendo el JRE de tu IDE (que no sirve para compilar Android) en vez de un JDK real.

---

## Cómo se hizo esto

15 reportes tuyos → 11 cerrados con **causa raíz probada** (dos verificadas desensamblando el código de Android, no adivinando).

**4 rondas de auditoría adversarial: 60 hallazgos, 20 reales.** La mayoría eran defectos **en los propios arreglos**, no en el código original. La última ronda encontró que **todo el trabajo de plegables no hacía nada** (un umbral de 700 que el sistema redondeaba a 840) y que un arreglo mío de la cola **duplicaba la siembra y borraba canciones**. Ambos corregidos antes de llegar aquí.

Verificado: compilación en verde, **137/137 tests**, sin regresiones contra las 62 filas del registro de errores.
