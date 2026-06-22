# Aura Hi-Res Player 0.0.3

## Sonido sin distorsión, garantizado en cualquier dispositivo
- El limitador true-peak y la normalización de volumen ahora nunca se saltan sin querer: si tenías activado el "audio offload" (que mandaba el sonido directo al hardware y se brincaba el limitador), ahora se desactiva solo mientras la normalización esté encendida. Así un tema con master fuerte ya no puede saturar.

## Arreglado: el tema mostraba dos opciones seleccionadas a la vez
- En Ajustes → Tema aparecían marcados al mismo tiempo "Seguir sistema" y "AMOLED". Ahora solo puede haber uno seleccionado.
- Al actualizar, tu app queda automáticamente en el **tema del sistema** (claro/oscuro automático + colores dinámicos) para que veas el tema nuevo desde que abres. Puedes cambiarlo cuando quieras.

## Lo que se hizo (técnico)
- App.seedDefaultsIfNeeded: el seed ya no fuerza pureBlack=true junto a darkMode=AUTO (eso encendía a la vez "Seguir sistema" y "AMOLED").
- App.migrateThemeSystemOnlyV2: migración única que deja a los usuarios existentes en AUTO + pureBlack OFF + tema dinámico ON al actualizar.
- ThemeScreen: las tarjetas "Seguir sistema"/"Light"/"Dark" requieren !pureBlack, así nunca se muestran dos modos seleccionados.
