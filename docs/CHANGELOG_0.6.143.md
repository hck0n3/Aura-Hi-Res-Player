# Registro de cambios — 0.6.143

> Versión de limpieza y protección. Sin funciones nuevas visibles: lo que hace es **quitar lo que mentía**
> y **proteger el motor de audio**. Sale sola, antes que la interfaz nueva, para que cuando esa llegue
> puedas volver atrás y encontrar exactamente esto.

---

## 🧹 Fuera lo que aparentaba funcionar y no funcionaba

Se auditó la app entera: **1.718 controles en unas 95 pantallas**, cada uno verificado contra el código.
De ahí salieron **1.717 líneas eliminadas** — y ni una sola función perdida, comprobado control por control.

- **El visualizador de espectro no dibujaba nada.** Su código era un contenedor vacío. Y venía **activado por
  defecto** en todos los móviles decentes, sin interruptor para apagarlo. Fuera: el componente, su procesador
  inerte, el bus que no leía nadie y la línea que lo encendía.
- **Cuatro archivos de componentes sin un solo uso** en toda la app: 345 líneas de primitivas de ajustes,
  246 de una barra de búsqueda, 96 de una barra de navegación y 233 de componentes de menú.
- **22 claves de configuración fantasma** — ni control que las escribiera, ni código que las leyera.
- **Seis pantallas inalcanzables** y tres rutas muertas. Una de ellas, 402 líneas, apuntaba al repositorio
  del proyecto del que Aura partió: una fuga de marca que ya no está.
- **Diálogos que nada podía abrir**, incluidos los de crear y unirse a sala en los ajustes de Escuchar
  Juntos — que sí existen y funcionan desde su propia pantalla y desde el menú del reproductor.
- **Una entrada fantasma en el buscador de ajustes**: buscabas "enviar me gusta", te lo ofrecía, navegabas,
  y no existía.

Nada de esto se borró a ojo. Cada eliminación se probó con una búsqueda en todo el proyecto, y un revisor
independiente volvió a verificarla después — encontrando, de paso, cuatro filas más de las declaradas.

---

## 🔒 El motor de audio, protegido

La clave de licencia de Superpowered **estaba escrita en texto plano en el código fuente**, en un
repositorio público. Ese es el mecanismo exacto por el que se desactiva una clave: alguien clona, publica un
clon usando tu licencia, y el proveedor lo detecta como uso no autorizado.

- **La clave ya no está en el código.** Se inyecta al compilar desde un secreto. Quien clone el repositorio
  obtiene un valor vacío.
- **Y va atada al certificado de firma de la app.** Un clon repackado y firmado con otro certificado
  reconstruye un valor inválido y se queda sin motor. El original sigue funcionando.
- **Con red de seguridad**: si algo falla, **la música sigue sonando intacta**, sin ecualizador, con un aviso
  claro en pantalla y una línea en el registro. Nunca muda, nunca distorsionada.
- **Y si el motor se detecta degradado**, ahora se libera y el audio se enruta por fuera, en vez de seguir
  alimentando un motor probadamente inerte.

Honestidad: la clave siempre será extraíble de un APK por alguien decidido. Lo que esto cierra es copiarla
de GitHub y repackar la app — que es justo lo que provoca que te la desactiven.

---

## 🩹 Arreglos

- Restaurado el acceso al **Ecualizador desde el buscador de ajustes**.
- Corregidas varias etiquetas y rutas que la limpieza dejó descolgadas.
