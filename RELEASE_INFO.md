# Aura Hi-Res Player — Qué cambia en esta versión

*(Explicado en palabras normales. Al final está lo que hace falta que revises tú.)*

---

## 🎬 El vídeo ya no se congela

**El problema que reportaste:** *"siempre el vídeo se friza de la nada y solo sigue el audio; si me salgo de la ventana y vuelvo, se friza y no vuelve"*.

**Qué pasaba:** el vídeo y la imagen se pintan sobre un "lienzo". Cada vez que la pantalla se redibujaba, la app le decía al reproductor *"vuelve a engancharte al lienzo"* — creyendo que si ya estaba enganchado, no pasaba nada. **Era falso.** Si en ese instante el lienzo aún no estaba listo, esa orden **borraba la imagen**. Como el sonido no necesita lienzo, el audio seguía tan tranquilo sobre una imagen congelada.

Y al salir de la app y volver, la app se reconstruía entera por dentro, lo que disparaba justo ese instante malo. Por eso no volvía nunca.

**Ahora:** solo se re-engancha cuando el lienzo está realmente listo, y al volver a la app ya no se reconstruye nada si el reproductor sigue vivo.

---

## ❤️ El "Me gusta" en Android Auto ya se actualiza

**Lo que reportaste:** das a "me gusta" en el coche y el corazón no cambia.

**Qué pasaba:** el "me gusta" **sí se guardaba** — era solo que la pantalla del coche no se enteraba. La app anunciaba el cambio por un canal que Android Auto no escucha.

**Ahora:** se anuncia también por el canal que sí lee el coche.

---

## 🚗 La miniatura en el carro deja de llegar tarde

**Lo que reportaste:** en el L200, tras un rato largo, la carátula no se actualiza hasta media canción.

**Qué pasaba:** para un iconito pequeño del coche, la app descargaba la **portada gigante** (unas 10 veces más grande de lo necesario) y luego la encogía. Sin límite de tiempo. Con datos móviles lentos, eso tarda una eternidad — y mientras tanto se ve la carátula anterior.

**Ahora:** se pide directamente el tamaño pequeño, hay un tope de 6 segundos, y se reutiliza lo ya descargado.

---

## 🔋 Menos batería y menos calor

Encontré trabajo que la app hacía **constantemente sin necesidad**:

- **Refrescaba los widgets una vez por segundo, tengas widgets o no.** Cada refresco incluía varias consultas a la base de datos. Si no tienes ningún widget puesto, era 100% desperdicio, cada segundo, incluso con la pantalla apagada. → Ahora comprueba si tienes widgets y, si no, se salta todo.
- **Guardaba la lista de reproducción entera en disco cada 30 segundos**, aunque no hubiera cambiado nada, para siempre. → Ahora solo la guarda si de verdad cambió.
- **Fugas de memoria en las transiciones entre canciones**: cada mezcla dejaba restos que no se limpiaban. → Limpiados.

---

## 🎵 Calidad de audio, "volver a obtener" y vibración *(venían de la versión anterior, ya auditadas)*

- **La calidad ya no se queda pegada.** Antes: oías una canción en calidad normal, cambiabas a LOSSLESS, la volvías a poner… y seguía en calidad normal. Para siempre. Y cambiar la calidad sin nada sonando (o sea, estando en Ajustes) no hacía nada. Las dos cosas arregladas.
- **"Volver a obtener" ya refresca el audio de verdad** — antes solo refrescaba el título y el álbum, y seguía sirviendo el sonido viejo. Si la canción estaba descargada, se vuelve a bajar a tu calidad de descarga (no pierdes el offline).
- **El interruptor de vibración por fin manda en toda la app** — antes solo lo respetaban 3 botones.

---

## 🧠 La cola infinita sigue TU estilo, sea el que sea

Antes el "mantener el estilo" solo entendía de música cristiana. Ahora usa el **género real** del artista que suena (latino, rock, pop, hip-hop, gospel…).

