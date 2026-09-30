import cn.missdrop.datavault.gradle.RuntimeCatalogTask

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

// Only bootstrap helpers are shaded. Database drivers are never included in the release jar.
val bootstrapLibraries by configurations.creating
configurations.implementation { extendsFrom(bootstrapLibraries) }

base {
    archivesName = "DataVault"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    bootstrapLibraries("net.byteflux:libby-core:1.3.2")
    bootstrapLibraries("me.lucko:jar-relocator:1.7")
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
val bootstrapComponents = bootstrapLibraries.incoming.artifacts.artifacts.map { it.id.componentIdentifier }.toSet()
val runtimeArtifacts = configurations.runtimeClasspath.get().incoming.artifactView {
    componentFilter {
        it is org.gradle.api.artifacts.component.ModuleComponentIdentifier && it !in bootstrapComponents
    }
}.artifacts
val resolvedMavenPaths = runtimeArtifacts.artifacts.associate { artifact ->
    val id = artifact.id.componentIdentifier as org.gradle.api.artifacts.component.ModuleComponentIdentifier
    artifact.file.name to "${id.group.replace('.', '/')}/${id.module}/${id.version}/${artifact.file.name}"
}
check(resolvedMavenPaths.size == runtimeArtifacts.artifacts.size) {
    "Runtime artifact filenames must be unique when generating Maven coordinates."
}
val generateRuntimeCatalog = tasks.register<RuntimeCatalogTask>("generateRuntimeCatalog") {
    artifacts.from(runtimeArtifacts.artifactFiles)
    mavenPaths.set(resolvedMavenPaths)
    catalogFile = layout.buildDirectory.file("generated/bootstrap/runtime-libraries.tsv")
}
tasks.processResources {
    from(generateRuntimeCatalog.flatMap { it.catalogFile }) { into("META-INF/datavault") }
}

tasks.jar { enabled = false }

tasks.shadowJar {
    dependsOn(":api:classes")
    archiveClassifier = ""
    configurations = listOf(bootstrapLibraries)
    // The server provides our public API; Maven consumers still use the separate API artifact.
    from(project(":api").extensions.getByType<SourceSetContainer>().named("main").map { it.output })
    relocate("net.byteflux.libby", "cn.missdrop.datavault.libs.libby")
    relocate("me.lucko.jarrelocator", "cn.missdrop.datavault.libs.jarrelocator")
    relocate("org.objectweb.asm", "cn.missdrop.datavault.libs.asm")
    // Rewrites our implementation references; the driver is relocated later, not bundled here.
    relocate("com.zaxxer.hikari", "cn.missdrop.datavault.libs.hikari")
    exclude("module-info.class", "META-INF/versions/**/module-info.class")
}
tasks.assemble { dependsOn(tasks.shadowJar) }

// Copy only the release jar: the fixture must obtain drivers using the production downloader.
val preparePackagedInstallation = tasks.register<Copy>("preparePackagedInstallation") {
    from(tasks.shadowJar)
    into(layout.buildDirectory.dir("test-installation"))
    rename { "DataVault.jar" }
}
val installedPlugin = layout.buildDirectory.file("test-installation/DataVault.jar")

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

tasks.register<JavaExec>("packagedBackendTest") {
    description = "Verifies a single-jar installation with automatically downloaded Maven dependencies."
    group = "verification"
    dependsOn(preparePackagedInstallation, tasks.testClasses)
    // A JDK-only fixture creates a plugin-like loader; no development drivers enter that loader.
    classpath = sourceSets.test.get().output
    mainClass = "cn.missdrop.datavault.packaging.DownloadFixture"
    args(installedPlugin.get().asFile.absolutePath)
    args(sourceSets.test.get().output.classesDirs.files.map { it.absolutePath })
    args(configurations.testRuntimeClasspath.get().filter {
        it.name.startsWith("junit-") || it.name.startsWith("hamcrest-")
    }.map { it.absolutePath })
    providers.gradleProperty("testJavaHome").orNull?.let {
        val windowsJava = file("$it/bin/java.exe")
        setExecutable((if (windowsJava.isFile) windowsJava else file("$it/bin/java")).absolutePath)
    }
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
