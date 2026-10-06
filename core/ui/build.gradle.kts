plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.comicnest.core.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)

    api(libs.androidx.core.ktx)
    api(platform(libs.compose.bom))
    api(libs.bundles.compose)
    api(libs.coil.compose)
    api(libs.coil.gif)

    debugApi(libs.compose.ui.tooling)
}
