import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.focusdivergent.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.focusdivergent.app"
        // API 26 es el mínimo para TYPE_APPLICATION_OVERLAY, la ventana que usará el bloqueo.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // La firma de release sale de `keystore.properties` (fuera del repositorio, ver .gitignore y Docs/RELEASE.md).
    // Sin ese fichero el build de release sale sin firmar: nunca hay una contraseña ni una clave en el código.
    val keystoreFile = rootProject.file("keystore.properties")
    val signing = if (keystoreFile.exists()) {
        signingConfigs.create("release") {
            val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            signingConfig = signing
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Con el Kotlin integrado de AGP 9, jvmTarget hereda de targetCompatibility.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric necesita los recursos y el manifest para los tests JVM.
            isIncludeAndroidResources = true
        }
    }

    // Los tests de migración de Room leen los JSON de esquema exportados como si fueran assets. Robolectric
    // toma los assets del variante debug (no los del conjunto `test`), así que van ahí; release no los incluye.
    sourceSets {
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }
}

// Exporta el esquema de Room a JSON: es la base de las migraciones futuras.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Núcleo AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navegación
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)

    // Room (desde la 2.7 las extensiones de corrutinas viven en room-runtime; room-ktx ya no hace falta)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Markdown de la Bóveda (analizador sin red; el dibujo es propio)
    implementation(libs.commonmark)

    // Corrutinas
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Tests JVM
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)

    // Tests de Compose sobre Robolectric (sin emulador). El manifest de pruebas aporta la actividad anfitriona.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
