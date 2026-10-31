// Der Kern hat ABSICHTLICH keine einzige Abhaengigkeit ausser dem JDK.
//
// Hier liegen Preisstaffeln, Zusammenlegungsregeln, Mietfristen, Buergerrechte und das
// Kontobuch - alles, was wirklich kaputtgehen kann. Ohne Bukkit laesst es sich mit nacktem
// JUnit pruefen, ohne Server, ohne Attrappen, ohne stillschweigend uebersprungene Tests.
//
// Sobald hier eine Bukkit-Klasse auftaucht, ist die Trennung verloren. Dann braucht jeder
// Test einen halben Server, und genau das soll dieser Aufbau verhindern.
