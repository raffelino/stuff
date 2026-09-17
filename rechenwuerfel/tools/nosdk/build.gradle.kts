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

dependencies {
    compileOnly(files(androidJar))
    testImplementation("junit:junit:4.13.2")
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

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}

val copyRuntime by tasks.registering(Copy::class) {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("runtime"))
}
