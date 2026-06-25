# Aura Hi-Res Player 0.6.42 — Menos calor y batería + se adapta a tu teléfono (parte 6 del plan)

## El fondo animado ya no calienta el teléfono 🔥➡️❄️
El **fondo animado (Canvas)** seguía decodificando video aunque tuvieras la **pantalla apagada** o la app en segundo plano — eso calentaba y gastaba batería en gama alta. **Arreglado:** ahora el Canvas **se pausa al instante** cuando no lo estás viendo (pantalla apagada, app atrás) y se reanuda al volver. La música no se interrumpe.

## Se adapta a la potencia de tu teléfono 📱
La app ahora detecta la **gama del dispositivo por sus características (RAM, núcleos)** —no por la marca— y ajusta el trabajo de los efectos:
- **Gama alta:** todo al máximo.
- **Gama media:** buffers más moderados.
- **Gama baja:** Canvas en menor resolución, sin forzar el máximo bitrate, y los efectos pesados **vienen desactivados de fábrica** (los puedes activar cuando quieras).

## Bajo el capó (revisado adversarialmente antes de subir)
- `rememberIsAppInForeground()` pausa los dos reproductores de Canvas (carátula del reproductor y del álbum) fuera de primer plano.
- Nuevo `DeviceCapabilities`/`DeviceTier` (RAM reportada, núcleos, media performance class) — umbrales calibrados para que un 4 GB real entre como baja y un flagship no baje de gama por núcleos en reposo.
- Los defaults por gama **respetan tu elección**: solo se aplican en instalación nueva, nunca pisan lo que ya cambiaste; `migrateCanvasDefaultOn` ahora también respeta la gama.
- Buffer del Canvas reducido (antes 20 MB) a 4/8/16 MB según gama.

## Para probar
- En tu gama alta: pon música con video de fondo y **apaga la pantalla** un buen rato — debería calentar mucho menos.
