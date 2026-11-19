plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    compileOnly("org.springframework.data:spring-data-redis:4.1.0")
    testImplementation(kotlin("test"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    testImplementation("tools.jackson.module:jackson-module-kotlin")
}