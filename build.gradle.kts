plugins {
    id("com.android.application") version "8.1.2" apply false
    kotlin("android") version "1.9.10" apply false
    id("com.google.devtools.ksp") version "1.9.10-1.0.13" apply false
    id("com.diffplug.spotless") version "6.21.0" apply false
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

buildscript {
    dependencies {
        classpath("com.android.tools:r8:8.3.6-dev")
    }
}
