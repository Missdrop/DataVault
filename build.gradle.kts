plugins {
    base
}

group = "cn.missdrop"
version = providers.gradleProperty("projectVersion").get()

subprojects {
    group = rootProject.group
    version = rootProject.version
}
