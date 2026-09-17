// Kompiliert app/src/main/java (+ generiertes R.java) gegen ein Android-Framework-Jar
// und legt die Kotlin-Standardbibliothek für den Dex-Schritt bereit.
// Eigenschaften: -PandroidJar=<pfad> -PgenDir=<pfad>
plugins {
    kotlin("jvm") version "2.2.20"
}

val androidJar: String = (project.findProperty("androidJar") as String?) ?: error("-PandroidJar fehlt")
val genDir: String = (project.findProperty("genDir") as String?) ?: error("-PgenDir fehlt")
val appDir = layout.projectDirectory.dir("../../app")

repositories { mavenCentral() }

val proguard: Configuration by configurations.creating

// Ersatz für androidx.test (nur Google Maven): siehe androidx-test-stubs/README.md
val stubs: SourceSet by sourceSets.creating {
    java.setSrcDirs(listOf(file("androidx-test-stubs")))
}

dependencies {
    compileOnly(files(androidJar))
    "stubsCompileOnly"(files(androidJar))
    testImplementation(files(androidJar))
    testRuntimeOnly(stubs.output)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1") {
        // androidx.test liegt nur auf Google Maven (hier nicht erreichbar); wird separat bereitgestellt
        exclude(group = "androidx.test")
        exclude(group = "androidx.test.espresso")
        exclude(group = "androidx.annotation")
        exclude(group = "androidx.tracing")
    }
    proguard("com.guardsquare:proguard-base:7.7.0")
}

sourceSets {
    main {
        // R.java liegt auch im Kotlin-Quellpfad, damit der Kotlin-Compiler die Symbole kennt.
        kotlin.setSrcDirs(listOf(appDir.dir("src/main/java").asFile, file(genDir)))
        java.setSrcDirs(listOf(appDir.dir("src/main/java").asFile, file(genDir)))
    }
    test {
        kotlin.setSrcDirs(listOf(appDir.dir("src/test/java").asFile))
        java.setSrcDirs(listOf<File>())
        resources.setSrcDirs(listOf(appDir.dir("src/test/resources").asFile))
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        // dx versteht kein invokedynamic: Lambdas als Klassen erzeugen.
        freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:-options"))
}

// Robolectric erwartet diese Datei auf dem Test-Klassenpfad (AGP erzeugt sie normalerweise).
val writeTestConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("test-config")
    val apk = project.findProperty("resourceApk") as String?
    val manifest = project.findProperty("mergedManifest") as String?
    inputs.property("apk", apk ?: "")
    inputs.property("manifest", manifest ?: "")
    outputs.dir(outDir)
    doLast {
        val f = outDir.get().file("com/android/tools/test_config.properties").asFile
        f.parentFile.mkdirs()
        f.writeText(
            """
            android_merged_manifest=$manifest
            android_merged_resources=${appDir.dir("src/main/res").asFile}
            android_merged_assets=${appDir.dir("src/main/assets").asFile}
            android_custom_package=com.raffelino.rechenwuerfel
            android_resource_apk=$apk
            """.trimIndent() + "\n"
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
    dependsOn(writeTestConfig)
    classpath += files(layout.buildDirectory.dir("test-config"))
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    systemProperty("robolectric.logging.enabled", "true")
    maxHeapSize = "2g"
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

val copyProguard by tasks.registering(Copy::class) {
    from(proguard)
    into(layout.buildDirectory.dir("proguard"))
}

val copyRuntime by tasks.registering(Copy::class) {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("runtime"))
}
