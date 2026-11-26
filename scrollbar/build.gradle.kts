plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.amaanprobably"
version = "0.2.1"

kotlin {
    explicitApi()
    android {
        namespace = "io.github.amaanprobably.scrollbar"
        compileSdk {
            version = release(36)
        }
        minSdk = 24

        withHostTestBuilder {
        }

        // "test" tree: device tests also include commonTest, so the UI tests run on Android too.
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        androidResources {
            enable = true
        }
    }

    // How to include this library in an Xcode project:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "scrollbarKit"

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain {
            dependencies {
                api("org.jetbrains.compose.runtime:runtime:1.10.3")
                api("org.jetbrains.compose.foundation:foundation:1.10.3")
                api("org.jetbrains.compose.ui:ui:1.10.3")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation("org.jetbrains.compose.ui:ui-test:1.10.3") // runComposeUiTest, shared by all three platforms
            }
        }

        getByName("desktopTest") {
            dependencies {
                implementation(compose.desktop.currentOs.substringBeforeLast(":") + ":1.10.3") // native renderer for the test window, pinned to the library's Compose version
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.testCore)
                implementation(libs.androidx.testExt.junit)
                implementation(libs.androidx.testRunner)
                implementation(libs.androidx.compose.uiTestManifest) // lets the test launch an activity
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    // Signs on CI, where the key comes from repository secrets. Local publishToMavenLocal runs unsigned.
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }

    coordinates(group.toString(), "compose-fast-scrollbar", version.toString())

    pom {
        name = "Compose Fast Scrollbar"
        description = "A draggable fast scrollbar for Jetpack Compose and Compose Multiplatform lazy layouts and scrollable Columns and Rows."
        inceptionYear = "2026"
        url = "https://github.com/Amaanprobably/compose-fast-scrollbar"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "Amaanprobably"
                name = "Amaan Qureshi"
                url = "https://github.com/Amaanprobably"
            }
        }
        scm {
            url = "https://github.com/Amaanprobably/compose-fast-scrollbar"
            connection = "scm:git:git://github.com/Amaanprobably/compose-fast-scrollbar.git"
            developerConnection = "scm:git:ssh://git@github.com/Amaanprobably/compose-fast-scrollbar.git"
        }
    }
}

// Compose UI tests need a real Android runtime: they run on the device task, not the JVM host task.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter {
        excludeTestsMatching("io.github.amaanprobably.scrollbar.ScrollbarUiTest")
        excludeTestsMatching("io.github.amaanprobably.scrollbar.ScrollbarScrollStateTest")
        excludeTestsMatching("io.github.amaanprobably.scrollbar.ScrollbarTouchTest")
        excludeTestsMatching("io.github.amaanprobably.scrollbar.ScrollbarVisibilityTest")
    }
}