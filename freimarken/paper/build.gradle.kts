dependencies {
    implementation(project(":core"))

    // compileOnly, NIE implementation: Paper bringt seine API selbst mit. Wer sie in die
    // eigene Jar packt, hat zwei Fassungen derselben Klassen in einer JVM - das endet in
    // LinkageError oder, schlimmer, in stillem Fehlverhalten.
    compileOnly("io.papermc.paper:paper-api:26.3.build.231-stable")
}

tasks.jar {
    // Der Kern gehoert IN die Jar. Er ist kein Fremdpaket, sondern die andere Haelfte
    // dieses Plugins - ohne ihn laedt Paper eine Jar ohne Rechenregeln.
    //
    // Solange der Kern keine eigenen Abhaengigkeiten hat, genuegt das hier. Kommt je eine
    // dazu, gehoert stattdessen com.gradleup.shadow her, samt Umbenennung der Pakete.
    from(configurations.runtimeClasspath.map { pfad ->
        pfad.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })

    // Zwei Module koennen dieselbe Datei beisteuern (etwa META-INF): die erste gewinnt,
    // statt den Build mit einer Dublettenmeldung abzubrechen.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    // Die Version steht im Gradle-Projekt, nicht doppelt in der plugin.yml. Sonst weicht
    // die Versionsangabe in der Jar irgendwann vom Build ab, und der Manager zeigt etwas
    // anderes an, als tatsaechlich laeuft.
    val angaben = mapOf("version" to project.version.toString())
    inputs.properties(angaben)
    filesMatching("plugin.yml") {
        expand(angaben)
    }
}
