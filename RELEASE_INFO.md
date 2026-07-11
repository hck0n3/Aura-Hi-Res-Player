# Aura Hi-Res Player 0.6.94 — Copias de seguridad a prueba de fallos y "no reproduce" resuelto

## ▶️ "No reproduce" tras restaurar — arreglado (para todos)
- Se corrigió un fallo por el que, tras **restaurar una copia de seguridad**, las canciones dejaban de reproducirse. La causa: la validación de la URL de audio enviaba una cookie de sesión que, si venía vieja o de otra sesión (p. ej. la de la copia), hacía que el servidor rechazara una URL perfectamente válida. Ahora la validación **no manda esa cookie** (igual que la descarga real), así que reproduce bien — y también arregla casos sueltos de "no carga" por una sesión caducada, **sin tener que reiniciar sesión**.

## 💾 Copias de seguridad y restauración que **nunca** fallan
- **Restaurar es seguro:** antes de tocar tu biblioteca, la app **verifica que la copia sea válida** (íntegra y compatible). Si el archivo está dañado o es de una versión más nueva, **te avisa con un mensaje claro y NO toca tus datos** (antes podía dejar la app rota).
- **Con red de seguridad:** si algo falla a mitad de la restauración, la app **recupera tu base de datos anterior** automáticamente — nunca te quedas con datos a medias.
- **Tus ajustes de ecualizador vuelven:** las copias ahora **incluyen y restauran tus presets de EQ** y apariencia (antes se perdían al restaurar).
- **Copia más robusta:** la base de datos se guarda con una **instantánea consistente** (no se corrompe aunque estés escuchando música al hacerla).
- **Mensajes claros:** ahora distingue entre archivo dañado, versión incompatible y error de lectura.

## ✅ Como siempre
- Suscripción, licencia, demo, crossfade de 9s y motor de audio: intactos. Calor y batería vigilados. La licencia nunca viaja en la copia.
