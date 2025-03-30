plugins {
    java
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":requery"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    implementation("io.reactivex.rxjava2:rxjava:${libs.versions.rxjava2.get()}")
    implementation("io.reactivex.rxjava3:rxjava:${libs.versions.rxjava3.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.kotlinx.coroutines.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:${libs.versions.kotlinx.coroutines.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:${libs.versions.kotlinx.coroutines.get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx3:${libs.versions.kotlinx.coroutines.get()}")
    runtimeOnly("io.projectreactor:reactor-core:${libs.versions.reactor.get()}")
}

sourceSets {
    main {
        java {
            srcDirs("src/main/kotlin")
        }
    }
}