# Aura Hi-Res Player 0.6.138 — El aleatorio vuelve a ser aleatorio

## 🎲 Fin del bombardeo del mismo artista
Reporte del dueño en una playlist de 5.000+ canciones: el aleatorio "bombardea" con el mismo artista, y apagarlo/encenderlo no cambia nada. **Confirmado en el código, con números**:
- El barajado "inteligente" mezclaba tus gustos con el azar, pero el comentario que decía "el azar domina" era falso: la preferencia por tu artista favorito aportaba **0.85 contra 1.0 de azar** — casi empate. Resultado: el bloque entero de tu artista más escuchado se ordenaba al frente en CADA barajado, y re-activar el aleatorio recalculaba exactamente el mismo sesgo.
- El pase que debía separar rachas solo miraba 20 posiciones adelante y solo rompía parejas adyacentes: contra un bloque denso de un artista se rendía sin hacer nada.

**Ahora**:
- Los gustos aportan como máximo **0.255 contra 1.0 de azar** (4 a 1 a favor del azar): sigue habiendo un empujoncito hacia lo que te gusta, pero se siente barajado de verdad.
- Separación real: una canción no puede repetir artista con **ninguna de las dos anteriores**, buscando reemplazo hasta 60 posiciones adelante.
- Cada re-barajado (incluido apagar/encender el aleatorio) produce un orden **distinto**.
- De la auditoría del propio arreglo: el separador podía, en un caso raro (artista muy saltado con afinidad negativa), **cruzar la frontera de no-repetición** y colar una canción ya reproducida antes de cerrar el ciclo — un agujero latente que existía desde antes. Sellado: la pertenencia reproducida/no-reproducida ahora se comprueba directamente, no por el valor de la clave.

Límite honesto: si un tramo de la lista es genuinamente de un solo artista, la separación no puede inventar variedad que el pool no tiene.

## Recordatorio
- **Cierres del sistema** (nuevo en 0.6.137): Ajustes ▸ Registros ▸ "Cierres del sistema" — si la app se te cerró sin error, ahí está la causa real. Compártela.
- Cortes a mitad de canción (Lossless) o transiciones raras: mismo registro.
