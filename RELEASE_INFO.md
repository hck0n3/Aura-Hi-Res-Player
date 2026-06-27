# Aura Hi-Res Player 0.6.57 — normalización pareja (estilo TIDAL), sin pantallas en blanco, "mejorar calidad" sin chillido

## 🔊 El interruptor de normalización vuelve a funcionar
Activar/desactivar la normalización de volumen a mitad de canción **no hacía nada** (un fallo que metí al arreglar otra cosa). **Arreglado:** desactivar = volumen crudo, reactivar = vuelve a nivelar al instante.

## 🎚️ Todas las canciones al MISMO nivel (incluso en transiciones)
Algunas sonaban más fuertes o más bajas que otras. **Dos causas:**
- Si la medida de volumen real de YouTube llegaba **tarde** (red lenta), se bloqueaba y la canción quedaba a un nivel provisional → fuera de nivel. **Arreglado:** ahora se aplica en cuanto llega, a cualquier momento (con transición suave, sin saltos).
- Las canciones **sin metadato de volumen** (locales/otras fuentes) sonaban más altas que las de YouTube niveladas. **Arreglado:** ahora se ajustan al mismo nivel de referencia.

Resultado: biblioteca pareja, **fuerte pero sin distorsionar, como TIDAL**.

## 🖼️ Adiós a la pantalla en blanco en las transiciones
A veces, en una transición (crossfade), la pantalla se quedaba **totalmente en blanco** un rato y luego volvía. **Causa:** durante el cambio, la app escuchaba un instante al reproductor que terminaba y borraba la carátula/controles. **Arreglado:** la portada y los controles **ya no se vacían** durante el cambio.

## ✨ "Mejorar calidad baja" sin agudos chillantes
El realce de agudos **saturaba/chillaba**. **Arreglado:** ahora es **mucho más sutil** (esquina más alta, fuera de la zona de sibilancia; realce 3× más suave) → restaura "aire" sin punzar.

## 👉 Para probar
- Activa/desactiva la normalización a mitad de canción → debe notarse cada vez.
- Pon canciones distintas seguidas → mismo nivel, sin saltos en las transiciones.
- Deja que ocurra un crossfade → sin pantalla en blanco.
- Activa "mejorar calidad" en una canción de baja calidad → más aire, sin chillido.
