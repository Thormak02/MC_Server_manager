dependencies {
    implementation(project(":core"))
    implementation(project(":daten"))

    // compileOnly, NIE implementation: Paper bringt seine API selbst mit. Wer sie in die
    // eigene Jar packt, hat zwei Fassungen derselben Klassen in einer JVM - das endet in
    // LinkageError oder, schlimmer, in stillem Fehlverhalten.
    compileOnly("io.papermc.paper:paper-api:26.3.build.231-stable")
}

tasks.jar {
    // Kern, Datenschicht und der MariaDB-Treiber gehoeren IN die Jar. Der Treiber bewusst
    // mit: Paper liefert keinen mit, und ihn zur Laufzeit nachladen zu lassen hiesse, dass
    // ein Server ohne Internet beim ersten Start nicht hochkommt.
    //
    // Ohne Umbenennung der Treiberpakete, und das ist Absicht: Paper gibt jedem Plugin
    // seinen eigenen Klassenlader, also stoert ein zweiter MariaDB-Treiber in einem anderen
    // Plugin nicht. Die eine Stelle, an der es doch global zugeht - DriverManager - umgeht
    // Datenbank.java, indem sie den Treiber direkt anspricht.
    from(configurations.runtimeClasspath.map { pfad ->
        pfad.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })

    // Signaturen fremder Jars werden ungueltig, sobald deren Inhalt in eine andere Jar
    // wandert - bleiben sie drin, lehnt die JVM die Klassen ab. module-info.class landete
    // durch das Auspacken im Wurzelverzeichnis und gehoert dort nicht hin.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
    exclude("module-info.class", "META-INF/versions/*/module-info.class")

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
