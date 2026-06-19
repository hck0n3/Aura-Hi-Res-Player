# Aura Hi-Res Player v5.7.21

## Descarga de actualizaciones más estable
- Antes, en algunos equipos la descarga de la actualización **se reiniciaba a cada rato** y **se cortaba en segundo plano**. Causa: la descarga no corría como tarea de primer plano (Android la mataba) y al menor corte **empezaba de cero**.
- Ahora:
  - La descarga corre como **servicio de primer plano** → **sigue en segundo plano** (sales de la app y no se corta).
  - **Reanuda** desde donde iba (no vuelve a empezar de 0) si hay un corte de red o se interrumpe.
  - Si falla, **reintenta solo** y continúa donde quedó.

## Incluye lo anterior
- v5.7.20: el botón de Canvas ahora se llama "Canvas (lienzo)". v5.7.19: Canvas del álbum oficial.
