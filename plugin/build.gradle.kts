import java.net.URI

plugins {
    java
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
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.h2database:h2:2.5.252")
    implementation("org.duckdb:duckdb_jdbc:1.5.6.0")
    // Only JDBC v2 is used; avoid the facade/legacy HTTP transport and pre-shaded :all bundle.
    implementation("com.clickhouse:jdbc-v2:0.9.3")
    implementation("org.mongodb:mongodb-driver-sync:5.6.1")
    implementation("io.lettuce:lettuce-core:6.8.1.RELEASE")
    testImplementation("junit:junit:4.13.2")
    compileOnly("org.bukkit:bukkit:1.15.2-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 11
}

tasks.withType<Test>().configureEach {
    // Gradle itself needs a newer JVM; launch tests on Java 11 to verify runtime compatibility.
    providers.gradleProperty("testJavaHome").orNull?.let {
        val windowsJava = file("$it/bin/java.exe")
        executable = (if (windowsJava.isFile) windowsJava else file("$it/bin/java")).absolutePath
    }
}

tasks.processResources {
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

// Preserve third-party jars (including native resources and service descriptors) unchanged.
val runtimeLibraries = configurations.runtimeClasspath.get().incoming.artifactView {
    componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
}.files
val libraryDirectory = layout.buildDirectory.dir("libs/DataVault-libraries")
val prepareRuntimeLibraries = tasks.register<Sync>("prepareRuntimeLibraries") {
    description = "Copies external dependencies beside the thin plugin JAR."
    from(runtimeLibraries)
    into(libraryDirectory)
}

tasks.jar {
    dependsOn(prepareRuntimeLibraries, ":api:classes")
    // The server provides our public API; Maven consumers still use the separate API artifact.
    from(project(":api").extensions.getByType<SourceSetContainer>().named("main").map { it.output })
    manifest {
        // Relative URLs work with the URLClassLoader used by older Bukkit versions as well.
        attributes("Class-Path" to runtimeLibraries.files.sortedBy { it.name }.joinToString(" ") {
            "DataVault-libraries/" + URI(null, null, it.name, null).toASCIIString()
        })
    }
}

val pluginDistribution = tasks.register<Zip>("pluginDistribution") {
    description = "Assembles an offline installation ZIP without merging dependency jars."
    group = "distribution"
    archiveBaseName = "DataVault"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(tasks.jar)
    from(prepareRuntimeLibraries) { into("DataVault-libraries") }
    from("INSTALL.txt")
}

tasks.assemble {
    dependsOn(pluginDistribution)
}

tasks.test {
    exclude("**/PackagingTest.class")
    exclude("**/*DockerTest.class")
    exclude("**/MariaDbIntegrationTest.class")
    exclude("**/PerformanceComparisonTest.class")
}

tasks.register<Test>("backendIntegrationTest") {
    description = "Verifies server backends in disposable Docker containers."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*DockerTest.class")
    exclude("**/benchmark/**")
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed") }
}

tasks.register<Test>("backendBenchmark") {
    description = "Compares all backends with direct drivers; requires Docker."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*BenchmarkDockerTest.class")
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed", "standardOut") }
}

tasks.register<Test>("packagedBackendTest") {
    description = "Verifies the thin plugin and manifest-loaded external libraries against real backends."
    group = "verification"
    dependsOn(pluginDistribution)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    // No development driver/API jars: the plugin manifest must supply the runtime dependencies.
    classpath = files(sourceSets.test.get().output, tasks.jar.flatMap { it.archiveFile }) +
        configurations.testRuntimeClasspath.get().filter {
            it.name.startsWith("junit-") || it.name.startsWith("hamcrest-")
        }
    systemProperty("datavault.packaged.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    include("**/integration/*DockerTest.class", "**/EmbeddedBackendTest.class", "**/RegistryTest.class",
        "**/PackagingTest.class")
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed") }
}

tasks.register<Test>("mariaDbTest") {
    description = "Runs opt-in integration tests against a dedicated local MariaDB database."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/MariaDbIntegrationTest.class")
    environment("DATAVAULT_TEST_PASSWORD", providers.environmentVariable("DATAVAULT_TEST_PASSWORD").getOrElse(""))
    environment("DATAVAULT_TEST_USER", providers.environmentVariable("DATAVAULT_TEST_USER").getOrElse("root"))
    outputs.upToDateWhen { false }
}

tasks.register<Test>("jdbcBenchmark") {
    description = "Compares direct JDBC, plugin-style async JDBC and DataVault on local databases."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/PerformanceComparisonTest.class")
    environment("DATAVAULT_TEST_PASSWORD", providers.environmentVariable("DATAVAULT_TEST_PASSWORD").getOrElse(""))
    environment("DATAVAULT_TEST_USER", providers.environmentVariable("DATAVAULT_TEST_USER").getOrElse("root"))
    maxParallelForks = 1
    outputs.upToDateWhen { false }
}
