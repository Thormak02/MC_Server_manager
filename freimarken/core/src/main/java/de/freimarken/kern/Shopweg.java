package de.freimarken.kern;

/**
 * Auf welchem Weg jemand an einen Kistenshop will - die drei, die
 * {@link Kistenshop#offenFuer} entscheidet.
 *
 * <p>Das Javadoc jener Methode nannte sie schon beim Namen, bevor es diese Aufzaehlung
 * gab: "beim Abbauen von Schild, Kiste und Traeger, beim OEFFNEN der Kiste und beim
 * Anlegen eines zweiten Schildes an derselben Kiste". Sie bekommen hier einen Namen,
 * weil die FREIGABE sie unterscheiden muss - ein Freigegebener darf oeffnen und
 * abbauen, aber kein zweites Schild anhaengen.
 *
 * <p><b>Warum der Weg ueberhaupt eine Rolle spielt.</b> Bis zum 09.11.2026 gab
 * {@code offenFuer} dieselbe Antwort fuer alle drei - und genau das trug die Sicherheit:
 * "Daran, dass es nur diese eine Stelle gibt, haengt, dass der Schluessel nicht ein
 * Schloss offen laesst." Die Stelle bleibt eine; nur gibt sie jetzt je Weg eine eigene
 * Antwort. Das ist der kleinere Eingriff als zwei Methoden - bei zweien waere die Frage
 * "welche gilt hier" an jeder Aufrufstelle neu zu beantworten, und die dritte sitzt in
 * {@code Shopbuch}, das keinen Spieler sieht.
 *
 * <p><b>Die Reihenfolge ist die des Schadens</b>, den ein unberechtigter Zugriff
 * anrichtet: Oeffnen nimmt Ware, Abbauen loest den Laden auf, ein zweites Schild
 * verkauft fremde Ware auf das eigene Konto - und das letzte ist das schlimmste, weil es
 * weiterlaeuft, ohne dass der Besitzer etwas merkt.
 */
public enum Shopweg {

    /**
     * Die Kiste oeffnen - nachfuellen und entnehmen.
     *
     * <p>Der Weg, fuer den die Freigabe ueberhaupt gebaut wird. Plan 7.4 nennt den Preis
     * der Ganz-Verweigerung: "dass ein Fremder einem Shop auch nichts schenken kann".
     *
     * <p>EIN Ereignis ohne Ausnahme ({@code InventoryOpenEvent}), und das ist der Grund,
     * warum das Oeffnen ganz verweigert wird statt nur das Herausnehmen: Das Nehmen hat
     * zehn Wege - Links-, Rechts-, Umschaltklick, Ziehen, Zahlentasten, zweite Hand,
     * Doppelklick, Wurf -, und jede vergessene Art ist ein Loch.
     */
    OEFFNEN,

    /**
     * Schild, Kiste oder Traeger abbauen - das loest den Shop auf.
     *
     * <p>Fuer einen Freigegebenen erlaubt (entschieden 09.11.2026): Wer einen Laden
     * mitbetreut, soll auch die Preise aendern koennen, und das geht heute nur, indem
     * man das Schild abbaut und neu beschriftet. Die WARE bleibt dabei in der Kiste -
     * ein aufgeloester Shop kostet den Laden, nicht den Bestand.
     */
    ABBAUEN,

    /**
     * Ein ZWEITES Schild an dieselbe Kiste haengen - ein eigener Shop daran.
     *
     * <p><b>Fuer einen Freigegebenen VERBOTEN, und das ist die wichtigste Unterscheidung
     * dieser Aufzaehlung.</b> Dieser Weg war das Loch, das am 08.11.2026 geschlossen
     * wurde: "Wer die Kiste nicht oeffnen kann, haengt sonst ein eigenes Schild daneben
     * und verkauft die fremde Ware auf sein Konto - Shopbuch.anlege prueefte nur das
     * Schild, nie die Kiste."
     *
     * <p>Waere er fuer Freigegebene offen, stuende das Loch fuer genau die Leute wieder
     * auf, denen man gerade Vertrauen geschenkt hat - und es faellt nicht auf: Der
     * fremde Shop verkauft weiter, der Besitzer sieht nur, dass seine Kiste leerer wird.
     */
    ZWEITES_SCHILD
}
