plugins {
    id("fabric-loom") version "1.9.2"
    kotlin("jvm") version "2.2.0"
    java
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://maven.fabricmc.net/")
}

loom {
    // Don't scan mods directory
    mods {
        // Only include our mod
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")
    
    // Cobblemon from Maven - compileOnly so it gets remapped but not included in jar
    modCompileOnly("com.cobblemon:fabric:${project.property("cobblemon_version")}")
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }
    
    jar {
        from("LICENSE")
    }
    
    withType<JavaCompile> {
        options.release.set(21)
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
