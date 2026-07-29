package com.aura.migration.source.apple

/**
 * Apple Music NO se programa.
 *
 * Apple tiene transferencia nativa hacia YouTube Music desde su pagina de
 * Datos y Privacidad. Como YouTube Music ES nuestro catalogo, las playlists
 * aparecen solas al refrescar la biblioteca. Cero codigo de migracion.
 *
 * Esta clase solo aporta el contenido de la pantalla guia.
 * MusicKit queda descartado: exige Apple Developer Program a 99 USD/ano.
 */
object AppleMusicGuide {

    const val PRIVACY_URL = "https://privacy.apple.com/"

    data class Step(val number: Int, val title: String, val detail: String)

    val STEPS = listOf(
        Step(1, "Inicia sesion en Apple",
            "Abre privacy.apple.com y entra con tu Apple ID."),
        Step(2, "Elige transferir datos",
            "Selecciona \"Transferir una copia de tus datos\"."),
        Step(3, "Selecciona tus playlists",
            "Marca \"Apple Music Playlists\" y elige YouTube Music como destino."),
        Step(4, "Vuelve aqui",
            "Cuando Apple confirme, pulsa Actualizar y tus playlists apareceran.")
    )

    /** Mostrar SIEMPRE. Evita el 90% de los tickets de soporte. */
    val LIMITATIONS = listOf(
        "Solo se transfieren las playlists que creaste tu (incluidas las colaborativas propias).",
        "Las playlists de otros usuarios guardadas en tu biblioteca no se transfieren.",
        "No pasan podcasts, audiolibros ni archivos que hayas subido tu.",
        "No pasan las carpetas de organizacion ni las portadas personalizadas.",
        "Las descripciones de playlist si se conservan.",
        "Necesitas cuenta activa en Apple Music y en YouTube Music.",
        "El proceso tarda unos minutos, a veces horas si tienes muchas playlists."
    )
}
