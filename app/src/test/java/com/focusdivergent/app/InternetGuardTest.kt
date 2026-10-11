package com.focusdivergent.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RNF-1 (todo local) con una sola excepción acotada: «Pasos con tu propia IA». Este test vigila que la excepción no
 * crezca sin que nadie lo decida: internet solo se abre desde un fichero, y ese fichero solo habla con la dirección
 * que el usuario configuró.
 *
 * Gradle ejecuta los tests con la carpeta del módulo (`app`) como directorio de trabajo.
 */
class InternetGuardTest {

    private val main = File("src/main")

    private fun kotlinFiles() = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `solo un fichero abre conexiones de red`() {
        val networkApis = Regex("""HttpURLConnection|HttpsURLConnection|openConnection\(|java\.net\.Socket|okhttp|retrofit|WebView|java\.net\.URL\b""")
        val files = kotlinFiles()
        assertTrue("no se encontraron fuentes", files.size > 100)

        val users = files.filter { networkApis.containsMatchIn(it.readText()) }.map { it.name }

        assertEquals(listOf("ChatCompletionsSuggester.kt"), users)
    }

    @Test
    fun `el permiso de internet es el unico de red y esta explicado en el manifiesto`() {
        val manifest = File(main, "AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue("el permiso debe decir para qué es", manifest.contains("Sugerir pasos con tu IA"))
        listOf("ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE", "CHANGE_NETWORK_STATE").forEach {
            assertTrue("$it no hace falta", !manifest.contains(it))
        }
        assertTrue("las copias de seguridad siguen apagadas", manifest.contains("""android:allowBackup="false""""))
        assertTrue("sin tráfico sin cifrar", !manifest.contains("usesCleartextTraffic=\"true\""))
    }
}
