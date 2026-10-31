import java.util.Properties

dependencies {
    implementation(project(":core"))
    // Der Lagervertrag aus dem Kern - damit die Datenbankfassung dieselben Pruefungen
    // besteht wie jede andere, statt eigene zu bekommen.
    testImplementation(testFixtures(project(":core")))
    // Die 3er-Linie ist die erprobte; 4.0.0 ist frisch erschienen. Fuer eine Schicht, an
    // der Besitz und Geld haengen, ist das kein Ort fuer Neugier.
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.11")
}

// Die Tests gegen die echte Datenbank brauchen Zugangsdaten. Die stehen NICHT im
// Repository, sondern in data/freimarken/datenbank.properties - angelegt von
// skripte/mariadb_einrichten.ps1. Fehlt die Datei, werden die Tests uebersprungen, und
// zwar sichtbar: eine gruene Suite, die nichts geprueft hat, ist schlimmer als eine rote.
val zugangsDatei = rootProject.projectDir.parentFile
    .resolve("data/freimarken/datenbank.properties")

tasks.test {
    // Lokale Kopien, und alle Meldungen schon hier fertig gerechnet: Eine doFirst-Lambda,
    // die eine Variable von der Skriptebene anfasst, haelt in der Kotlin-DSL das ganze
    // Skriptobjekt fest - und das kann Gradle nicht in den Konfigurationszwischenspeicher
    // schreiben. Lambdas duerfen deshalb nur noch Zeichenketten sehen.
    val datei = zugangsDatei
    val wo = datei.toString()

    if (datei.isFile) {
        val angaben = Properties().apply { datei.inputStream().use { load(it) } }
        // AUSSCHLIESSLICH die Testadresse. Die Tests leeren vor jedem Test alle Tabellen -
        // gegen das Spielschema gerichtet loeschen sie jede Parzelle und jedes Guthaben.
        // Deshalb gibt es hier kein Ausweichen auf url/url.netz, auch nicht als Notnagel.
        // Vom Entwicklungsrechner geht es ueber das Netz, auf dem Spielserver ueber
        // localhost; url.test.netz hat Vorrang, wenn sie da ist.
        val url = angaben.getProperty("url.test.netz")?.takeIf { it.isNotBlank() }
            ?: angaben.getProperty("url.test")?.takeIf { it.isNotBlank() }

        if (url == null) {
            val meldung = "In $wo fehlt url.test (bzw. url.test.netz).\n" +
                "Die Datenbanktests leeren vor jedem Test alle Tabellen und duerfen daher " +
                "nur gegen ein Testschema laufen.\n" +
                "Auf dem Datenbankrechner einmal freimarken/skripte/mariadb_einrichten.bat " +
                "erneut ausfuehren - es legt freimarken_test an und ergaenzt die Zeile. " +
                "Vorhandene Passwoerter bleiben dabei unveraendert."
            doFirst { throw GradleException(meldung) }
        } else {
            systemProperty("freimarken.db.url", url)
            systemProperty("freimarken.db.benutzer", angaben.getProperty("benutzer") ?: "")
            systemProperty("freimarken.db.passwort", angaben.getProperty("passwort") ?: "")
            val anzeige = "Datenbanktests laufen gegen $url"
            doFirst { logger.lifecycle(anzeige) }
        }
    } else {
        val meldung = "ACHTUNG: $wo fehlt - die Datenbanktests werden UEBERSPRUNGEN. " +
            "Erst skripte/mariadb_einrichten.bat ausfuehren."
        doFirst { logger.warn(meldung) }
    }
}
