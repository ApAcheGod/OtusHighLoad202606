plugins {
    `java-library`
    id("io.spring.dependency-management")
    id("com.google.protobuf") version "0.9.5"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.34.2"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.80.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

dependencies {
    api("io.grpc:grpc-stub:1.80.0")
    api("io.grpc:grpc-protobuf")
    api("com.google.protobuf:protobuf-java")
    compileOnly("jakarta.annotation:jakarta.annotation-api")
}
