plugins { `java-library` }
repositories { mavenCentral() }
dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation("org.apache.commons:commons-lang3:3.19.0")
    implementation("commons-io:commons-io:2.21.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("org.codehaus.plexus:plexus-utils:4.1.0")
}
