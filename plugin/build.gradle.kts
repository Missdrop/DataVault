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
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.h2database:h2:2.5.252")
    implementation("org.duckdb:duckdb_jdbc:1.5.6.0")
    implementation("com.clickhouse:clickhouse-jdbc:0.9.3:all")
    implementation("org.mongodb:mongodb-driver-sync:5.6.1")
    implementation("io.lettuce:lettuce-core:6.8.1.RELEASE")
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
    mergeServiceFiles()
    // Let the service transformer see every JDBC provider descriptor.
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    exclude("**/MariaDbIntegrationTest.class")
    exclude("**/PerformanceComparisonTest.class")
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
