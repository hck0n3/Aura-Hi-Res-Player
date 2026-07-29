# Módulo `migration`

Migración de playlists **Tidal · Deezer · Apple Music · archivo → YouTube Music**.

## Instalación

1. Copia la carpeta a la raíz del proyecto como `migration/`
2. En `settings.gradle.kts`: `include(":migration")`
3. En `app/build.gradle.kts`: `implementation(project(":migration"))`
4. Renombra `YtmClientInnerTube.kt.template` → `.kt` y conéctalo a tu innertube
5. Registra la `MatchCacheDao` en tu `RoomDatabase` (o usa una base aparte)

## Uso

```kotlin
val engine = MigrationEngine(ytmClient, TrackResolver(ytmClient, cache), cache)

engine.import(DeezerSource(http), playlistId = "908622995")
    .collect { progress ->
        when (progress) {
            is MigrationEngine.Progress.Started  -> mostrarBarra(progress.total)
            is MigrationEngine.Progress.Track    -> actualizar(progress.done, progress.current)
            is MigrationEngine.Progress.Finished -> mostrarInforme(progress.report)
            is MigrationEngine.Progress.Failed   -> mostrarError(progress.error)
        }
    }
```

## Orden de implementación recomendado

| Fase | Qué | Esfuerzo |
|---|---|---|
| 0 | Apple Music (pantalla guía) | 0,5 día |
| 1 | Migrar Spotify a `PlaylistSource` | 1–2 días |
| 2 | Golden set + calibrar scorer + UI de revisión | 3–4 días |
| 3 | Import de archivo | 1 día |
| 4 | Deezer | 0,5 día |
| 5 | Tidal | 3–4 días |
| 6 | WorkManager, informe, pulido | 1–2 días |

**Empieza por la fase 0**: cuesta medio día y entrega una plataforma entera.

## Tests

```bash
./gradlew :migration:test
```

`GoldenSetTest` imprime precisión, cobertura y tasa de revisión. Es la única
forma honesta de calibrar el scorer.
