# Aura Hi-Res Player v5.7.69

## Arreglo: la pestaña "Sugerencias" (y "Álbum") no mostraban nada
- Como la app fuerza el idioma a español, el **código de país del sistema quedaba vacío** y la lista de Apple Music (pestaña Sugerencias) se pedía con una dirección inválida → no salía nada.
- Ahora se usa el **país real de tu dispositivo**, así que **Sugerencias** vuelve a cargar, y **Álbum/Explorar** usan tu región (en vez de quedarse siempre en "US").
