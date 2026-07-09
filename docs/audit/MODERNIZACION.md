# Modernización — dependencias / toolchain / DB / JDK

> Auditoría read-only verificada vía web (2026-07-09). Toolchain actual: JDK 21, compileSdk/targetSdk 36, minSdk 26, Room v37 (migraciones completas, exportSchema on).

## Conclusiones clave
- El stack es **coherente** pero algo por detrás de lo último. Hay un camino de bumps **seguros** + **medios (aislados, build-verde por batch)**.
- **Material3 se queda en la línea alpha** (1.5.0-alpha18): 197 usos de APIs Expressive (WavySlider, FloatingNavigationToolbar, LoadingIndicator, ButtonGroup…) en 32 archivos + `ExperimentalMaterial3*` en 102. Bajar a estable 1.4.0 **rompe la compilación**. NO mover.
- **`material-icons-extended` 1.7.8 está CONGELADO por Google** (no se publica en Compose 1.8+). El pin es correcto. NO tocar.
- **Room 2.8.4** = última 2.x (3.0 es alpha KMP). Un bump de librería **no requiere cambio de esquema**.
- **JDK 21 (LTS)** cubre todo (AGP 9 min JDK 17; Kotlin 2.4 target hasta Java 26). Subir a 25 aporta poco. Mantener 21.

## Inventario: actual → última estable → estado
| Dependencia | Actual | Última estable | Estado |
|---|---|---|---|
| Gradle wrapper | 9.3.1 | 9.6.1 | behind |
| AGP | 9.0.0 | 9.2.0 | behind |
| Kotlin | 2.3.10 | 2.4.0 | behind |
| KSP | 2.3.5 | 2.3.9 | behind |
| Compose | 1.10.2 | 1.11.0 | behind |
| material-icons-extended | 1.7.8 | 1.7.8 (congelado) | on-latest |
| Material3 | 1.5.0-alpha18 | (alpha, se queda) | ahead/alpha |
| Media3 | 1.10.1 | 1.10.1 | on-latest |
| Room | 2.8.4 | 2.8.4 | on-latest |
| Ktor | 3.4.0 | 3.5.0 | behind |
| Coil3 | 3.3.0 | 3.5.0 | behind |
| Hilt/Dagger | 2.59.1 | 2.59.2 | behind (patch) |
| DataStore | 1.2.0 | 1.2.1 | behind (patch) |
| WorkManager | 2.10.0 | 2.10.2 | behind (patch) |
| Protobuf | 4.33.5 | 4.34.2 | behind |
| Guava | 33.5.0-jre | 33.6.0-jre | behind |
| Firebase BOM (gms) | 33.1.0 | 34.15.0 | muy behind |
| play-services-auth (gms) | 21.2.0 | 21.3.0 | behind |
| ffmpeg-kit-full | 6.0-2 | RETIRADO/EOL | ⚠️ EOL |
| youtubedl-android | 0.17.3 | 0.18.1 | behind |
| NewPipeExtractor | v0.25.2 | v0.26.1 | behind |
| Lottie | 6.6.2 | 6.7.1 | behind |
| Haze | 1.0.2 | ~1.7 / 2.0-alpha | behind (rework API) |
| MaterialKolor | 4.1.1 | 4.1.1 | on-latest |
| Jsoup | 1.22.1 | 1.22.2 | behind (patch) |
| androidx.browser | 1.8.0 | 1.9.0 | behind |

## Plan por riesgo
### 🟢 Seguros ya (un batch, solo `gradle/libs.versions.toml` + GMS)
jsoup 1.22.2 · guava 33.6.0-jre · datastore 1.2.1 · lottie 6.7.1 · coil 3.5.0 · ktor 3.5.0 · protobuf 4.34.2 · hilt 2.59.2 · work 2.10.2 · browser 1.9.0 · newpipeextractor v0.26.1 · Firebase BOM 34.15.0 · play-services-auth 21.3.0 · google-services 4.4.3. NO tocar: material-icons-extended, materialKolor, media3, room, process-phoenix.

### 🟡 Medio (aislado, build-verde por batch)
1. Gradle 9.3.1 → 9.6.1, luego AGP 9.0.0 → 9.2.0 (AGP 9.2 exige Gradle ≥9.1).
2. Kotlin 2.3.10 → 2.4.0 + KSP 2.3.5 → 2.3.9 (emparejados; compose-compiler/serialization suben con `version.ref=kotlin`). Validar KSP (Room/Hilt).
3. Compose 1.10.2 → 1.11.0 (Material3/adaptive intactos).
4. youtubedl-android 0.17.3 → 0.18.1 (nativo → test extracción/descarga en dispositivo).

### 🔴 Riesgoso / diferido (proyecto aparte)
- **ffmpeg-kit-full EOL** — retirado 2025, binarios fuera de Maven Central; hoy compila por caché/mirror. Migración (self-host binario / alternativa) = proyecto aparte, no un bump. **Flag de suministro.**
- **Haze** — 1.x reworkeó API (hazeChild→hazeEffect); requiere migración de código; 2.0 alpha. Diferir.
- Material3 estable 1.4.0 (rompe Expressive), compileSdk/targetSdk 37 (cambia runtime, testing dedicado, solo tras AGP), Room 3.0 (alpha), JDK 25 (bajo valor), motor Superpowered (política).

## Orden de aplicación (cada paso: build limpia real — ojo PIPESTATUS enmascara exit)
1. Batch SEGUROS → build foss+gms.
2. Gradle → AGP.
3. Kotlin + KSP.
4. Compose 1.11.0.
5. youtubedl 0.18.1 (test dispositivo).
6. Diferidos como trabajos separados.

Ningún bump toca demo/licencia/suscripción, el crossfade de 9s, ni el motor de audio.

## ⚠️ Resultado del intento (2026-07-09) — el "batch seguro" NO es seguro standalone
Se aplicó el batch verde y el build de CI FALLÓ en Hilt:
`[Hilt] Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0`.
Causa: **coil 3.5.0 / ktor 3.5.0 (releases de jun-2026) ya vienen compilados con Kotlin 2.4**, y su metadata 2.4.0 la procesa Hilt vía `kotlin-metadata-jvm`, que en el stack actual (Kotlin **2.3.10**) topa en 2.3.0. Revertir Hilt a 2.59.1 no lo arregla (el metadata 2.4 viene de los deps, no de Hilt).
**Conclusión:** los minors nuevos exigen subir PRIMERO el toolchain a **Kotlin 2.4.0 + KSP 2.3.9 + Hilt compatible** (el batch MEDIO). Sin eso, incluso "patch/minor" rompen. → El batch de deps se **revirtió**; la modernización queda como **un solo trabajo aislado**: Gradle 9.6.1 → AGP 9.2.0 → Kotlin 2.4.0/KSP → luego coil/ktor/Compose, cada paso con build verde. El branch de auditoría queda SIN bumps (verde con todos los fixes reales).
