# Aura Hi-Res Player 0.1.3

## Vuelve la configuración de sonido que mejor te funcionó
- Restauramos el comportamiento de audio de la versión 0.0.9: la nivelación sube las canciones flojas como antes y se quitó la medición de loudness de las descargas que se había añadido después (era la que ensuciaba el sonido en algunos casos).
- Resultado: el sonido vuelve a ser el que te gustaba, sin saturación ni "olas" de volumen.

## Lo que se hizo (técnico)
- loudnessMakeupDb vuelve a +12 dB (config v0.0.9).
- Se eliminó la medición ITU-R BS.1770 por descarga (LoudnessAnalyzer/DownloadLoudnessMeasurer) y su hook en DownloadUtil. Las descargas vuelven a usar el loudness que entrega YouTube.
