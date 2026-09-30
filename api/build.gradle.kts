plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    // Native callback types are compile-time API; the server plugin supplies these clients at runtime.
    compileOnlyApi("org.mongodb:mongodb-driver-sync:5.6.1")
    compileOnlyApi("io.lettuce:lettuce-core:6.8.1.RELEASE")
    testImplementation("junit:junit:4.13.2")
}

base {
    archivesName = "datavault-api"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 11
}

tasks.withType<Test>().configureEach {
    providers.gradleProperty("testJavaHome").orNull?.let {
        val windowsJava = file("$it/bin/java.exe")
        executable = (if (windowsJava.isFile) windowsJava else file("$it/bin/java")).absolutePath
    }
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "datavault-api"
            from(components["java"])

            pom {
                name = "DataVault API"
                description = "A database abstraction API for Minecraft plugins."
                url = "https://github.com/Missdrop/DataVault"

                licenses {
                    license {
                        name = "GNU General Public License v3.0"
                        url = "https://www.gnu.org/licenses/gpl-3.0.html"
                        distribution = "repo"
                    }
                }

                developers {
                    developer {
                        id = "Missdrop"
                        name = "Missdrop"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/Missdrop/DataVault.git"
                    developerConnection = "scm:git:ssh://git@github.com/Missdrop/DataVault.git"
                    url = "https://github.com/Missdrop/DataVault"
                }
            }

            // Maven has no compileOnlyApi scope: keep native clients optional for JDBC-only users.
            pom.withXml {
                val root = asElement()
                val dependencies = root.getElementsByTagName("dependency")
                for (index in 0 until dependencies.length) {
                    val dependency = dependencies.item(index) as org.w3c.dom.Element
                    val group = dependency.getElementsByTagName("groupId").item(0).textContent
                    if (group in setOf("org.mongodb", "io.lettuce")) {
                        dependency.appendChild(root.ownerDocument.createElement("optional").apply {
                            textContent = "true"
                        })
                    }
                }
            }
        }
    }
}
