plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Optional: `-PbuildRoot=C:/ascii/path` moves build outputs out of a non-ASCII project path.
// Unit tests fail with ClassNotFoundException on Windows when the classpath contains Korean folder names.
providers.gradleProperty("buildRoot").orNull?.let { root ->
    allprojects { layout.buildDirectory.set(file("$root/${project.name}")) }
}
