plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

base {
    archivesName = "DataVault"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":api"))
    implementation("com.zaxxer:HikariCP:7.1.0")
    testImplementation("junit:junit:4.13.2")
    compileOnly("org.bukkit:bukkit:1.15.2-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 11
}

tasks.processResources {
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("com.zaxxer.hikari", "cn.missdrop.datavault.libs.hikari")
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
