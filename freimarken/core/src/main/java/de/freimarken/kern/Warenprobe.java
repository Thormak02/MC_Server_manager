package de.freimarken.kern;

/**
 * Was ein Kistenshop handelt - EIN Exemplar der Ware, als Text.
 *
 * <p><b>Die Ware steht in der Zeile und nicht auf dem Schild.</b> Das ist die tragende
 * Entscheidung aus Plan 7.4, und sie kommt aus einem belegten Fehler der Vorbilder:
 * ChestShop schreibt einen Itemcode in die vierte Schildzeile und fuehrt dafuer vier
 * offene Fehler - "Not enough space in sign for some Enchanted Book" (#416), Fehler beim
 * Erzeugen des Namens fuer verzauberte Items (#606, #611), und die Verzauberung steht am
 * Ende gar nicht darauf (#305). Eine Schildzeile hat fuenfzehn Zeichen; eine Spitzhacke
 * mit fuenf Verzauberungen passt dort nie hinein.
 *
 * <p>Dieselbe Bauart wie {@link Abholgut}, und aus demselben Grund: Der Kern weiss nicht,
 * was ein Gegenstand ist, und soll es nicht wissen. Die Schicht darueber verwandelt einen
 * Stapel in Text und zurueck; alles dazwischen - Datenbank, Regel, Probe - sieht nur
 * diesen Text.
 *
 * <p><b>Der Unterschied zu {@link Abholgut} ist die MENGE.</b> Ein Abholstueck ist der
 * Stapel, wie er in der Truhe lag, mit seiner Anzahl. Eine Warenprobe ist genau EIN
 * Exemplar: Sie sagt, WAS gehandelt wird, und die Anzahl je Handel steht daneben in der
 * Shopzeile. Zwei Angaben derselben Menge liefen auseinander, sobald jemand die eine
 * aendert.
 *
 * @param name   wie die Ware heisst - fuer das Schild und fuer das Protokoll. Kurz genug
 *               fuer eine Schildzeile, denn dort landet sie
 * @param inhalt das eine Exemplar als Text, wie die Schicht darueber es geschrieben hat
 */
public record Warenprobe(String name, String inhalt) {

    /**
     * Was in eine Schildzeile passt.
     *
     * <p>Fuenfzehn Zeichen sind die Breite einer Minecraft-Schildzeile. Der Name wird
     * NICHT darauf gekuerzt - er wird abgewiesen, und die Schicht darueber kuerzt ihn
     * vorher auf ihre Weise. Ein hier stillschweigend gekuerzter Name waere zwei
     * verschiedene Namen fuer dieselbe Ware: einer in der Zeile, einer auf dem Schild.
     */
    public static final int LAENGSTER_NAME = 15;

    /** Dieselbe Grenze wie beim Abholfach - derselbe Weg, dieselbe Spalte. */
    public static final int LAENGSTER_INHALT = Abholgut.LAENGSTER_INHALT;

    public Warenprobe {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Eine Warenprobe ohne Namen ist eine, von"
                    + " der kein Kunde lesen kann, was sie ist");
        }
        name = name.trim();
        if (name.length() > LAENGSTER_NAME) {
            throw new IllegalArgumentException("Der Warenname \"" + name + "\" ist "
                    + name.length() + " Zeichen lang - auf eine Schildzeile passen "
                    + LAENGSTER_NAME + ". Wer kuerzt, kuerzt VOR dieser Stelle: Ein hier"
                    + " gekuerzter Name waere zwei Namen fuer dieselbe Ware.");
        }
        if (inhalt == null || inhalt.isEmpty()) {
            throw new IllegalArgumentException("Eine Warenprobe ohne Inhalt ist keine."
                    + " Der Name allein genuegt nicht: Zwei Spitzhacken heissen gleich"
                    + " und sind verschieden, sobald eine verzaubert ist.");
        }
        // NICHT getrimmt - wie bei Abholgut: Was die Schicht darueber geschrieben hat,
        // kommt Zeichen fuer Zeichen zurueck.
        if (inhalt.length() > LAENGSTER_INHALT) {
            throw new IllegalArgumentException("Die Ware \"" + name + "\" wird zu "
                    + inhalt.length() + " Zeichen Text - hoechstens " + LAENGSTER_INHALT
                    + " gehen in eine Shopzeile.");
        }
    }
}
