# Aura Hi-Res Player 0.6.231

⚠️ Misma prueba diagnóstica que la 0.6.230 (firmada con clave distinta a propósito). Si ya tenías la 0.6.229 instalada, vas a necesitar desinstalarla antes de instalar esta.

---

## Novedades y Correcciones

- **La 0.6.230 nunca llegó a compilar**: falló en CI antes de generar el APK, así que "FALLO" era de la compilación, no de la app en tu teléfono. La causa: el paso de firma esperaba encontrar el keystore de depuración de Android en el runner de GitHub, pero ese archivo solo se crea automáticamente cuando se compila una variante "debug" normal — y este flujo nunca compila una. Se agregó un paso que lo genera a mano con las credenciales estándar antes de compilar.
- Con eso arreglado, esta versión SÍ debería llegar a publicarse. El contenido es idéntico a la 0.6.230: una build de release normal, pero firmada con la clave de depuración genérica en vez de tu certificado único, para comprobar si el bloqueo de reproducción está pegado al certificado.
- Si esta reproduce música, confirmamos que el certificado es la causa. Si también falla, se descarta esa última variable.

## Cómo actualizar

- Firmada distinto a la 0.6.229 — Android te va a pedir desinstalar esa primero antes de poder instalar esta.
