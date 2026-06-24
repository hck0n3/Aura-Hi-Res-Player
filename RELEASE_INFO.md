# Aura Hi-Res Player 0.6.18

## IA: 2 arreglos + Mix con indicador
- **Géneros del onboarding ahora SÍ sirven.** Antes elegías géneros al inicio y no se usaban para nada. Ahora **siembran tu motor de recomendaciones** (mapeados a los géneros de iTunes), así Home y la reproducción tiran hacia lo que elegiste desde el primer día.
- **Proveedor "Claude" quitado** de la traducción de letras: estaba roto (formato de API incorrecto) y solo daba error. Para usar modelos Claude, elige **OpenRouter** (que sí los sirve). OpenAI / Gemini / OpenRouter / Perplexity / DeepL / Mistral siguen igual.
- **Mix/Radio con indicador:** el botón **Mix** ahora se **resalta** cuando está activo (iniciaste una radio) y se apaga al reproducir una cola nueva — así sabes cuándo está encendido.

## Técnico
- Nuevo `reco/OnboardingGenres.kt` (mapeo chip-español → iTunes primaryGenreName); `AffinityEngine.buildProfile(onboardingGenres=…)` siembra afinidad de género; leído en `HomeViewModel` y `MusicService`.
- `AiSettings`: eliminado "Claude" de proveedores/ayuda/modelos.
- `MusicService._mixActive` (true en `startRadioSeamlessly`, false en `playQueue`), expuesto en `PlayerConnection`; chip Mix resaltado.
