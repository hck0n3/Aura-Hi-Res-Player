# Aura Hi-Res Player 0.0.5

## Arreglado: el Inicio salía vacío tras el onboarding del primer arranque
- Al abrir la app por primera vez y elegir tus artistas y géneros, el Inicio aparecía vacío hasta que cerrabas y volvías a abrir la app.
- Ahora, al terminar el onboarding, el Inicio se recarga solo con los artistas que acabas de elegir — sin tener que reiniciar.

## Lo que se hizo (técnico)
- HomeViewModel guardaba un snapshot del Inicio en su primera carga, que ocurría ANTES de completar el onboarding (biblioteca vacía → Inicio vacío); al volver, restauraba ese snapshot vacío y no recargaba.
- Ahora observa `OnboardingArtistsDoneKey`: cuando el onboarding se completa durante la sesión, invalida el snapshot y recarga el Inicio con la nueva selección.
