plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.alihansarigit.snoop.noop"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Types referenced by the public API surface; provided by the consuming app.
    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.compose.runtime)
    compileOnly(libs.okhttp)
}
