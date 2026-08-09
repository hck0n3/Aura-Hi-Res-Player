# AGENTS.md — instrucciones para agentes de IA

> Este archivo está en español a propósito: el dueño del proyecto debe poder auditar sus reglas, y el
> documento más importante al que apunta (`docs/REGRESSION_REGISTRY.md`) también lo está. El **código,
> los commits y los mensajes de error siguen en inglés**.

---

## Qué es este proyecto

**Aura Hi-Res Player** — reproductor de música Hi-Res para Android (Kotlin + Compose, Gradle multi-módulo).
Es una **app de pago con beta privada**: el dueño es también el usuario que reporta los fallos, y las notas
de versión (`RELEASE_INFO.md`) se escriben para que las lea él y sus usuarios, no para otros desarrolladores.

El paquete publicado es `iad1tya.echo.music`, el código vive en `com.music.echo` y el root project de Gradle
se llama `echomusic`: es herencia del fork del que salió, no un despiste. No lo "arregles".

---

## Antes de tocar código — reglas duras

**1. `docs/REGRESSION_REGISTRY.md` es lectura obligatoria.** Registra 30+ fallos reportados con su causa raíz
real y el archivo:línea que hace de guardián. Antes de dar por bueno un cambio, verifica —leyendo o
grepeando el código, no solo compilando— que no revierte ninguno. Los archivos compartidos son la fuente #1
de regresiones: `MusicService.kt`, `Player.kt`, `App.kt`, `utils/Utils.kt`, `Lyrics.kt`, `YTPlayerUtils.kt`,
`BackupRestoreViewModel.kt`, `HomeScreen.kt`, `ArtistItemsViewModel.kt`, `Thumbnail.kt`.

**2. "Compila y parece correcto" NO es un arreglo.** Este proyecto ya acumula cinco placebos que llegaron a
darse por buenos. Traza todo arreglo hasta el efecto que el usuario percibe: ¿ese código se ejecuta?, ¿en su
dispositivo y su configuración?, ¿el valor llega hasta lo que produce el efecto?, ¿hay un gate o un valor por
defecto que lo anule?

**3. Confirma que el fallo reportado es real** —que se reproduce en el código actual, o que ya estaba
arreglado— antes de cambiar nada.

**4. Loguear no es solo logcat.** `utils/AppLogger.kt` persiste todo lo de nivel `INFO` o superior a
`filesDir/logs/app.log`, que es **el archivo que el usuario comparte** desde Ajustes ▸ Registros. Antes de
loguear una variable: ¿contiene datos del usuario (títulos, artistas, IDs, cuerpos de respuesta, cookies)?
Si la respuesta es sí, no se loguea.

**5. Antes de tocar la interfaz, lee `docs/UI_INVENTORY.md`.** Enumera todo lo que la app expone hoy. Lo que
no aparezca en el diseño nuevo y esté en esa lista, se habrá perdido en silencio. **Ocultar también es perder.**

**6. Zonas que no se tocan salvo petición explícita:**
- El **ecualizador** y el motor **Superpowered** (`eq/`, `app/src/main/cpp/`) — solo para mejorarlos, y solo
  si te lo piden.
- El paquete **`license/`** — la puerta de suscripción, el periodo de gracia, la master key y el Worker.
  Todo cambio debe dejar intactos el modo demo, la licencia y la suscripción.

**7. Verifica siempre el impacto en calentamiento y batería.** Es un criterio permanente de calidad, no una
consideración opcional: nada que muestree la pantalla o la red en cada fotograma mientras suena música.

---

## Cómo compilar

Requisitos: **JDK 21** (`jvmToolchain(21)`; `gradle/gradle-daemon-jvm.properties` ya fija el toolchain, así
que no hace falta tocar `JAVA_HOME`), Android SDK con **platform 36**, **build-tools 36**,
**NDK 27.0.12077973** y **CMake 3.22.1**. El `sdk.dir` va en `local.properties`, que no se commitea.

```bash
./gradlew assembleUniversalFossDebug      # sabor por defecto, sin Google Play Services
./gradlew assembleUniversalGmsDebug       # con Cast y Crashlytics
./gradlew assembleUniversalFossDebug -Pnosub=true   # sin la puerta de suscripción
```

`-Pnosub=true` compila con otro `applicationId` (`iad1tya.echo.music.dev`), así que se instala **al lado** de
la app real sin pisarla. Nunca se publica.

### Trampas conocidas de este repo

- **`./gradlew … | tail` devuelve el código de salida de `tail`, no el de Gradle.** Un fallo de compilación
  *parece verde*. Comprueba `PIPESTATUS[0]` o busca `BUILD SUCCESSFUL` en la salida.
