plugins {
    // Macht den Lagervertrag fuer andere Module nutzbar. Ohne das muesste jedes Modul
    // seine eigene Fassung der Pruefungen schreiben - und genau dann weichen sie ab.
    `java-test-fixtures`
}

dependencies {
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}

// Der Kern hat ABSICHTLICH keine einzige Abhaengigkeit ausser dem JDK.
//
// Hier liegen Preisstaffeln, Zusammenlegungsregeln, Mietfristen, Buergerrechte und das
// Kontobuch - alles, was wirklich kaputtgehen kann. Ohne Bukkit laesst es sich mit nacktem
// JUnit pruefen, ohne Server, ohne Attrappen, ohne stillschweigend uebersprungene Tests.
//
// Sobald hier eine Bukkit-Klasse auftaucht, ist die Trennung verloren. Dann braucht jeder
// Test einen halben Server, und genau das soll dieser Aufbau verhindern.
