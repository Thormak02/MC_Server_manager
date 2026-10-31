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
    if (zugangsDatei.isFile) {
        val angaben = Properties().apply { zugangsDatei.inputStream().use { load(it) } }
        // Vom Entwicklungsrechner geht es ueber das Netz, auf dem Spielserver ueber
        // localhost. url.netz hat Vorrang, wenn sie da ist.
        val url = angaben.getProperty("url.netz")?.takeIf { it.isNotBlank() }
            ?: angaben.getProperty("url")
        systemProperty("freimarken.db.url", url ?: "")
        systemProperty("freimarken.db.benutzer", angaben.getProperty("benutzer") ?: "")
        systemProperty("freimarken.db.passwort", angaben.getProperty("passwort") ?: "")
        doFirst { logger.lifecycle("Datenbanktests laufen gegen $url") }
    } else {
        doFirst {
            logger.warn("ACHTUNG: ${zugangsDatei} fehlt - die Datenbanktests werden "
                    + "UEBERSPRUNGEN. Erst skripte/mariadb_einrichten.bat ausfuehren.")
        }
    }
}
