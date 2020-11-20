import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    kotlin("jvm") version "1.3.70" apply false
}

allprojects {
    group = "io.pleo"
    version = "1.0"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        jcenter()
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "11"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        val implementation by configurations
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1")
        implementation("com.github.shyiko.skedule:skedule:0.4.0")

        val testImplementation by configurations
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.4.1")
    }
}



