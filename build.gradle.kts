import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

repositories {
    google()

    mavenCentral()

    maven("https://jitpack.io")
}

group = "one.wabbit"
version = "1.2.0"

plugins {
    id("com.android.kotlin.multiplatform.library")

    kotlin("multiplatform")

    kotlin("plugin.serialization")

    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
    id("maven-publish")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")

    }
    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        testRuns["test"].executionTask.configure {
            jvmArgs("-ea")
        }
    }

    androidLibrary {
        namespace = "one.wabbit.parsing.charinput"
        compileSdk = 34
        minSdk = 26
    }

    iosArm64()

    iosSimulatorArm64()

    macosArm64("hostNative")

    targets.withType(KotlinNativeTarget::class.java).configureEach {
        binaries.framework {
            baseName = "ParsingCharinput"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")

            }

        }

        val jvmAndAndroidMain by creating {
            dependsOn(commonMain)

        }

        val jvmMain by getting {
            dependsOn(jvmAndAndroidMain)

        }

        val androidMain by getting {
            dependsOn(jvmAndAndroidMain)

        }

        val nativeMain by getting

        val iosArm64Main by getting

        val iosSimulatorArm64Main by getting

        val hostNativeMain by getting

        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:2.3.10")

            }

        }

    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("-ea")
}

dokka {
    moduleName.set("kotlin-parsing-charinput")
    dokkaPublications.html {
        suppressInheritedMembers.set(true)
        failOnWarning.set(true)
    }

    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src"))
            remoteUrl("https://github.com/wabbit-corp/kotlin-parsing-charinput/tree/master/src")
            remoteLineSuffix.set("#L")
        }

    }

    pluginsConfiguration.html {
        footerMessage.set("(c) Wabbit Consulting Corporation")
    }
}
