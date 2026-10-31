import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
}

allprojects {
    group = "de.freimarken"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        // Paper 26.3 verlangt Java 25. Die Toolchain sorgt dafuer, dass Gradle genau
        // dieses JDK benutzt - egal, mit welchem Java Gradle selbst gestartet wurde.
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:6.1.3")
    }

    tasks.withType<JavaCompile>().configureEach {
        // --release statt -source/-target: prueft zusaetzlich, dass keine neueren
        // Bibliotheksmethoden verwendet werden, als die Zielversion kennt.
        options.release.set(25)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            // Uebersprungene Tests werden ausdruecklich gemeldet. Eine gruene Suite, die
            // in Wahrheit nichts geprueft hat, ist schlimmer als eine rote.
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
