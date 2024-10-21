import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spotless)
}

android {
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "27.2.12479018"

    splits {
        abi {
            isEnable = true
            reset()
            if (gradle.startParameter.taskNames.any { it.contains("Release") }) {
                include("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
                isUniversalApk = true
            } else {
                include("arm64-v8a")
            }
        }
    }

    val signConfig = signingConfigs.create("release") {
        storeFile = File(projectDir.path + "/keystore/androidkey.jks")
        storePassword = "000000"
        keyAlias = "key0"
        keyPassword = "000000"
        enableV3Signing = true
        enableV4Signing = true
    }

    val commitSha by lazy {
        val stdout = ByteArrayOutputStream()
        exec {
            commandLine = "git rev-parse --short=7 HEAD".split(' ')
            standardOutput = stdout
        }
        stdout.toString().trim()
    }

    val buildTime by lazy {
        val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneOffset.UTC)
        formatter.format(Instant.now())
    }

    defaultConfig {
        applicationId = "org.moedog.ehviewer"
        minSdk = 28
        targetSdk = 35
        versionCode = 180008
        versionName = "1.8.7"
        resourceConfigurations.addAll(
            listOf(
                "zh",
                "zh-rCN",
                "zh-rHK",
                "zh-rTW",
                "ja",
            ),
        )
        buildConfigField("String", "VERSION_CODE", "\"${defaultConfig.versionCode}\"")
        buildConfigField("String", "COMMIT_SHA", "\"$commitSha\"")
    }

    externalNativeBuild {
        cmake {
            path = File("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf(
            // https://kotlinlang.org/docs/compiler-reference.html#progressive
            "-progressive",

            "-opt-in=coil.annotation.ExperimentalCoilApi",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable.add("MissingTranslation")
    }

    packaging {
        resources {
            excludes += "/META-INF/**"
            excludes += "/kotlin/**"
            excludes += "**.txt"
            excludes += "**.bin"
        }
    }

    dependenciesInfo.includeInApk = false

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = signConfig
            buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "BUILD_TIME", "\"\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    namespace = "com.hippo.ehviewer"
}

dependencies {
    // https://developer.android.com/jetpack/androidx/releases/activity
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.collection)

    implementation(libs.androidx.core)

    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.fragment)
    // https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation(libs.androidx.lifecycle.process)

    // https://developer.android.com/jetpack/androidx/releases/paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)

    // https://developer.android.com/jetpack/androidx/releases/room
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.paging)

    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.drawer)
    implementation(libs.material)

    // https://square.github.io/okhttp/changelogs/changelog/
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.coroutines)

    implementation(libs.okio.jvm)

    // https://github.com/RikkaApps/RikkaX
    implementation(libs.bundles.rikkax)

    // https://coil-kt.github.io/coil/changelog/
    implementation(libs.coil)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.ktor.utils)
    implementation(libs.jsoup)
}

configurations.all {
    exclude("dev.rikka.rikkax.appcompat", "appcompat")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

spotless {
    kotlin {
        // https://github.com/diffplug/spotless/issues/111
        target("src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
