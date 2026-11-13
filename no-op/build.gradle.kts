plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // The Compose compiler must run here too: registerSection takes a @Composable () -> Unit, and
    // only the compiler lowers that param to Function2 (adding Composer/Int). Without it the no-op
    // ships registerSection(String, Function0) while a Compose consumer compiles against the
    // @Composable metadata and calls registerSection(String, Function2) → NoSuchMethodError at
    // runtime. Applying it keeps the no-op ABI byte-identical to :core.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.alihansarigit.snoop.noop"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Types referenced by the public API surface; provided by the consuming app.
    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.compose.runtime)
    compileOnly(libs.okhttp)
    compileOnly(libs.ktor.client.core)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
