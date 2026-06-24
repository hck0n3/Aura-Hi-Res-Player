# Aura Hi-Res Player 0.6.2

## Sincronización robusta (no se interrumpe) 🔁🛡️
- "Sincronizar todo" (y cada tipo) ahora corre en **segundo plano vía WorkManager**: **sobrevive aunque cierres la app** y **reintenta solo hasta completarse** (con red). Como las sincronizaciones son aditivas, si se interrumpe simplemente continúa llenando tu biblioteca, sin perder ni duplicar nada.

## Introducción más clara 🎬
- Los pasos ahora **solo avanzan** (Artistas → Géneros → Spotify → YouTube → Inicio), con botón **"Siguiente"** y un **"Comenzar"** al final que te lleva al inicio. Ya no tienes que "regresar" para continuar. Puedes ir hacia atrás solo si tú quieres (botón atrás del sistema).

## Quitada "Realce de graves" 🧹
- Se eliminó el efecto **Realce de graves** de Efectos DSP (queda desactivado aunque lo tuvieras). El resto del sonido sigue igual.

## Pendiente (necesito tu confirmación)
- "Sincronizar todo" trajo 88 de tus 500+ artistas: la paginación está bien (hasta 50 páginas). Probablemente sean tus **suscripciones de canal** (otro conjunto) vs. la pestaña **"Artistas"** de tu biblioteca. Dime cuál de los dos son tus 500 y ajusto el endpoint.

## Técnico
- utils/YtmSyncWorker (CoroutineWorker + EntryPoint a SyncUtils, NetworkType.CONNECTED, backoff, ExistingWorkPolicy.KEEP). El hub enqueue por tipo.
- Onboarding: quitados los popUpTo intermedios; "Comenzar"/"Sincronizar" hacen popUpTo onboarding_artists.
- JrDsp bassEnhanceEnabled=false; item quitado de SoundSettings; mención fuera de About.
