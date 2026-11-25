package de.freimarken.kern;

/**
 * Was ein Text sein darf, der in ein Protokoll geht und einem anderen gezeigt wird
 * (Schritt 16a).
 *
 * <p><b>EINE Stelle und nicht zwei.</b> Ein Meldegrund (Teil C), ein Strafgrund und ein
 * Aufhebungsgrund (Teil B) sind dieselbe Sache: Text, den ein Mensch tippt und ein
 * anderer Mensch in einer Chatzeile liest. Als die Pruefung zum zweiten Mal gebraucht
 * wurde, war die Versuchung, sie abzuschreiben - und zwei Kopien weichen voneinander ab.
 * Die, die dann das Abschnittszeichen vergisst, ist die, mit der ein Spieler in der
 * oeffentlichen Bannliste Farben faelscht.
 *
 * <p><b>Steuerzeichen werden ABGEWIESEN und nicht entfernt.</b> Ein Grund, der heimlich
 * anders aussieht als getippt, ist in einem Protokoll das Letzte, was man will: Der
 * Moderator schreibt etwas, liest es als etwas anderes, und niemand kann sagen, wo es
 * sich geaendert hat. Dazu gehoert das Abschnittszeichen, mit dem sich in einer Chatzeile
 * Farben und ganze Zeilen nachbauen liessen.
 *
 * <p>Die Grenzen kommen von aussen: Jede Spalte hat ihre eigene Laenge, und die Grenze
 * muss die der SPALTE sein - sonst bricht der strict mode von MariaDB mitten in einer
 * Buchung ab, und der Absender glaubt, es sei nichts passiert.
 */
final class Klartext {

    private Klartext() {
    }

    /**
     * Der Text ohne Rand - oder eine Klage.
     *
     * @param text      was getippt wurde
     * @param hoechstens die Laenge der Spalte, in die er geht
     * @param was       wie er in der Klage heissen soll ("Grund", "Vermerk")
     * @return der Text ohne Rand
     * @throws IllegalArgumentException wenn er fehlt, zu lang ist oder ein Steuerzeichen
     *         enthaelt
     */
    static String geprueft(String text, int hoechstens, String was) {
        if (text == null) {
            throw new IllegalArgumentException("Ein Eintrag ohne " + was);
        }
        String rein = text.trim();
        if (rein.length() > hoechstens) {
            throw new IllegalArgumentException("Der " + was + " ist laenger als "
                    + hoechstens + " Zeichen (" + rein.length() + ")");
        }
        int stelle = ersteStoerung(rein);
        if (stelle >= 0) {
            throw new IllegalArgumentException("Der " + was + " enthaelt ein"
                    + " Steuerzeichen an Stelle " + stelle + ". Erlaubt ist, was man"
                    + " tippen kann - ein Abschnittszeichen faelschte Farben in der"
                    + " Zeile, die ein anderer liest");
        }
        return rein;
    }

    /**
     * Ob aus diesem Text ein Eintrag werden kann - dieselben Grenzen, ohne Wurf.
     *
     * <p>Fuer die Stelle VOR dem Anlegen: Ein Spieler, der zu kurz getippt hat, soll eine
     * Absage auf Deutsch lesen und keinen Wurf. Geprueft wird mit denselben Grenzen wie
     * in {@link #geprueft}, also gibt es nur eine Wahrheit darueber, was brauchbar ist.
     */
    static boolean brauchbar(String text, int kuerzestens, int hoechstens) {
        if (text == null) {
            return false;
        }
        String rein = text.trim();
        return rein.length() >= kuerzestens && rein.length() <= hoechstens
                && ersteStoerung(rein) < 0;
    }

    /**
     * Die Stelle des ersten Zeichens, das nicht hineingehoert - oder -1.
     *
     * <p>Die Stelle und nicht ein {@code boolean}: Die Klage soll sie nennen. Ein
     * unsichtbares Zeichen in einem 200 Zeichen langen Grund findet sonst niemand.
     */
    private static int ersteStoerung(String rein) {
        for (int stelle = 0; stelle < rein.length(); stelle++) {
            char zeichen = rein.charAt(stelle);
            if (zeichen < ' ' || zeichen == '§' || zeichen == '\u007f') {
                return stelle;
            }
        }
        return -1;
    }
}
