plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    explicitApi()
    android {
        namespace = "io.github.amaanprobably.scrollbar"
        compileSdk {
            version = release(37)
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
                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.compose.ui)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.compose.uiTest) // runComposeUiTest, shared by all three platforms
            }
        }

        getByName("desktopTest") {
            dependencies {
                implementation(compose.desktop.currentOs) // native renderer for the test window
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

// Compose UI tests need a real Android runtime: they run on the device task, not the JVM host task.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter {
        excludeTestsMatching("io.github.amaanprobably.scrollbar.ScrollbarUiTest")
    }
}