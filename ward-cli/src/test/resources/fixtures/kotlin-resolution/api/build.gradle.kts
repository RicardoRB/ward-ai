plugins {
    kotlin("jvm") version "2.2.21"
}

sourceSets {
    main {
        kotlin {
            srcDir("src/generated/kotlin")
        }
    }
}