Importante: **sigue descubriendo música nueva.** Solo descarta lo que sabe que es de otro palo; si no conoce a un artista, le da la oportunidad. Y va aprendiendo géneros de lo que la radio saca (solo con WiFi, en segundo plano).

---

## ✨ Nuevo: edita tus playlists hablándoles

*"Quita las lentas y añade 5 de este artista"* → la IA propone los cambios y **te los enseña antes de tocar nada**. No se aplica nada sin que lo confirmes.

---

## 🔒 Privacidad: los documentos ya dicen la verdad

Tu política de privacidad **decía que la app recoge analíticas y las comparte con Google**. Es **falso** y jugaba en tu contra. Verificado: no hay analíticas activas, no se envían informes de fallos, y el paso que podría activarlos en el servidor de compilación nunca se ha ejecutado (100 compilaciones comprobadas).

Ahora los documentos dicen exactamente qué sale del móvil y qué no, sin exagerar en ninguna de las dos direcciones.

---

## 🧹 Limpieza

- Fuera dos enlaces heredados que apuntaban al proyecto del que nació Aura (uno mandaba a los usuarios al APK **de otro proyecto** si alguien encendía esa ruta).
- Borrado un archivo de reproductor de vídeo que **no se usaba desde ningún sitio** (me costó una ronda entera de investigación descubrirlo).
- Los 3 tests del ecualizador que fallaban describían un diseño de hace 3 rediseños. Corregidos **los tests**; el ecualizador **no se tocó**.

---

# ⚠️ Lo que NO está arreglado todavía (sé honesto contigo)

| Lo que reportaste | Estado |
|---|---|
| **Redmi 14C: cuesta arrancar y dice "no disponible"** | **Causa encontrada, no arreglada.** Hay un límite de 30 segundos para resolver una canción; al agotarse muestra "no disponible". *"Tarda mucho"* y *"no disponible"* **son el mismo problema**. En tu móvil no pasa porque es más rápido. |
| **Se adelantan las canciones / cortes de milisegundos** | Causa probable identificada (recuperación tras error re-abre el audio). Falta confirmar. |
| **Plegables: letra descentrada, interfaz que desaparece al girar, vista dividida, todo gigante** | **En curso ahora mismo.** |
| **Ecualizador que se expanda con la pantalla** | **En curso.** Solo el diseño — bandas y filtros intactos. |
| **Xiaomi 14 CE se queda pegado (no responde)** | **En curso.** Causa principal localizada: la app lee ajustes de forma bloqueante cientos de veces al redibujar. |
| **Volumen Seguro: ¿funciona?** | **Verificándose.** Hay precedente de que algo así quedó en placebo, por eso lo estoy comprobando de verdad. |
| **Botones de abajo: animación trabada** | **En curso.** |
| **Cola infinita: a veces sí, a veces no** | **En curso.** |

---

# 🙋 Lo que necesito de ti

1. **Desplegar la ruta `/ai` en Cloudflare** (~10 minutos, sin publicar nada). Hoy tu IA depende de **un solo** modelo gratis que se cayó y volvió el mismo día. Descubrí que la "cascada de 4 modelos de respaldo" era mentira: **3 de los 4 no existen**, así que al fallar el único bueno se perdían 3 intentos inútiles. Ya está corregido, pero la única redundancia REAL es tu Worker.

2. **Responder 3 preguntas** que deciden los arreglos que faltan:
   - Los que sufren el arranque lento, **¿qué calidad de audio tienen puesta?** (Ajustes → calidad)
   - El del problema de los saltos, **¿tiene SponsorBlock activado?**
   - En el Xiaomi que se queda pegado, **¿al hacer qué exactamente?**

3. **Probar en tu teléfono.** Todo esto está compilado y auditado, pero **nada está probado en un dispositivo real**. Lo más sensible: la cadena de calidad de audio y el vídeo.
