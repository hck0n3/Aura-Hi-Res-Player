# Aura Hi-Res Player v5.7.15

## Canvas (lienzos) arreglados — álbum y reproductor
- Encontré la causa real: la app usaba un proveedor de Canvas **muerto** (no devolvía videos), por eso no animaba ni en el álbum ni en el reproductor.
- Ahora usa el proveedor **Tidal** (videos de portada reales), igual que la versión de referencia → los Canvas **vuelven a funcionar** en el álbum y en el reproductor.
- Ya venían **activados por defecto**; si los habías apagado, vuelve a activarlos en **Apariencia → Canvas** (y el de álbum en **Contenido**).

## Incluye lo anterior
- v5.7.14: motor de podcasts (Apple/RSS). v5.7.13: ocultar videos apagado por defecto.
