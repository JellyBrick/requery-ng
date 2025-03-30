import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version "8.3.6"
    id("com.google.devtools.ksp") version libs.versions.ksp.get()
    kotlin("jvm")
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":requery"))
    implementation("jakarta.persistence:jakarta.persistence-api:${libs.versions.jpa.get()}")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
    implementation("com.squareup:javapoet:1.11.1")
    implementation("com.google.devtools.ksp:symbol-processing-api:${libs.versions.ksp.get()}")
    implementation(kotlin("stdlib"))
}

tasks.named<ShadowJar>("shadowJar") {
    dependencies {
        include(dependency("com.squareup:javapoet:.*"))
    }
    relocate("com.squareup", "io.requery.com.squareup")
    archiveFileName.set("${project.name}.jar")
}

tasks.named("jar") {
    finalizedBy("shadowJar")
}