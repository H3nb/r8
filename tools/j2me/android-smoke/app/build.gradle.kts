plugins { id("com.android.application") }

val generatedAssets = layout.buildDirectory.dir("generated/smokeAssets")
val compiledFixture = layout.buildDirectory.dir("fixtureClasses")
val android35Jar = File(System.getenv("ANDROID_HOME"), "platforms/android-35/android.jar")

val compileFixture by tasks.registering(Exec::class) {
    val classes = compiledFixture.get().asFile
    inputs.file("../fixture/sample/Legacy.java")
    outputs.dir(classes)
    doFirst { classes.mkdirs() }
    commandLine("javac", "--release", "8", "-d", classes.absolutePath, "../fixture/sample/Legacy.java")
}

val prepareAssets by tasks.registering(Sync::class) {
    dependsOn(compileFixture)
    from(compiledFixture) { include("sample/Legacy.class") }
    from(zipTree(android35Jar)) {
        include("java/lang/Object.class", "java/lang/Throwable.class", "java/lang/String.class")
        include("java/lang/Cloneable.class", "java/io/Serializable.class", "java/lang/Class.class")
    }
    into(generatedAssets)
}

android {
    namespace = "io.github.h3nb.d8j2me.smoke"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.h3nb.d8j2me.smoke"
        minSdk = 23
        targetSdk = 28
        multiDexEnabled = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    sourceSets["main"].assets.srcDir(generatedAssets)
}

tasks.named("preBuild").configure { dependsOn(prepareAssets) }

dependencies {
    implementation(files("libs/d8-j2me-api.jar"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