- **"Unable to delete … classes.jar" o "Unable to delete directory"** casi nunca es un problema de código:
  suelen ser daemons de Gradle solapados (`./gradlew --stop` y reintentar) o permisos heredados de otro
  perfil de Windows sobre las carpetas `build/`.
- **`google-services.json` no existe, y es correcto.** La app publicada no lleva Firebase. Añadirlo cambia lo
  que la app hace en tiempo de ejecución; no lo hagas por iniciativa propia.

---

## Publicar — el tag decide quién recibe la actualización

El actualizador de la app consulta `releases/latest` de GitHub, que **excluye las prereleases**. El CI
(`.github/workflows/gradle.yml`) marca como prerelease cualquier tag que contenga `-beta` o `-test`:

| Tag | Quién la recibe |
|---|---|
| `v0.6.150-beta1` | **Solo el dueño**, entrando a la página de releases. |
| `v0.6.150` | **Todos los usuarios**, por el actualizador dentro de la app. |

> ### ⛔ Nunca publiques sin permiso explícito del dueño.
> Compilar y dejar el trabajo listo es correcto. Ejecutar `git push` de un tag **no lo es** salvo que él lo
> pida en ese momento. Es la única acción de todo el repositorio que llega a los móviles de terceros.

Antes de una versión estable, comprueba:

1. `versionName` sin `-beta`, y `versionCode` incrementado (es monótono, nunca por debajo de 673).
2. `RELEASE_INFO.md` cuenta **todo lo que el usuario va a recibir**. Si hay varias betas acumuladas desde la
   última estable, hay que fusionarlas o esa parte llega en silencio. La **línea 1 es el título** de la
   release y de la línea 3 en adelante es el cuerpo.
3. El CI genera `changelog.json` recogiendo **solo los títulos `##` y las viñetas `-`**. La prosa suelta no
   llega a la pantalla de novedades de la app: lo importante va en viñetas.
4. El registro de regresiones (regla 1).
5. Ejecutar `.\scripts\pre-publish-check.ps1 -Build` (Windows) o `./scripts/pre-publish-check.sh --build`
   (Linux/CI). Debe terminar en **READY**; cualquier **FAIL** bloquea la publicación (secretos GitHub,
   clave Superpowered, certificado `CN=JR MUSIC PRO`, coincidencia con el APK publicado).
6. **⛔ Publicar solo si GitHub está todo en verde.** Tras el push a `main`, esperar a que
   **Android Build & Sign** y **CodeQL Advanced** (y cualquier otro check del commit) terminen en
   `success`. Solo entonces crear/pushear el tag estable `vX.Y.Z` (sin `-beta`). Si algo está rojo o
   en curso, **no** taggear: corregir y reintentar. El agente debe verificar el estado de Actions
   aquí (API o badges) antes de publicar — no basta con “compila en local”.

### PC nueva o disco formateado (Windows)

Antes de formatear: `.\scripts\backup-dev-secrets.ps1` (copia keystore + `local.properties` a
`%USERPROFILE%\AuraHiResDevBackup`). En la máquina nueva, tras clonar: `.\scripts\setup-dev-environment.ps1`.

### La firma, que es lo que puede dejar a todos sin actualizar

Si el secret `RELEASE_KEYSTORE_BASE64` faltara, el CI **genera una keystore desechable en silencio** y la
build sale verde igualmente. Los usuarios recibirían "aplicación no instalada" y la única salida sería
desinstalar, perdiendo sus datos. Se distinguen de un vistazo: la de emergencia usa `CN=JR-MUSIC-PRO` **con
guiones**; la buena es `CN=JR MUSIC PRO` **con espacios**. Comprobación sobre cualquier APK publicado:

```bash
apksigner verify --print-certs <apk>
```

### Arreglar la reproducción sin publicar una versión

Cuando YouTube rota su player, `sig`/`n` dejan de deobfuscarse y algunos streams no resuelven. La vía rápida
es publicar la config del player nuevo en `player_configs.json` de la rama `main`: la app la lee en vivo y se
auto-repara para todos, sin actualización. **Regla absoluta: jamás publicar valores adivinados** —solo
verificados ejecutando el `base.js` real—, porque romperían más de lo que arreglan.

---

## Secretos

- `local.properties` (gitignored) lleva `sdk.dir` y `SUPERPOWERED_LICENSE_KEY`.
- `app/keystore/` (gitignored) lleva la keystore de release y sus credenciales.
- Nada de eso se commitea, se pega en un changelog, ni se escribe en un log.
- Las claves de Last.fm, Tidal y Qobuz embebidas en `app/build.gradle.kts` son deliberadas y están
  documentadas ahí mismo: son públicas por diseño. No las trates como una filtración ni las "arregles".
