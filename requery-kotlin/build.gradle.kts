import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    implementation(project(":requery"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    implementation("io.reactivex.rxjava2:rxjava:${libs.versions.rxjava2.get()}")
    implementation("io.projectreactor:reactor-core:${libs.versions.reactor.get()}")
}

sourceSets {
    main {
        java {
            srcDirs("src/main/kotlin")
        }
    }
}