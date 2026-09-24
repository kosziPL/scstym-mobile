plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "moe.rukamori.archivetune.morideobfuscator"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.quickjs.kt)
}
