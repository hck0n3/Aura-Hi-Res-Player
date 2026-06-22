# Aura Hi-Res Player 0.0.2

## Nuevo: "Espejar favoritos desde mi cuenta"
- En Ajustes → Cuenta hay un botón nuevo para dejar tus favoritos del app **idénticos** a los de tu cuenta de YouTube: añade los que falten y quita los que ya no estén en tu cuenta.
- Es **manual y con confirmación** (tú decides cuándo), y **nunca toca tu cuenta de YouTube** — solo ajusta el app para que coincida.
- Trae una salvaguarda: si tu cuenta responde vacía (fallo de conexión), **no borra nada** para no perder tus favoritos.

## Recordatorio: tus favoritos ya no se borran solos
- La sincronización automática es aditiva: nunca te quita "me gusta" por su cuenta. Si en una versión anterior se te bajaron los favoritos, entra a Biblioteca → Favoritos y sincroniza (o usa el nuevo botón "Espejar"): tus "me gusta" siguen en tu cuenta de YouTube y vuelven.

## Lo que se hizo (técnico)
- SyncUtils.executeMirrorLikedSongs: acción manual que reconcilia los "liked" locales contra la lista canónica "LM" de YouTube (vía .completed()): añade los remotos faltantes y quita los locales ausentes, solo en local. Guard: aborta si el remoto vuelve vacío.
- AccountSettingsScreen: nuevo ítem "Espejar favoritos desde mi cuenta" + diálogo de confirmación; AccountSettingsViewModel.mirrorFromAccount() encola la operación.
