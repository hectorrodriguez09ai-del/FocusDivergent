package com.focusdivergent.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que la primera release promete por escrito (licencia, política de privacidad) tiene que seguir siendo verdad en
 * el código. Si alguien cambia el servicio de accesibilidad o el manifiesto, estos tests obligan a actualizar los textos.
 *
 * Gradle ejecuta los tests con la carpeta del módulo (`app`) como directorio de trabajo; la raíz del proyecto es `..`.
 */
class ReleaseReadinessTest {

    private val root = File("..")
    private val main = File("src/main")

    @Test
    fun `el proyecto lleva la licencia GPL-3 y los textos legales`() {
        val license = File(root, "LICENSE").readText()
        assertTrue(license.contains("GNU GENERAL PUBLIC LICENSE") && license.contains("Version 3, 29 June 2007"))
        assertTrue(File(root, "README.md").readText().contains("GPL-3.0"))
        assertTrue(File(root, "PRIVACY.md").exists())
    }

    @Test
    fun `el servicio de accesibilidad no puede leer el contenido de la pantalla, como dice la politica de privacidad`() {
        val config = File(main, "res/xml/accessibility_service_config.xml").readText()
        val privacy = File(root, "PRIVACY.md").readText()

        assertTrue(config.contains("""android:canRetrieveWindowContent="false""""))
        assertTrue(config.contains("typeWindowStateChanged"))
        assertTrue(privacy.contains("canRetrieveWindowContent=false"))
    }

    @Test
    fun `cada permiso del manifiesto esta explicado en la politica de privacidad`() {
        val manifest = File(main, "AndroidManifest.xml").readText()
        val privacy = File(root, "PRIVACY.md").readText()
        val declared = Regex("""<uses-permission[^>]*android:name="android\.permission\.(\w+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        val explained = mapOf(
            "SYSTEM_ALERT_WINDOW" to "Mostrar sobre otras apps",
            "PACKAGE_USAGE_STATS" to "Acceso al uso de apps",
            "FOREGROUND_SERVICE" to "Servicios en primer plano",
            "FOREGROUND_SERVICE_SPECIAL_USE" to "Servicios en primer plano",
            "FOREGROUND_SERVICE_MEDIA_PLAYBACK" to "Servicios en primer plano",
            "POST_NOTIFICATIONS" to "Notificaciones",
            "SCHEDULE_EXACT_ALARM" to "Alarmas exactas",
            "RECEIVE_BOOT_COMPLETED" to "Iniciar al arrancar",
            "INTERNET" to "Internet",
        )

        assertTrue("permisos sin explicar en PRIVACY.md: ${declared - explained.keys}", explained.keys.containsAll(declared))
        declared.forEach { assertTrue("$it falta en PRIVACY.md", privacy.contains(explained.getValue(it))) }
    }

    @Test
    fun `la firma no se guarda en el repositorio y la muestra de diseno ya no esta`() {
        val ignore = File(root, ".gitignore").readText()
        assertTrue(ignore.contains("keystore.properties") && ignore.contains("*.jks"))
        assertFalse(File(main, "java/com/focusdivergent/app/presentation/showcase").exists())
        assertFalse(File(main, "res/values/strings.xml").readText().contains("Muestra de diseño"))
    }

    @Test
    fun `la ficha de F-Droid existe y respeta los limites de longitud`() {
        val meta = File(root, "fastlane/metadata/android/es-ES")
        assertTrue(File(meta, "title.txt").readText().isNotBlank())
        assertTrue(File(meta, "short_description.txt").readText().trim().length <= 80)
        assertTrue(File(meta, "full_description.txt").readText().length <= 4000)
        assertTrue(File(meta, "changelogs/1.txt").readText().length <= 500)
    }
}
