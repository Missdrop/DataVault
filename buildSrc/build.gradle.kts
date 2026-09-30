plugins {
    `java-library`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}
