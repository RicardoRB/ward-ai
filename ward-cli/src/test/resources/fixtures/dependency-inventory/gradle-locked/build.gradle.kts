plugins { `java-library` }
repositories { mavenCentral() }
dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation(libs.guava)
    api("org.slf4j:slf4j-api:2.0.16")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.11.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
}
