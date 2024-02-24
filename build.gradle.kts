import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.com.github.johnrengelman.shadow)
    alias(libs.plugins.com.google.protobuf)
    alias(libs.plugins.org.jlleitschuh.gradle.ktlint)
}

group = "me.jason5lee"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val launcherClassName = "io.vertx.core.Launcher"

application {
    mainClass.set("me.jason5lee.rwlock_grpc.RwLockMapServerKt")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlinx.coroutines.core)

    runtimeOnly(libs.grpc.netty)
    implementation(libs.grpc.protobuf)
    implementation(libs.protobuf.kotlin)
    implementation(libs.grpc.kotlin.stub)
    testImplementation(libs.junit.jupiter)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "17"

tasks.withType<ShadowJar> {
    archiveClassifier.set("fat")
    mergeServiceFiles()
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(PASSED, SKIPPED, FAILED)
    }
}

sourceSets {
    main {
        proto {
            srcDirs("src/main/protos")
        }
    }
}

protobuf {
    protoc {
        artifact = libs.protoc.asProvider().get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
        }
        create("grpckt") {
            artifact = libs.protoc.gen.grpc.kotlin.get().toString() + ":jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}
