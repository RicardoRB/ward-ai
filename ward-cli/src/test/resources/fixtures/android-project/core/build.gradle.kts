plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.sample.core"
    compileSdk = 35
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
