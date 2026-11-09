plugins {
    kotlin("jvm") version "2.2.21"
}

group = "demo"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion("@RELEASE@")
    targetCompatibility = JavaVersion.toVersion("@RELEASE@")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("@RELEASE@"))
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    useJUnitPlatform()
}
