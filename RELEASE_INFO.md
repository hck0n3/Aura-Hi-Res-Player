# Aura Hi-Res Player 0.6.102 — Android Auto ya no desaparece + indicador de Last.fm

## 🚗 Android Auto ya no se cae (sobre todo en Xiaomi/MIUI)
- Arreglada la causa real de que **la app apareciera y desapareciera** en la pantalla de Android Auto: con bibliotecas grandes, Aura mandaba **toda** la lista de golpe y el sistema **mataba el proceso** (cruzaba el límite interno de 1 MB). Ahora la lista va **acotada** y ya no se cae. Beneficia a **todos**, no solo a Xiaomi.
- Bajada la resolución de la carátula que se manda a la notificación/Auto (justo estaba en el límite de 1 MB) — la portada dentro de la app **no cambia**.
- Blindados los puntos de entrada del navegador de Auto para que **nunca fallen** y tumben la lista.

## 🔋 Menos cierres en segundo plano (Xiaomi/MIUI/HyperOS y similares)
- En teléfonos que **matan apps en segundo plano** (Xiaomi, Redmi, POCO, Oppo, Realme, OnePlus, Vivo, Huawei, Samsung…), Aura ahora te ofrece **una vez** activar lo necesario para que no te corte:
  - **Permitir batería sin restricción**, y
  - un atajo a **"Inicio automático"**.
- Antes esto estaba escondido en Ajustes → Contenido; ahora se te ofrece directo. *(El "Inicio automático" de MIUI no se puede activar solo desde la app — tienes que confirmarlo tú; no existe forma técnica de hacerlo automático.)*

## 🎧 Ves si Last.fm está funcionando
- Bajo el interruptor de **"Mejorar recomendaciones con Last.fm"** ahora aparece: **"✓ Última sincronización: hace X · N artistas de tu historial en uso"** — así confirmas con tus ojos que de verdad está leyendo tu historial y alimentando la IA. Antes no había forma de saberlo.

## ✅ Como siempre
- Suscripción, licencia, demo, crossfade de 9s, motor de audio y la cola infinita sin repetir: intactos. Verificado contra el registro de fallos (26 puntos): sin regresiones.
