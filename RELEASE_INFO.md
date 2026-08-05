# Aura Hi-Res Player 0.6.143

Versión de limpieza y protección. No trae funciones nuevas: **quita lo que aparentaba funcionar y no hacía nada**, y **protege el motor de audio**. Se auditó la app entera —1.718 controles en unas 95 pantallas, uno a uno contra el código— y de ahí salió todo lo de abajo.

---

## 🧹 Fuera lo que mentía
- **El visualizador de espectro no dibujaba nada.** Su código era un contenedor vacío, y venía **activado por defecto** en todos los móviles decentes, sin interruptor para apagarlo.
- **Cuatro archivos de componentes sin un solo uso** en toda la app: 920 líneas entre primitivas de ajustes, una barra de búsqueda, una barra de navegación y componentes de menú.
- **22 claves de configuración fantasma**: ni control que las escribiera, ni código que las leyera.
- **Seis pantallas inalcanzables y tres rutas muertas.** Una de ellas, de 402 líneas, apuntaba al repositorio del proyecto del que Aura partió.
- **Diálogos que nada podía abrir** y una **entrada fantasma en el buscador de ajustes**: buscabas "enviar me gusta", te lo ofrecía, navegabas, y no existía.
- **1.717 líneas eliminadas en total, y ni una sola función perdida** — verificado control por control por un revisor independiente, que encontró de paso cuatro filas más de las declaradas.

## 🔒 El motor de audio, protegido
- **La clave de licencia ya no está en el código fuente.** Se inyecta al compilar desde un secreto; quien clone el repositorio obtiene un valor vacío y una app sin motor.
- **Y va atada al certificado de firma.** Un clon repackado y firmado con otro certificado reconstruye un valor inválido y se queda sin motor; el original sigue.
- **Con red de seguridad**: si algo falla, la música **sigue sonando intacta**, sin ecualizador, con aviso claro en pantalla y una línea en el registro. Nunca muda, nunca distorsionada.
- **Si el motor se detecta degradado**, ahora se libera y el audio se enruta por fuera, en vez de seguir alimentando un motor probadamente inerte.

## 🩹 Arreglos
- Restaurado el acceso al **Ecualizador desde el buscador de ajustes**.
- Corregida la etiqueta de **"ocultar control de volumen"**, que decía otra cosa de la que hacía.
- Los cuatro estilos de barra de progreso ahora viven en **una sola implementación** compartida — y el estado "deshabilitado" se aplica por igual en todos, así que un invitado en Escuchar Juntos ya no puede arrastrar la línea de tiempo en unos estilos y en otros no.
