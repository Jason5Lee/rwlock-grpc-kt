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

val mainVerticleName = "me.jason5lee.rwlock_grpc.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
    mainClass.set(launcherClassName)
}

dependencies {
    implementation(platform(libs.vertx.stack.depchain))
    implementation("io.vertx:vertx-grpc-server")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")
    implementation(kotlin("stdlib-jdk8"))

    implementation(libs.protobuf.kotlin)
    implementation(libs.grpc.kotlin.stub)
    testImplementation("io.vertx:vertx-junit5")
    testImplementation(libs.junit.jupiter)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "17"

tasks.withType<ShadowJar> {
    archiveClassifier.set("fat")
    manifest {
        attributes(mapOf("Main-Verticle" to mainVerticleName))
    }
    mergeServiceFiles()
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(PASSED, SKIPPED, FAILED)
    }
}

tasks.withType<JavaExec> {
    args = listOf(
        "run",
        mainVerticleName,
        "--redeploy=$watchForChange",
        "--launcher-class=$launcherClassName",
        "--on-redeploy=$doOnChange"
    )
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
